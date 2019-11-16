package com.test.repomigrator.services.impl;

import com.test.repomigrator.services.IndyHttpClientService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;

import java.util.Iterator;
import java.util.Map;

/**
 * @author gorgigeorgievski
 */
public class IndyHttpClientServiceImpl implements IndyHttpClientService {
  
  static String MAVEN_REPOS = "/api/admin/stores/maven/remote";
  static String NPM_REPOS = "/api/admin/stores/npm/remote";
  static String BROWSED_STORES = "/api/browse/maven/remote/";
  
  Vertx vertx;
  WebClient client;
  
  public IndyHttpClientServiceImpl(WebClient client) {
    this.client = client;
    this.vertx = Vertx.vertx();
  }
  
  @Override
  public void getAllRemoteRepositories(String packageType, Handler<AsyncResult<JsonObject>> handler) {
    client
      .get(80, "indy-admin-stage.psi.redhat.com",
        (packageType.equalsIgnoreCase("maven") || packageType.isEmpty()) ? MAVEN_REPOS : NPM_REPOS)
      .send(res -> {
        if (res.failed()) {
          handler.handle(Future.failedFuture(res.cause()));
        } else {
          if (res.result().statusCode() == 200) {
            if(res.result().bodyAsJsonObject() == null) {
              handler.handle(Future.failedFuture(res.result().bodyAsString()));
            } else {
              handler.handle(Future.succeededFuture(res.result().bodyAsJsonObject()));
            }
          } else {
            handler.handle(Future.failedFuture(res.result().bodyAsString()));
          }
        }
      });
  }
  
  @Override
  public void getListingsFromBrowsedStore(String name, Handler<AsyncResult<JsonObject>> handler) {
    client
      .get(80, "indy-admin-stage.psi.redhat.com", BROWSED_STORES + name)
      .send(res -> {
        if (res.failed()) {
          handler.handle(Future.failedFuture(res.cause()));
        } else {
          if (res.result().statusCode() == 200) {
            if(res.result().bodyAsJsonObject() == null) {
              handler.handle(Future.failedFuture(res.result().bodyAsString()));
            } else {
              handler.handle(Future.succeededFuture(res.result().bodyAsJsonObject()));
            }
          } else {
            handler.handle(Future.failedFuture(res.result().bodyAsString()));
          }
        }
      });
  }
  
  @Override
  public void getContentAsync(JsonObject lu, Handler<AsyncResult<JsonObject>> handler) {
    
    io.vertx.reactivex.ext.web.client.WebClient.create(vertx, new WebClientOptions().setKeepAlive(true))
      .getAbs(lu.getString("listingUrl"))
      .rxSend()
      .subscribe(res -> {
        if (res.statusCode() == 200) {
          JsonObject browsedStore = res.bodyAsJsonObject();
          if (browsedStore.getJsonArray("listingUrls") != null && !browsedStore.getJsonArray("listingUrls").isEmpty()) {
            handler.handle(Future.succeededFuture(browsedStore));
          }
          // HANDLE IF BROWSED STORE IS EMPTY ...
        } else {
          handler.handle(Future.failedFuture(res.bodyAsString()));
        }
      }, t -> {
        handler.handle(Future.failedFuture(t.getCause()));
      });
  }
  
  @Override
  public void getAndCompareHeadersAsync(JsonObject lu, Handler<AsyncResult<JsonObject>> handler) {
    io.vertx.reactivex.ext.web.client.WebClient.create(vertx, new WebClientOptions().setKeepAlive(true))
      .getAbs(lu.getString("listingUrl"))
      .rxSend()
      .subscribe(res -> {
        if (res.statusCode() == 200) {
          JsonObject jsonObject = new JsonObject().mergeIn(lu);
          Iterator<Map.Entry<String, String>> headers = res.headers().iterator();
          JsonObject headersJson = new JsonObject();
          while (headers.hasNext()) {
            Map.Entry<String, String> headerTuple = headers.next();
            headersJson.put(headerTuple.getKey(), headerTuple.getValue());
          }
          jsonObject.put("headers", headersJson);
          jsonObject.put("compare", true);
          handler.handle(Future.succeededFuture(jsonObject));
        } else {
          handler.handle(Future.failedFuture(res.bodyAsString()));
        }
      }, t -> {
        handler.handle(Future.failedFuture(t.getCause()));
      });
  }
  
  @Override
  public void getContentSync(JsonObject lu, Handler<AsyncResult<JsonObject>> handler) {
    client
      .getAbs(lu.getString("listingUrl"))
      .send(res -> {
        if (res.failed()) {
          handler.handle(Future.failedFuture(res.cause()));
        } else {
          if (res.result().statusCode() == 200) {
            JsonObject browsedStore = res.result().bodyAsJsonObject();
            if (browsedStore.getJsonArray("listingUrls") != null && !browsedStore.getJsonArray("listingUrls").isEmpty()) {
              handler.handle(Future.succeededFuture(browsedStore));
            }
          } else {
            handler.handle(Future.failedFuture(res.result().bodyAsString()));
          }
        }
      });
  }
  
  @Override
  public void getAndCompareHeadersSync(JsonObject lu, Handler<AsyncResult<JsonObject>> handler) {
    client
      .getAbs(lu.getString("listingUrl"))
      .send(res -> {
        if (res.failed()) {
          handler.handle(Future.failedFuture(res.cause()));
        } else {
          if (res.result().statusCode() == 200) {
            JsonObject jsonObject = new JsonObject().mergeIn(lu);
            Iterator<Map.Entry<String, String>> headers = res.result().headers().iterator();
            JsonObject headersJson = new JsonObject();
            while (headers.hasNext()) {
              Map.Entry<String, String> headerTuple = headers.next();
              headersJson.put(headerTuple.getKey(), headerTuple.getValue());
            }
            jsonObject.put("headers", headersJson);
            jsonObject.put("compare", true);
            handler.handle(Future.succeededFuture(jsonObject));
          } else {
            handler.handle(Future.failedFuture(res.result().bodyAsString()));
          }
        }
      });
  }
  
  
}
