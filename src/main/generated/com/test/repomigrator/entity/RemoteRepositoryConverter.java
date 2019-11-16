package com.test.repomigrator.entity;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link com.test.repomigrator.entity.RemoteRepository}.
 * NOTE: This class has been automatically generated from the {@link com.test.repomigrator.entity.RemoteRepository} original class using Vert.x codegen.
 */
public class RemoteRepositoryConverter {

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, RemoteRepository obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "allowReleases":
          if (member.getValue() instanceof Boolean) {
            obj.setAllowReleases((Boolean)member.getValue());
          }
          break;
        case "allowSnapshots":
          if (member.getValue() instanceof Boolean) {
            obj.setAllowSnapshots((Boolean)member.getValue());
          }
          break;
        case "authoritativeIndex":
          if (member.getValue() instanceof Boolean) {
            obj.setAuthoritativeIndex((Boolean)member.getValue());
          }
          break;
        case "cacheTimeoutSeconds":
          if (member.getValue() instanceof Number) {
            obj.setCacheTimeoutSeconds(((Number)member.getValue()).intValue());
          }
          break;
        case "disableTimout":
          if (member.getValue() instanceof String) {
            obj.setDisableTimout((String)member.getValue());
          }
          break;
        case "disabled":
          if (member.getValue() instanceof Boolean) {
            obj.setDisabled((Boolean)member.getValue());
          }
          break;
        case "host":
          if (member.getValue() instanceof String) {
            obj.setHost((String)member.getValue());
          }
          break;
        case "ignoreHostnameVerification":
          if (member.getValue() instanceof Boolean) {
            obj.setIgnoreHostnameVerification((Boolean)member.getValue());
          }
          break;
        case "isPassthrough":
          if (member.getValue() instanceof Boolean) {
            obj.setIsPassthrough((Boolean)member.getValue());
          }
          break;
        case "key":
          if (member.getValue() instanceof String) {
            obj.setKey((String)member.getValue());
          }
          break;
        case "maxConnections":
          if (member.getValue() instanceof Number) {
            obj.setMaxConnections(((Number)member.getValue()).intValue());
          }
          break;
        case "metadata":
          if (member.getValue() instanceof JsonObject) {
            obj.setMetadata(new com.test.repomigrator.entity.Metadata((JsonObject)member.getValue()));
          }
          break;
        case "metadataTimeoutSeconds":
          if (member.getValue() instanceof Number) {
            obj.setMetadataTimeoutSeconds(((Number)member.getValue()).intValue());
          }
          break;
        case "name":
          if (member.getValue() instanceof String) {
            obj.setName((String)member.getValue());
          }
          break;
        case "nfcTimeoutSeconds":
          if (member.getValue() instanceof Number) {
            obj.setNfcTimeoutSeconds(((Number)member.getValue()).intValue());
          }
          break;
        case "packageType":
          if (member.getValue() instanceof String) {
            obj.setPackageType((String)member.getValue());
          }
          break;
        case "pathStyle":
          if (member.getValue() instanceof String) {
            obj.setPathStyle((String)member.getValue());
          }
          break;
        case "port":
          if (member.getValue() instanceof Number) {
            obj.setPort(((Number)member.getValue()).intValue());
          }
          break;
        case "prefetchListingType":
          if (member.getValue() instanceof String) {
            obj.setPrefetchListingType((String)member.getValue());
          }
          break;
        case "prefetchPriority":
          if (member.getValue() instanceof Number) {
            obj.setPrefetchPriority(((Number)member.getValue()).intValue());
          }
          break;
        case "prefetchRescan":
          if (member.getValue() instanceof Boolean) {
            obj.setPrefetchRescan((Boolean)member.getValue());
          }
          break;
        case "proxyPort":
          if (member.getValue() instanceof Number) {
            obj.setProxyPort(((Number)member.getValue()).intValue());
          }
          break;
        case "serverCertificatePem":
          if (member.getValue() instanceof String) {
            obj.setServerCertificatePem((String)member.getValue());
          }
          break;
        case "timeoutSeconds":
          if (member.getValue() instanceof Number) {
            obj.setTimeoutSeconds(((Number)member.getValue()).intValue());
          }
          break;
        case "type":
          if (member.getValue() instanceof String) {
            obj.setType((String)member.getValue());
          }
          break;
        case "url":
          if (member.getValue() instanceof String) {
            obj.setUrl((String)member.getValue());
          }
          break;
      }
    }
  }

  public static void toJson(RemoteRepository obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(RemoteRepository obj, java.util.Map<String, Object> json) {
    if (obj.getAllowReleases() != null) {
      json.put("allowReleases", obj.getAllowReleases());
    }
    if (obj.getAllowSnapshots() != null) {
      json.put("allowSnapshots", obj.getAllowSnapshots());
    }
    if (obj.getAuthoritativeIndex() != null) {
      json.put("authoritativeIndex", obj.getAuthoritativeIndex());
    }
    if (obj.getCacheTimeoutSeconds() != null) {
      json.put("cacheTimeoutSeconds", obj.getCacheTimeoutSeconds());
    }
    if (obj.getDisableTimout() != null) {
      json.put("disableTimout", obj.getDisableTimout());
    }
    if (obj.getDisabled() != null) {
      json.put("disabled", obj.getDisabled());
    }
    if (obj.getHost() != null) {
      json.put("host", obj.getHost());
    }
    if (obj.getIgnoreHostnameVerification() != null) {
      json.put("ignoreHostnameVerification", obj.getIgnoreHostnameVerification());
    }
    if (obj.getIsPassthrough() != null) {
      json.put("isPassthrough", obj.getIsPassthrough());
    }
    if (obj.getKey() != null) {
      json.put("key", obj.getKey());
    }
    if (obj.getMaxConnections() != null) {
      json.put("maxConnections", obj.getMaxConnections());
    }
    if (obj.getMetadataTimeoutSeconds() != null) {
      json.put("metadataTimeoutSeconds", obj.getMetadataTimeoutSeconds());
    }
    if (obj.getName() != null) {
      json.put("name", obj.getName());
    }
    if (obj.getNfcTimeoutSeconds() != null) {
      json.put("nfcTimeoutSeconds", obj.getNfcTimeoutSeconds());
    }
    if (obj.getPackageType() != null) {
      json.put("packageType", obj.getPackageType());
    }
    if (obj.getPathStyle() != null) {
      json.put("pathStyle", obj.getPathStyle());
    }
    if (obj.getPort() != null) {
      json.put("port", obj.getPort());
    }
    if (obj.getPrefetchListingType() != null) {
      json.put("prefetchListingType", obj.getPrefetchListingType());
    }
    if (obj.getPrefetchPriority() != null) {
      json.put("prefetchPriority", obj.getPrefetchPriority());
    }
    if (obj.getPrefetchRescan() != null) {
      json.put("prefetchRescan", obj.getPrefetchRescan());
    }
    if (obj.getProxyPort() != null) {
      json.put("proxyPort", obj.getProxyPort());
    }
    if (obj.getServerCertificatePem() != null) {
      json.put("serverCertificatePem", obj.getServerCertificatePem());
    }
    if (obj.getTimeoutSeconds() != null) {
      json.put("timeoutSeconds", obj.getTimeoutSeconds());
    }
    if (obj.getType() != null) {
      json.put("type", obj.getType());
    }
    if (obj.getUrl() != null) {
      json.put("url", obj.getUrl());
    }
  }
}
