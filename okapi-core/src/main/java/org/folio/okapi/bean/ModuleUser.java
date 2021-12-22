package org.folio.okapi.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tells that module user should be created. This metadata should be read
 * by 3rd party automation script, which should create module user
 * with appropriate permissions
 */
@JsonInclude(Include.NON_NULL)
public class ModuleUser {

  private String type;
  private String[] permissions;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String[] getPermissions() {
    return permissions;
  }

  public void setPermissions(String[] permissions) {
    this.permissions = permissions;
  }
}
