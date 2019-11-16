package com.test.repomigrator;

import io.reactivex.Flowable;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ErrorProcessing extends AbstractVerticle {
  
  
  Logger logger = Logger.getLogger(this.getClass().getName());
  
  public static List<JsonObject> errorList = new ArrayList<>();
  public static Flowable<JsonObject> errorFlow = Flowable.fromIterable(errorList);
  
  @Override
  public void start() throws Exception {
    
    vertx.eventBus().<JsonObject>consumer("error.processing", res -> {
      errorList.add(res.body());
//      logger.info(res.body().encodePrettily());
    })
      .exceptionHandler(t -> {
        JsonObject errorData =
          new JsonObject()
            .put("source", getClass().getSimpleName().join(".", "content.error"))
            .put("cause", t.getMessage());
        vertx.eventBus().publish("error.processing", errorData );
      })
    ;
  }
}
