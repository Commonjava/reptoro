package com.test.repomigrator.services.impl;

import com.test.repomigrator.services.IndyHttpClientService;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
//import io.vertx.ext.web.client.WebClient;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;

import java.net.URL;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author gorgigeorgievski
 */
public class IndyHttpClientServiceImpl implements IndyHttpClientService {
  
  static String INDY_API;
  static String MAVEN_REPOS = "/admin/stores/maven/remote";
  static String NPM_REPOS = "/admin/stores/npm/remote";
  static String BROWSED_STORES = "/browse/maven/remote/";
  
  Vertx vertx;
  WebClient client;
  JsonObject config;
  String indyHost;
  Integer indyPort;
  String indyUser;
  String indyPass;
  
  
  public IndyHttpClientServiceImpl(WebClient client) {
    this.client = client;
    this.vertx = Vertx.vertx();
  }
  
  public IndyHttpClientServiceImpl(WebClient client,JsonObject config) {
    this.client = client;
    this.vertx = Vertx.vertx();
    this.config = config;
    this.indyHost = config.getString("indy.host");
    this.indyPort = config.getInteger("indy.port");
    this.indyUser = config.getString("indy.user");
    this.indyPass = config.getString("indy.pass");
    this.INDY_API = config.getString("indy.api");
  }
  
  @Override
  public void getAllRemoteRepositories(String packageType, Handler<AsyncResult<JsonObject>> handler) {
    System.out.println("Indy Address: " + "http://" + indyHost + ":" + indyPort + INDY_API + MAVEN_REPOS );
    client
      .get(indyPort, indyHost,
        (packageType.equalsIgnoreCase("maven") || packageType.isEmpty()) ? INDY_API+MAVEN_REPOS : INDY_API+NPM_REPOS)
      .basicAuthentication(indyUser, indyPass)
      .rxSend()
      .subscribe(res -> {
          if (res.statusCode() == 200) {
            if(!res.getHeader("content-type").equalsIgnoreCase("application/json")) {
              handler.handle(Future.failedFuture(res.bodyAsString()));
            } else {
              handler.handle(Future.succeededFuture(res.bodyAsJsonObject()));
            }
          } else {
            handler.handle(Future.failedFuture(res.bodyAsString()));
          }
      });
  }
  
  @Override
  public void getListingsFromBrowsedStore(String name, Handler<AsyncResult<JsonObject>> handler) {
    client
      .get(indyPort, indyHost, INDY_API+BROWSED_STORES+name)
      .basicAuthentication(indyUser, indyPass)
      .timeout(60000)
      .rxSend()
      .subscribe(res -> {
          if (res.statusCode() == 200) {
            if(!res.getHeader("content-type").equalsIgnoreCase("application/json")) {
              handler.handle(Future.failedFuture(res.bodyAsString()));
            } else {
              handler.handle(Future.succeededFuture(res.bodyAsJsonObject()));
            }
          } else {
            JsonObject jsonObject = new JsonObject();
            jsonObject
              .put("http.statusCode", res.statusCode())
              .put("http.statusMsg", res.statusMessage())
              .put("http.response.time", Instant.now())
              .put("http.response.body", res.bodyAsString());
            handler.handle(Future.succeededFuture(jsonObject));
          }
//        }
      },t -> {
        handler.handle(Future.failedFuture(t.getMessage()));
      });
  }
  
  @Override
  public void getContentAsync(JsonObject lu, Handler<AsyncResult<JsonObject>> handler) {
    
      client
      .getAbs(lu.getString("listingUrl"))
      .followRedirects(true)
      .timeout(60000)
      .basicAuthentication(indyUser, indyPass)
      .rxSend()
      .subscribe(res -> {
        if (res.statusCode() == 200) {
          JsonObject browsedStore = res.bodyAsJsonObject();
          if (browsedStore.getJsonArray("listingUrls") != null && !browsedStore.getJsonArray("listingUrls").isEmpty()) {
            handler.handle(Future.succeededFuture(browsedStore));
          }
        } else {
          JsonObject jsonObject = new JsonObject();
          jsonObject
            .put("http.statusCode", res.statusCode())
            .put("http.statusMsg", res.statusMessage())
            .put("headers", res.headers())
            .put("http.response.time", Instant.now())
            .put("http.response.body", res.bodyAsString());
          handler.handle(Future.succeededFuture(jsonObject));
        }
      }, t -> {
        handler.handle(Future.failedFuture(t.getCause()));
      });
  }
  
