package com.commonjava.reptoro.stores;

import io.vertx.core.AbstractVerticle;

import java.util.logging.Logger;

public class BrowsedStoreVerticle extends AbstractVerticle {

    Logger logger = Logger.getLogger(this.getClass().getName());

    @Override
    public void start() throws Exception {

      logger.info("\n\n\n BROWSED STORE VERTICLE DEPLOYED\n OWNER: " + context.owner() + "\nID: " + context.deploymentID());


//      vertx.eventBus().<String>consumer("publish.to.client", msg -> {
//        logger.info("Recived message: " + msg.body());
//      });


    }
}
