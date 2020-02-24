package com.reptoro.reptoro.common;

import io.reactivex.Observable;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChecksumCompare {

  public static Map<String , JsonObject> checksumCompareResults = new ConcurrentHashMap<>();
  public static Observable<JsonObject> checksumCompareResultsFlowable = Observable.fromIterable(checksumCompareResults.values());


  public Map<String, JsonObject> getChecksumCompareResults() {
    return checksumCompareResults;
  }

  public void setChecksumCompareResults(Map<String, JsonObject> checksumCompareResults) {
    this.checksumCompareResults = checksumCompareResults;
  }

  public Observable<JsonObject> getChecksumCompareResultsFlowable() {
    return checksumCompareResultsFlowable;
  }

  public void setChecksumCompareResultsFlowable(Observable<JsonObject> checksumCompareResultsFlowable) {
    this.checksumCompareResultsFlowable = checksumCompareResultsFlowable;
  }
}
