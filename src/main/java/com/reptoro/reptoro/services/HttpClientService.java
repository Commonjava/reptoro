package com.reptoro.reptoro.services;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;


@ProxyGen
public interface HttpClientService {

  // all maven & npm remote repositories
  void getAllRemoteRepositories(String packageType , Handler<AsyncResult<JsonObject>> handler);

  // browsed store by name -> name get from remote repositories
  void getListingsFromBrowsedStore(String name , Handler<AsyncResult<JsonObject>> handler);

  // getting content from listing urls
  void getContentAsync(JsonObject lu ,Handler<AsyncResult<JsonObject>> handler);

  // get headers from content and store them in json data object
  void getAndCompareHeadersAsync(JsonObject lu ,Handler<AsyncResult<JsonObject>> handler);

  void getContentSync(JsonObject lu ,Handler<AsyncResult<JsonObject>> handler);

  void getLocalContentHeadersSync(JsonObject content ,Handler<AsyncResult<JsonObject>> handler);
  void getRemoteContentHeadersSync(JsonObject content ,Handler<AsyncResult<JsonObject>> handler);


  void getAndCompareSourceHeaders(JsonObject listingUrl,Handler<AsyncResult<JsonObject>> handler);

  static HttpClientService createProxy(Vertx vertx, String address) {
    return new HttpClientServiceVertxEBProxy(vertx, address);
  }
}
