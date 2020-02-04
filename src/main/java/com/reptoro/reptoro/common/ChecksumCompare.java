package com.reptoro.reptoro.common;

import io.reactivex.Observable;
import io.vertx.core.json.JsonArray;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChecksumCompare {

  public static Map<String , JsonArray> checksumCompareResults = new ConcurrentHashMap<>();
  public static Observable<JsonArray> checksumCompareResultsFlowable = Observable.fromIterable(checksumCompareResults.values());


  public Map<String, JsonArray> getChecksumCompareResults() {
    return checksumCompareResults;
  }

  public void setChecksumCompareResults(Map<String, JsonArray> checksumCompareResults) {
    this.checksumCompareResults = checksumCompareResults;
  }

  public Observable<JsonArray> getChecksumCompareResultsFlowable() {
    return checksumCompareResultsFlowable;
  }

  public void setChecksumCompareResultsFlowable(Observable<JsonArray> checksumCompareResultsFlowable) {
    this.checksumCompareResultsFlowable = checksumCompareResultsFlowable;
  }
}
