package com.test.repomigrator;

import com.test.repomigrator.cache.RepomigratorCache;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;

import java.util.logging.Logger;

public class ErrorProcessing extends AbstractVerticle {
  
  
  Logger logger = Logger.getLogger(this.getClass().getName());
  
  @Override
  public void start() throws Exception {
//    logger.log(Level.INFO,"[[START]] {0}" , this.getClass().getName());
    
    vertx.eventBus().<String>consumer("error.processing", res -> {
      RepomigratorCache.errorList.add(Json.decodeValue(res.body(), JsonObject.class));
    });
  }
}
