package com.test.repomigrator;


import com.test.repomigrator.cache.RepomigratorCache;
import io.reactivex.Flowable;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.core.eventbus.MessageConsumer;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class BrowsedProcessing extends AbstractVerticle {
  
  Logger logger = Logger.getLogger(this.getClass().getName());
  
  public static List<JsonObject> openCircuitBrowsedStores = new ArrayList<>();
  public static Flowable<JsonObject> openCircuitBSFlow = Flowable.fromIterable(openCircuitBrowsedStores);
  
  String indyRemoteRepoUrl;
  String authToken;
  String user;
  String pass;
  
  @Override
  public void start() throws Exception {
  
    ConfigRetriever configRetriever = ConfigRetriever.create(vertx);
    configRetriever.getConfig(conf -> {
      if(conf.failed()) {
        logger.info("Config retriving failed");
      }else {
        indyRemoteRepoUrl = conf.result().getString("url");
        authToken = conf.result().getString("authToken");
        user = conf.result().getString("username");
        pass = conf.result().getString("pass");
      }
    });
  
    MessageConsumer<JsonObject> consumer = vertx.eventBus().<JsonObject>consumer("browsed.stores", this::handleBrowsedStores);
    
    MessageConsumer<JsonObject> cbConsumer = vertx.eventBus().<JsonObject>consumer("vertx.circuit-breaker");
    
    
//    cbConsumer.handler((res) -> {
//      if(res.body().getString("state").equalsIgnoreCase("OPEN")) {
//        consumer.handler(msg -> {
//          vertx.eventBus().publish("open.circuit.browsed.stores", msg.body());
//        });
//      }
//    });

//    vertx.eventBus().<JsonObject>consumer("open.circuit.browsed.stores", (res) -> {
//      System.out.println(res.body().encodePrettily());
//    });
  }
  
  // http://indy-admin-master-devel.psi.redhat.com  http://indy-admin-stage.psi.redhat.com
  private void getBrowsedStores(final String name) {
     logger.info("======== HTTP GET REPO: " + name + "============");
    getClient()
      .getAbs(indyRemoteRepoUrl + "/api/browse/maven/remote/"+name)
      .as(BodyCodec.jsonObject())
      .basicAuthentication(user, pass)
      .rxSend()
      .subscribe((res) -> {
        if(res.statusCode()==200) {
          JsonObject data = res.body();
          if(data.getJsonArray("listingUrls") != null && !data.getJsonArray("listingUrls").isEmpty()) {
            vertx.eventBus().publish("listings.urls", data);
          }
        } else {
          logger.info("browsed.stores BAD RESPONSE: " + res.body());
        }
      },
        (t) -> {
        JsonObject errorData =
          new JsonObject()
            .put("source", getClass().getSimpleName().join(".", "get.bs.error"))
            .put("cause", t.getMessage())
            .put("bsname", name);
        vertx.eventBus().publish("processing.errors",errorData);
      })
    
    ;
  }
  
  WebClient getClient() {
//    WebClientOptions webClientOptions = new WebClientOptions().setKeepAlive(true);
//    return WebClient.create(vertx,webClientOptions);
    return WebClient.create(vertx);
  }
  
  void handleBrowsedStores(Message<JsonObject> res) {
    try {
      JsonObject remoteRepo = res.body();
      String storeKey = remoteRepo.getString("key");
      String[] splitStoreKEy = storeKey.split(":");
      String name = splitStoreKEy[2];
    
      getBrowsedStores(name);
      
      
    }
    catch (Exception t) {
      JsonObject errorData =
        new JsonObject()
          .put("source", getClass().getSimpleName().join(".", "parse.bs.rr.error"))
          .put("cause", t.getMessage())
          .put("body", res.body());
      vertx.eventBus().publish("processing.errors",errorData);
    }
  }
  
  void registerBrowsedStoresOnOpenCircuit(Message<JsonObject> res) {
    RepomigratorCache.openCircuitBrowsedStores.add(res.body());
  }
}
