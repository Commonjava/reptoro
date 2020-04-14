package com.commonjava.reptoro.contents;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.serviceproxy.ServiceBinder;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ContentProcessingVerticle extends AbstractVerticle {

    Logger logger = Logger.getLogger(this.getClass().getName());

    @Override
    public void start() throws Exception {

        WebClientOptions webOptions =
                new WebClientOptions()
                        .setKeepAlive(true)
                        .setTrustAll(true)
                        .setConnectTimeout(60000);

        ContentProcessingServiceImpl contentProcessingService =
                new ContentProcessingServiceImpl(vertx, WebClient.create(vertx, webOptions), config());

        new ServiceBinder(vertx)
                .setAddress("content.service")
                .setTimeoutSeconds(TimeUnit.SECONDS.toMillis(60))
//                    .addInterceptor(msg -> {  })
                .register(ContentProcessingService.class, contentProcessingService);

        vertx.setTimer(TimeUnit.SECONDS.toMillis(1) , time -> {
            DeploymentOptions contentOptions = new DeploymentOptions().setWorker(true).setConfig(config());
            vertx.deployVerticle("com.commonjava.reptoro.contents.ProcessingContentVerticle",contentOptions , res -> {
                if(res.succeeded()) {
                    logger.info(">>> Processing Content Verticle Deployed! ID: " + res.result());
                } else {
                    logger.info(">>> Problem Deploying Content Processing Verticle: " + res.cause());
                }
            });
            DeploymentOptions contentSaveOptions = new DeploymentOptions().setWorker(true).setInstances(10).setConfig(config());
            vertx.deployVerticle("com.commonjava.reptoro.contents.SaveContentVerticle",contentSaveOptions , res -> {
                if(res.succeeded()) {
                    logger.info(">>> Processing Save Content Verticle Deployed! ID:" + res.result());
                } else {
                    logger.info(">>> Problem Deploying Content Processing Verticle: " + res.cause());
                }
            });
            DeploymentOptions headersSaveOptions = new DeploymentOptions().setWorker(true).setInstances(10).setConfig(config());
            vertx.deployVerticle("com.commonjava.reptoro.contents.SaveHeadersVerticle",headersSaveOptions , res -> {
                if(res.succeeded()) {
                    logger.info(">>> Save Content Headers Verticle Deployed! " + res.result());
                } else {
                    logger.info(">>> Problem Deploying Content Headers Verticle: " + res.cause());
                }
            });
        });


    }

}
