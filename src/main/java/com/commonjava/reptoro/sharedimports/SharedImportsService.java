package com.commonjava.reptoro.sharedimports;


import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface SharedImportsService {

  void createTableSharedImports(Handler<AsyncResult<JsonObject>> handler);

  void getAllSealedTrackingRecords(Handler<AsyncResult<JsonObject>> handler);

  void getOneSealedRecord(Handler<AsyncResult<JsonObject>> handler);

  void getSealedRecordRaport(String buildId, Handler<AsyncResult<JsonObject>> handler);

  void checkSharedImportInDb(String buildId, Handler<AsyncResult<Boolean>> handler);

  void getSharedImportContent(String path , Handler<AsyncResult<JsonObject>> handler);

  void deleteSharedImportBuildId(String buildId , Handler<AsyncResult<JsonObject>> handler);

  void getOriginUrlHeaders(String originUrl , Handler<AsyncResult<JsonObject>> handler);

  void getAllSharedImportsFromDb(Handler<AsyncResult<JsonArray>> handler);

  void getNotComparedSharedImportsFromDB(Handler<AsyncResult<JsonObject>> handler);

  void getSharedImportContentCount(Handler<AsyncResult<JsonObject>> handler);

  void getSharedImportCount(Handler<AsyncResult<JsonArray>> handler);

  void getSharedImportDownloads(String buildId , Handler<AsyncResult<JsonObject>> handler);

  void getDownloads(String buildId,Handler<AsyncResult<JsonObject>> handler);

  @GenIgnore
  static SharedImportsService createProxy(Vertx vertx, String address) {
    return new SharedImportsServiceVertxEBProxy(vertx,address);
  }

  @GenIgnore
  static SharedImportsService createProxyWithOptions(Vertx vertx, String address, DeliveryOptions options) {
    return new SharedImportsServiceVertxEBProxy(vertx,address,options);
  }

}
