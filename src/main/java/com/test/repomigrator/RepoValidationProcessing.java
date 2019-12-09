package com.test.repomigrator;

import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;

import java.util.logging.Logger;

public class RepoValidationProcessing extends AbstractVerticle {
  
  Logger logger = Logger.getLogger(this.getClass().getName());
  
  @Override
  public void start() throws Exception {
    vertx.eventBus().<JsonObject>consumer("remote.repository.valid.change", res -> {
//      logger.info("(( VALID CHANGE ))");
//      logger.info(res.body().getString("listingUrl"));
//      logger.info(res.body().getString("sources"));
//      logger.info(String.valueOf(ContentProcessing.contentList.size()));
//      logger.info("========================================================");
    });
  
    vertx.eventBus().<JsonObject>consumer("remote.repository.not.valid.change", res -> {
      logger.info("(( NOT VALID CHANGE ))");
      logger.info(res.body().getString("listingUrl"));
      logger.info(res.body().getString("sources"));
      logger.info(String.valueOf(ContentProcessing.contentList.size()));
      logger.info("========================================================");
    });
  }
}
