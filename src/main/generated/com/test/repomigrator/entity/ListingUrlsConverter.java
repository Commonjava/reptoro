package com.test.repomigrator.entity;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link com.test.repomigrator.entity.ListingUrls}.
 * NOTE: This class has been automatically generated from the {@link com.test.repomigrator.entity.ListingUrls} original class using Vert.x codegen.
 */
public class ListingUrlsConverter {

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, ListingUrls obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "contentUrl":
          if (member.getValue() instanceof String) {
            obj.setContentUrl((String)member.getValue());
          }
          break;
        case "listingUrl":
          if (member.getValue() instanceof String) {
            obj.setListingUrl((String)member.getValue());
          }
          break;
        case "path":
          if (member.getValue() instanceof String) {
            obj.setPath((String)member.getValue());
          }
          break;
        case "sources":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<java.lang.String> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof String)
                list.add((String)item);
            });
            obj.setSources(list);
          }
          break;
      }
    }
  }

  public static void toJson(ListingUrls obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(ListingUrls obj, java.util.Map<String, Object> json) {
    if (obj.getContentUrl() != null) {
      json.put("contentUrl", obj.getContentUrl());
    }
    if (obj.getListingUrl() != null) {
      json.put("listingUrl", obj.getListingUrl());
    }
    if (obj.getPath() != null) {
      json.put("path", obj.getPath());
    }
    if (obj.getSources() != null) {
      JsonArray array = new JsonArray();
      obj.getSources().forEach(item -> array.add(item));
      json.put("sources", array);
    }
  }
}
