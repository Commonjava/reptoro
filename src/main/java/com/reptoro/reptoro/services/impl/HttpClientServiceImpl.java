package com.reptoro.reptoro.services.impl;

import com.reptoro.reptoro.services.HttpClientService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

//import io.vertx.ext.web.client.WebClient;

/**
 * @author gorgigeorgievski
 */
public class HttpClientServiceImpl implements HttpClientService {

  Logger logger = Logger.getLogger(this.getClass().getName());

  static String INDY_API;
  static String MAVEN_REPOS = "/admin/stores/maven/remote";
  static String NPM_REPOS = "/admin/stores/npm/remote";
  static String BROWSED_STORES = "/browse/maven/remote/";
  static String CONTENT_STORES = "/content/maven/remote/";

  Vertx vertx;
  WebClient client;
  JsonObject config;
  String indyHost;
  Integer indyPort;
  String indyUser;
  String indyPass;


  public HttpClientServiceImpl(WebClient client) {
    this.client = client;
    this.vertx = Vertx.vertx();
  }

  public HttpClientServiceImpl(WebClient client, JsonObject config) {
    this.client = client;
    this.vertx = Vertx.currentContext().owner();
    this.config = config;
    this.indyHost = config.getString("indy.host");
    this.indyPort = config.getInteger("indy.port");
    this.indyUser = config.getString("indy.user");
    this.indyPass = config.getString("indy.pass");
    this.INDY_API = config.getString("indy.api");
  }

  @Override
  public void getAllRemoteRepositories(String packageType, Handler<AsyncResult<JsonObject>> handler) {
    logger.info("[[INDY]] " + "http://" + indyHost + ":" + indyPort + ((packageType.equalsIgnoreCase("maven") || packageType.isEmpty()) ? INDY_API+MAVEN_REPOS : INDY_API+NPM_REPOS) );
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
	  .send(res -> {
		  if(res.succeeded()) {
			  if(res.result().statusCode() == 200) {
				  if(!res.result().getHeader("content-type").equalsIgnoreCase("application/json")) {
					  handler.handle(Future.failedFuture(res.result().bodyAsString()));
				  } else {
					  handler.handle(Future.succeededFuture(res.result().bodyAsJsonObject()));
				  }
			  } else {
				  JsonObject jsonObject = new JsonObject();
					jsonObject
					  .put("http.statusCode", res.result().statusCode())
					  .put("http.statusMsg", res.result().statusMessage())
					  .put("http.response.time", Instant.now())
					  .put("http.response.body", res.result().bodyAsString());
					handler.handle(Future.succeededFuture(jsonObject));
			  }
		  }
	  });


//      .rxSend()
//      .subscribe(res -> {
//          if (res.statusCode() == 200) {
//            if(!res.getHeader("content-type").equalsIgnoreCase("application/json")) {
//              handler.handle(Future.failedFuture(res.bodyAsString()));
//            } else {
//              handler.handle(Future.succeededFuture(res.bodyAsJsonObject()));
//            }
//          } else {
//            JsonObject jsonObject = new JsonObject();
//            jsonObject
//              .put("http.statusCode", res.statusCode())
//              .put("http.statusMsg", res.statusMessage())
//              .put("http.response.time", Instant.now())
//              .put("http.response.body", res.bodyAsString());
//            handler.handle(Future.succeededFuture(jsonObject));
//          }
//      },t -> {
//        handler.handle(Future.failedFuture(t.getMessage()));
//      });
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
  public void getLocalContentHeadersSync(JsonObject content, Handler<AsyncResult<JsonObject>> handler) {
    String repoKey = content.getString("filesystem");
    String repoName = repoKey.split(":")[repoKey.split(":").length - 1];

    String path = INDY_API + CONTENT_STORES + repoName + content.getString("parentpath") +"/" + content.getString("filename");
    logger.info("[[HEADERS.LOCAL]] " + "http://" +  indyHost + ":" + indyPort + path);
    client
      .head(indyPort,indyHost,path)
      .followRedirects(true)
//      .basicAuthentication(indyUser, indyPass)
      .send(res -> {
        if (res.failed()) {
          handler.handle(Future.failedFuture(res.cause()));
        } else {
//          logger.info("http: " + res.result());
          if (res.result().statusCode() == 200) {
//            logger.info("http-200: " + res.result());
            JsonObject jsonObject = new JsonObject(); //.mergeIn(content);
            Iterator<Map.Entry<String, String>> headers = res.result().headers().iterator();
            JsonObject headersJson = new JsonObject();
            while (headers.hasNext()) {
              Map.Entry<String, String> headerTuple = headers.next();
              headersJson.put(headerTuple.getKey(), headerTuple.getValue());
            }
            handler.handle(Future.succeededFuture(headersJson));
//            jsonObject.put("headers", headersJson);
//            jsonObject.put("compare", true);
//            handler.handle(Future.succeededFuture(jsonObject));
          } else {
            handler.handle(Future.failedFuture(res.result().bodyAsString()));
          }
        }
      });
  }

  @Override
  public void getRemoteContentHeadersSync(JsonObject content,Handler<AsyncResult<JsonObject>> handler) {
    String path = content.getString("parentpath") + "/" + content.getString("filename");
    String source = content.getString("source");
    URL sourceUrl = null;
    String HTTPS = "https";
    URL httpsSourceUrl = null;

    try {
      sourceUrl = new URL(source);
      httpsSourceUrl = new URL(HTTPS, sourceUrl.getHost(), sourceUrl.getPort(), sourceUrl.getPath().substring(0,sourceUrl.getPath().length()-1) + path);
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }

    logger.info("[[HEADERS.REMOTE]] " + httpsSourceUrl.toString());

    client
      .headAbs(httpsSourceUrl.toString())
      .followRedirects(true)
//      .basicAuthentication(indyUser, indyPass)
      .send(res -> {
        if (res.failed()) {
          handler.handle(Future.failedFuture(res.cause()));
        } else {
          if (res.result().statusCode() == 200) {
            JsonObject jsonObject = new JsonObject(); //.mergeIn(content);
            Iterator<Map.Entry<String, String>> headers = res.result().headers().iterator();
            JsonObject headersJson = new JsonObject();
            while (headers.hasNext()) {
              Map.Entry<String, String> headerTuple = headers.next();
              headersJson.put(headerTuple.getKey(), headerTuple.getValue());
            }
            handler.handle(Future.succeededFuture(headersJson));
//
//            jsonObject.put("headers", headersJson);
//            jsonObject.put("compare", true);
//            handler.handle(Future.succeededFuture(jsonObject));
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
