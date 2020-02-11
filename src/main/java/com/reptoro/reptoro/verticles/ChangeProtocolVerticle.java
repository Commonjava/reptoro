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

    logger.info(repo.encodePrettily());

  }
}
