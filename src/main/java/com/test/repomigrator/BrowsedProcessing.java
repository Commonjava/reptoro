package com.test.repomigrator;


import com.test.repomigrator.services.IndyHttpClientService;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.Message;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class BrowsedProcessing extends AbstractVerticle {
  
  Logger logger = Logger.getLogger(this.getClass().getName());
  
  static AtomicLong counter = null;
  
  static {
    if(counter == null) {
      counter = new AtomicLong();
    }
  }
  
  
  @Override
  public void start() throws Exception {
    
    vertx.eventBus().<JsonObject>consumer("browsed.stores", this::handleBrowsedStores)
      .exceptionHandler(t -> {
        JsonObject errorData =
          new io.vertx.core.json.JsonObject()
            .put("source", getClass().getSimpleName().join(".", "content.error"))
            .put("cause", t.getMessage());
        vertx.eventBus().publish("error.processing", errorData );
      })
    ;
    
    
  }
  
  
  void handleBrowsedStores(Message<JsonObject> res) {
      IndyHttpClientService indyHttpClientService =
        IndyHttpClientService.createProxy(vertx.getDelegate(), RemoteRepositoryProcessing.INDY_HTTP_CLIENT_SERVICE);
      
      JsonObject remoteRepo = res.body();
      String storeKey = remoteRepo.getString("key");
      String cert = remoteRepo.getString("server_certificate_pem");
      String[] splitStoreKey = storeKey.split(":");
      String name = splitStoreKey[2];
      
      
      indyHttpClientService.getListingsFromBrowsedStore(name , ar -> {
        if(ar.failed()) {
          logger.info("Fail Reciving Browsed Store: " + name + " cause: " + ar.cause());
        } else {
          logger.info("\t\t\t========= < " + counter.incrementAndGet() + " : " + name + " > ===========");
          JsonObject result = ar.result();
          if(result.containsKey("storeKey") && result.getJsonArray("listingUrls") != null && !result.getJsonArray("listingUrls").isEmpty()) {
            result.put("cert", (cert == null || cert.isEmpty() ) ? cert : "" );
            vertx.eventBus().<JsonObject>publish("listings.urls", result);
          } else if(result.containsKey("http.statusCode")) {
            logger.info("Browsed Store HTTP Status Code: " + result.getString("http.statusCode"));
            logger.info("Browsed Store Name: " + name);
          }
        }
      });
      
      // Replying to publisher ...
      res.reply(new JsonObject().put("status", "ok"));
  }
  
}
