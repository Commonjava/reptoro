package com.test.repomigrator.cache;

import io.reactivex.Flowable;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class RepomigratorCache {


  public static List<JsonObject> errorList = new ArrayList<>();
  public static Flowable<JsonObject> errorFlow = Flowable.fromIterable(errorList);
  
  
  public static List<JsonObject> contentList = new ArrayList<>();
  public static Flowable<JsonObject> contentFlow = Flowable.fromIterable(contentList);
  
  
  public static List<JsonObject> openCircuitBrowsedStores = new ArrayList<>();
  public static Flowable<JsonObject> openCircuitBSFlow = Flowable.fromIterable(openCircuitBrowsedStores);

}
