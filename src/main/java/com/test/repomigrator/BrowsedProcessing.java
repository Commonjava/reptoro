package com.test.repomigrator;


import com.test.repomigrator.services.IndyHttpClientService;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.Message;

import java.util.logging.Logger;

public class BrowsedProcessing extends AbstractVerticle {
  
  Logger logger = Logger.getLogger(this.getClass().getName());
  
  static int counter = 0;
  
  
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
      String[] splitStoreKey = storeKey.split(":");
      String name = splitStoreKey[2];
      
      logger.info("\t\t\t========= < " + ++counter + " : " + name + " > ===========");
      indyHttpClientService.getListingsFromBrowsedStore(name , ar -> {
        if(ar.failed()) {
          logger.info("Fail Reciving Browsed Store: " + name + " cause: " + ar.cause());
        } else {
          JsonObject result = ar.result();
          if(result.getJsonArray("listingUrls") != null && !result.getJsonArray("listingUrls").isEmpty()) {
            vertx.eventBus().<JsonObject>publish("listings.urls", result);
          }
        }
      });
  }
  
}