  @Override
  public void getAndCompareHeadersAsync(JsonObject lu, Handler<AsyncResult<JsonObject>> handler) {
      client
      .headAbs(lu.getString("listingUrl"))
      .followRedirects(true)
      .basicAuthentication(indyUser, indyPass)
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
          jsonObject
            .put("headers", headersJson)
            .put("compare", true)
            .put("http.statusMsg", res.statusMessage())
            .put("http.response.time", Instant.now())
          ;
          handler.handle(Future.succeededFuture(jsonObject));
        } else {
          
          JsonObject jsonObject = new JsonObject().mergeIn(lu);
          Iterator<Map.Entry<String, String>> headers = res.headers().iterator();
          JsonObject headersJson = new JsonObject();
          while (headers.hasNext()) {
            Map.Entry<String, String> headerTuple = headers.next();
            headersJson.put(headerTuple.getKey(), headerTuple.getValue());
          }
          jsonObject
            .put("http.statusCode", res.statusCode())
            .put("http.statusMsg", res.statusMessage())
            .put("headers", headersJson)
            .put("http.response.time", Instant.now())
            .put("missing", true);
          
          handler.handle(Future.succeededFuture(jsonObject));
        }
      }, t -> {
        handler.handle(Future.failedFuture(t.getCause()));
      });
  }
  
  @Override
  public void getContentSync(JsonObject lu, Handler<AsyncResult<JsonObject>> handler) {
    client
      .getAbs(lu.getString("listingUrl"))
      .followRedirects(true)
      .basicAuthentication(indyUser, indyPass)
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
      .headAbs(lu.getString("listingUrl"))
      .followRedirects(true)
      .basicAuthentication(indyUser, indyPass)
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
  
  @Override
  public void getAndCompareSourceHeaders(JsonObject listingUrl, Handler<AsyncResult<JsonObject>> handler) {
    URL lu = null;
    String HTTPS = "https";
    URL httpsListingUrl = null;
    try {
      lu = new URL(listingUrl.getString("sources"));
      httpsListingUrl = new URL(HTTPS, lu.getHost(), lu.getPort(), lu.getFile());
    } catch (Exception e) {
      handler.handle(Future.failedFuture(e.getMessage()));
    }
    client
      .headAbs(httpsListingUrl.toString())
      .ssl(true)
      .rxSend()
      .subscribe(res -> {
        if(res.statusCode() == 200) {
          JsonObject jsonObject = new JsonObject().mergeIn(listingUrl);
          
          MultiMap sourceHeaders = res.headers();
          HashMap<String, Object> headers = new HashMap<>();
          Iterator<Map.Entry<String, String>> iterator = sourceHeaders.iterator();
          while (iterator.hasNext()) {
            Map.Entry<String, String> next = iterator.next();
            headers.put(next.getKey(),next.getValue());
          }
          jsonObject.put("headers", headers);
          jsonObject.put("http.response.time", Instant.now());
          handler.handle(Future.succeededFuture(jsonObject));
        } else {
          JsonObject jsonObject = new JsonObject().mergeIn(listingUrl);
  
          MultiMap sourceHeaders = res.headers();
          HashMap<String, Object> headers = new HashMap<>();
          Iterator<Map.Entry<String, String>> iterator = sourceHeaders.iterator();
          while (iterator.hasNext()) {
            Map.Entry<String, String> next = iterator.next();
            headers.put(next.getKey(),next.getValue());
          }
          
          jsonObject
            .put("http.statusCode", res.statusCode())
            .put("http.statusMsg", res.statusMessage())
            .put("headers", headers)
            .put("http.response.time", Instant.now())
            .put("http.response.body", res.bodyAsString());
  
          handler.handle(Future.succeededFuture(jsonObject));
        }
      }, t -> {
        handler.handle(Future.failedFuture(t.getCause()));
      });
    
  }
  
}
