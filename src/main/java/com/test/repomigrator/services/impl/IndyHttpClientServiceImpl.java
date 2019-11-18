package com.test.repomigrator.services.impl;

import com.test.repomigrator.services.IndyHttpClientService;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.config.impl.ConfigRetrieverImpl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
//import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;

import javax.swing.plaf.synth.SynthListUI;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author gorgigeorgievski
 */
public class IndyHttpClientServiceImpl implements IndyHttpClientService {
  
  static String INDY_API = "/api";
  static String MAVEN_REPOS = "/admin/stores/maven/remote";
  static String NPM_REPOS = "/admin/stores/npm/remote";
  static String BROWSED_STORES = "/browse/maven/remote/";
  
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
        (packageType.equalsIgnoreCase("maven") || packageType.isEmpty()) ? INDY_API+MAVEN_REPOS : INDY_API+NPM_REPOS)
//      .send(res -> {
      .rxSend()
      .subscribe(res -> {
//        if (res.failed()) {
//          handler.handle(Future.failedFuture(res.cause()));
//        } else {
          if (res.statusCode() == 200) {
            if(!res.getHeader("content-type").equalsIgnoreCase("application/json")) {
              handler.handle(Future.failedFuture(res.bodyAsString()));
            } else {
              handler.handle(Future.succeededFuture(res.bodyAsJsonObject()));
            }
          } else {
            handler.handle(Future.failedFuture(res.bodyAsString()));
          }
//        }
      });
  }
  
  @Override
  public void getListingsFromBrowsedStore(String name, Handler<AsyncResult<JsonObject>> handler) {
    client
      .get(80, "indy-admin-stage.psi.redhat.com", INDY_API+BROWSED_STORES+name)
//      .send(res -> {
      .rxSend()
      .subscribe(res -> {
//        if (res.failed()) {
//          handler.handle(Future.failedFuture(res.cause()));
//        } else {
          if (res.statusCode() == 200) {
            if(!res.getHeader("content-type").equalsIgnoreCase("application/json")) {
              handler.handle(Future.failedFuture(res.bodyAsString()));
            } else {
              handler.handle(Future.succeededFuture(res.bodyAsJsonObject()));
            }
          } else {
            handler.handle(Future.failedFuture(res.bodyAsString()));
          }
//        }
      });
  }
  
  @Override
  public void getContentAsync(JsonObject lu, Handler<AsyncResult<JsonObject>> handler) {
  
//    io.vertx.reactivex.ext.web.client.WebClient.create(vertx, new WebClientOptions().setKeepAlive(true))
      client
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
      .followRedirects(true)
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
    try {
      WebClient client1 = null;
      lu = new URL(listingUrl.getString("sources"));
      URL httpsListingUrl = new URL(HTTPS, lu.getHost(), lu.getPort(), lu.getFile());
      if(!listingUrl.getString("cert").isEmpty()) {
        
        System.out.println("Setting Cert for this https call: \n" + httpsListingUrl);
        
        JksOptions cert = new JksOptions().setValue(Buffer.buffer(listingUrl.getString("cert").getBytes()));
        WebClientOptions options = new WebClientOptions().setSsl(true).setTrustStoreOptions(cert);
        client1 = WebClient.create(vertx, options);
      }
      if(client1 != null) {
        client1
          .head(443, httpsListingUrl.getHost(), httpsListingUrl.getPath())
          .ssl(true)
          .send(res -> {
            if (res.failed()) {
              handler.handle(Future.failedFuture(res.cause()));
            } else {
              if (res.result().statusCode() == 200) {
  
                System.out.println("SUCCESS!");
                
                io.vertx.reactivex.ext.web.client.HttpResponse<io.vertx.reactivex.core.buffer.Buffer> result = res.result();
  
                io.vertx.reactivex.core.MultiMap headers = result.headers();
                
                Map<String, Object> sourceHeaders = new HashMap<>();
                Iterator<Map.Entry<String, String>> iterator = headers.iterator();
                while (iterator.hasNext()) {
                  sourceHeaders.put(iterator.next().getKey(), iterator.next().getValue());
                }
                handler.handle(Future.succeededFuture(new JsonObject(sourceHeaders)));
              } else {
                handler.handle(Future.failedFuture(res.result().bodyAsString()));
              }
            }
          });
      } else {
        client
        .headAbs(httpsListingUrl.toString())
//          .head(443, httpsListingUrl.getHost(), httpsListingUrl.getPath())
          .ssl(true)
          .send(res -> {
            if (res.failed()) {
              handler.handle(Future.failedFuture(res.cause()));
            } else {
              if (res.result().statusCode() == 200) {
                HttpResponse<io.vertx.reactivex.core.buffer.Buffer> result = res.result();
  
                MultiMap headers = result.headers();
                
                Map<String, Object> sourceHeaders = new HashMap<>();
                Iterator<Map.Entry<String, String>> iterator = headers.iterator();
                while (iterator.hasNext()) {
                  sourceHeaders.put(iterator.next().getKey(), iterator.next().getValue());
                }
                handler.handle(Future.succeededFuture(new JsonObject(sourceHeaders)));
              } else {
                handler.handle(Future.failedFuture(res.result().bodyAsString()));
              }
            }
          });
      }
  
    } catch (MalformedURLException mue) {
    
    } catch (Exception e) {
    
    }
  }
  
  
  private ConfigRetrieverOptions getConfigurationOptions() {
    JsonObject path = new JsonObject().put("path", "config/config.json");
    return new ConfigRetrieverOptions().addStore(new ConfigStoreOptions().setType("file").setConfig(path));
  }
}
