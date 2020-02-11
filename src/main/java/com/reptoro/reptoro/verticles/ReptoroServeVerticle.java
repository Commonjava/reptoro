package com.reptoro.reptoro.verticles;

import io.vertx.core.AbstractVerticle;

import java.util.logging.Logger;

public class ReptoroServeVerticle extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());

  @Override
  public void start() throws Exception {
    // Create Http Server and serve SSE and EventBus to UI
  }
}
