package com.test.repomigrator.entity;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter for {@link com.test.repomigrator.entity.BrowsedStore}.
 * NOTE: This class has been automatically generated from the {@link com.test.repomigrator.entity.BrowsedStore} original class using Vert.x codegen.
 */
public class BrowsedStoreConverter {

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, BrowsedStore obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "baseBrowseUrl":
          if (member.getValue() instanceof String) {
            obj.setBaseBrowseUrl((String)member.getValue());
          }
          break;
        case "baseContentUrl":
          if (member.getValue() instanceof String) {
            obj.setBaseContentUrl((String)member.getValue());
          }
          break;
        case "listingUrls":
          if (member.getValue() instanceof JsonArray) {
            java.util.ArrayList<com.test.repomigrator.entity.ListingUrls> list =  new java.util.ArrayList<>();
            ((Iterable<Object>)member.getValue()).forEach( item -> {
              if (item instanceof JsonObject)
                list.add(new com.test.repomigrator.entity.ListingUrls((JsonObject)item));
            });
            obj.setListingUrls(list);
          }
          break;
        case "parentPath":
          if (member.getValue() instanceof String) {
            obj.setParentPath((String)member.getValue());
          }
          break;
        case "parentUrl":
          if (member.getValue() instanceof String) {
            obj.setParentUrl((String)member.getValue());
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
        case "storeBrowseUrl":
          if (member.getValue() instanceof String) {
            obj.setStoreBrowseUrl((String)member.getValue());
          }
          break;
        case "storeContentUrl":
          if (member.getValue() instanceof String) {
            obj.setStoreContentUrl((String)member.getValue());
          }
          break;
        case "storeKey":
          if (member.getValue() instanceof String) {
            obj.setStoreKey((String)member.getValue());
          }
          break;
      }
    }
  }

  public static void toJson(BrowsedStore obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(BrowsedStore obj, java.util.Map<String, Object> json) {
    if (obj.getBaseBrowseUrl() != null) {
      json.put("baseBrowseUrl", obj.getBaseBrowseUrl());
    }
    if (obj.getBaseContentUrl() != null) {
      json.put("baseContentUrl", obj.getBaseContentUrl());
    }
    if (obj.getParentPath() != null) {
      json.put("parentPath", obj.getParentPath());
    }
    if (obj.getParentUrl() != null) {
      json.put("parentUrl", obj.getParentUrl());
    }
    if (obj.getPath() != null) {
      json.put("path", obj.getPath());
    }
    if (obj.getSources() != null) {
      JsonArray array = new JsonArray();
      obj.getSources().forEach(item -> array.add(item));
      json.put("sources", array);
    }
    if (obj.getStoreBrowseUrl() != null) {
      json.put("storeBrowseUrl", obj.getStoreBrowseUrl());
    }
    if (obj.getStoreContentUrl() != null) {
      json.put("storeContentUrl", obj.getStoreContentUrl());
    }
    if (obj.getStoreKey() != null) {
      json.put("storeKey", obj.getStoreKey());
    }
  }
}
