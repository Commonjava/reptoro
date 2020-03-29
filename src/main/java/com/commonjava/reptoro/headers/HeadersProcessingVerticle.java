package com.commonjava.reptoro.headers;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.serviceproxy.ServiceBinder;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class HeadersProcessingVerticle extends AbstractVerticle {

    Logger logger = Logger.getLogger(this.getClass().getName());


    @Override
    public void start() throws Exception {

        // TODO Deploy Headers Proxy Service if nedeeth...

        vertx.setTimer(TimeUnit.SECONDS.toMillis(1) , time -> {
            DeploymentOptions headersOptions = new DeploymentOptions().setWorker(true).setInstances(1).setConfig(config());
            vertx.deployVerticle("com.commonjava.reptoro.headers.ProcessingHeadersVerticle",headersOptions , res -> {
                if(res.succeeded()) {
                    logger.info(">>> Processing Headers Verticle Deployed!");
                } else {
                    logger.info(">>> Problem Deploying Headers Processing Verticle: " + res.cause());
                }
            });
        });

    }
}
