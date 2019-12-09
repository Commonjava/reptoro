package com.test.repomigrator.entity;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link com.test.repomigrator.entity.Metadata}.
 * NOTE: This class has been automatically generated from the {@link com.test.repomigrator.entity.Metadata} original class using Vert.x codegen.
 */
public class MetadataConverter {

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, Metadata obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "changelog":
          if (member.getValue() instanceof String) {
            obj.setChangelog((String)member.getValue());
          }
          break;
      }
    }
  }

  public static void toJson(Metadata obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(Metadata obj, java.util.Map<String, Object> json) {
    if (obj.getChangelog() != null) {
      json.put("changelog", obj.getChangelog());
    }
  }
}
