package org.folio.okapi.common;

import io.micrometer.core.lang.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.JsonEvent;
import io.vertx.core.parsetools.JsonParser;
import io.vertx.core.streams.WriteStream;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(VertxUnitRunner.class)
public class JsonParserTest {
  Vertx vertx;
  private static final int PORT = 9231;
  private String buffer;
  private final Logger logger = OkapiLogger.get();
  private final int NUMBER_OF_RECORDS = 10000;
  private final int BYTES_IN_A_CHUNK = 11;

  private void chunksHandler(AtomicInteger offset, HttpServerResponse response) {
    vertx.runOnContext(y -> {
      int chunk = BYTES_IN_A_CHUNK;
      int remaining = buffer.length() - offset.get();
      if (remaining <= chunk) {
        response.end(buffer.substring(offset.get()));
        return;
      }
      response.write(buffer.substring(offset.get(), offset.get() + chunk));
      offset.getAndAdd(chunk);
      chunksHandler(offset, response);
    });
  }

  private void myStreamHandle(RoutingContext ctx) {
    AtomicInteger offset = new AtomicInteger(0);
    ctx.response().setStatusCode(200);
    ctx.response().setChunked(true);
    ctx.response().putHeader("Content-Type", "application/json");
    HttpServerRequest request = ctx.request();
    request.endHandler(x -> chunksHandler(offset, ctx.response()));
  }

  @Before
  public void setup(TestContext context) {
    this.vertx = Vertx.vertx();

    JsonObject jsonResponse = new JsonObject();
    JsonArray ar = new JsonArray();
    for (int i = 0; i < NUMBER_OF_RECORDS; i++) {
      ar.add(new JsonObject()
          .put("sequence", i)
          .put("id", UUID.randomUUID().toString())
          .put("title", new JsonObject()
              .put("cover", "computer programming")
              .put("author", "d e knuth")
          )
          .put("contributors", new JsonArray()
              .add("a1").add("a2").add("a3"))
          .put("k1", true)
          .put("k2", 1234)
          .putNull("k3")
      );
    }
    jsonResponse.put("response", ar);
    buffer = jsonResponse.encodePrettily();
    logger.info("size = {}", buffer.length());

    Router router = Router.router(vertx);
    router.get("/test").handler(this::myStreamHandle);
    HttpServerOptions so = new HttpServerOptions().setHandle100ContinueAutomatically(true);
    vertx.createHttpServer(so)
        .requestHandler(router)
        .listen(
            PORT).onComplete(context.asyncAssertSuccess());
  }

  @After
  public void after(TestContext context) {
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void test1(TestContext context) {
    HttpClient httpClient = vertx.createHttpClient();
    RequestOptions options = new RequestOptions()
        .setHost("localhost")
        .setPort(PORT)
        .setURI("/test").setMethod(HttpMethod.GET);

    Async async = context.async();
    httpClient.request(options)
        .onComplete(context.asyncAssertSuccess(request ->
            request.send()
                .onComplete(context.asyncAssertSuccess(response -> {
                  Buffer responseBody = Buffer.buffer();
                  context.assertEquals(200, response.statusCode());
                  context.assertEquals("application/json", response.getHeader("Content-Type"));
                  response.handler(responseBody::appendBuffer);
                  response.endHandler(end -> {
                    context.assertEquals(responseBody.toString(), buffer);
                    async.complete();
                  });
                }))
        ));
    async.await();
  }

  public class BatchStreamWrapper implements WriteStream<JsonEvent> {

    @Override
    public WriteStream<JsonEvent> exceptionHandler(Handler<Throwable> handler) {
      return null;
    }

    @Override
    public Future<Void> write(JsonEvent jsonEvent) {
      return null;
    }

    @Override
    public void write(JsonEvent jsonEvent, Handler<AsyncResult<Void>> handler) {

    }

    @Override
    public void end(Handler<AsyncResult<Void>> handler) {

    }

    @Override
    public WriteStream<JsonEvent> setWriteQueueMaxSize(int i) {
      return null;
    }

    @Override
    public boolean writeQueueFull() {
      return false;
    }

    @Override
    public WriteStream<JsonEvent> drainHandler(@Nullable Handler<Void> handler) {
      return null;
    }
  }

  @Test
  public void test2(TestContext context) {
    HttpClient httpClient = vertx.createHttpClient();
    RequestOptions options = new RequestOptions()
        .setHost("localhost")
        .setPort(PORT)
        .setURI("/test").setMethod(HttpMethod.GET);

    Async async = context.async();
    httpClient.request(options)
        .onComplete(context.asyncAssertSuccess(request ->
            request.send()
                .onComplete(context.asyncAssertSuccess(response -> {
                  JsonParser jp = JsonParser.newParser(response);
                  jp.objectEventMode();
                  jp.pipeTo(new BatchStreamWrapper());
                  jp.endHandler(end -> {
                    async.complete();
                  });
                  jp.exceptionHandler(e -> {
                    context.assertEquals("msg", e.getMessage());
                    async.complete();
                  });
                }))
        ));
    async.await();
  }

}
