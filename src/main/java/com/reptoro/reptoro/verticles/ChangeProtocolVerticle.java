package com.reptoro.reptoro.verticles;

import com.reptoro.reptoro.common.ReptoroTopics;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import java.util.logging.Logger;

public class ChangeProtocolVerticle extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());

  @Override
  public void start() throws Exception {


    vertx.eventBus().consumer(ReptoroTopics.CHANGE_PROTOCOL , this::handleChangeProtocol);

  }


  void handleChangeProtocol(Message<JsonObject> msg) {
    JsonObject repo = msg.body();
    if(!repo.getJsonObject("browsedStore").containsKey("content"))
      logger.info("[[NO>CONTENT]] " + repo.getString("key"));
    // Get repository metadata object and check for exception messages from indy validation (http-404 , exceptions ... )
    // if there is indy-validation exception messages and this repository doesn't have content then disable it
  }
}
