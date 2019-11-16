package com.test.repomigrator.cache;

import io.reactivex.Flowable;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class RepomigratorCache {
  
  
  public static List<JsonObject> openCircuitBrowsedStores = new ArrayList<>();
  public static Flowable<JsonObject> openCircuitBSFlow = Flowable.fromIterable(openCircuitBrowsedStores);

}
