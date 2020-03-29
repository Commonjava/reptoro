package com.commonjava.reptoro.remoterepos;

import io.vertx.cassandra.CassandraClient;
import io.vertx.core.*;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.serviceproxy.ServiceBinder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class RemoteRepositoryVerticle extends AbstractVerticle {

    Logger logger = Logger.getLogger(this.getClass().getName());

    @Override
    public void start() throws Exception {

        WebClientOptions webOptions =
                new WebClientOptions()
                        .setKeepAlive(true)
                        .setTrustAll(true)
                        .setConnectTimeout(60000);

        RemoteRepositoryServiceImpl remoteRepositoryService =
                new RemoteRepositoryServiceImpl(vertx, WebClient.create(vertx, webOptions), config());
        MessageConsumer<JsonObject> service =
                new ServiceBinder(vertx)
                        .setAddress("repo.service")
                        .setTimeoutSeconds(TimeUnit.SECONDS.toMillis(60))
//                    .addInterceptor(msg -> {  })
                        .register(RemoteRepositoryService.class, remoteRepositoryService);


        vertx.setTimer(TimeUnit.SECONDS.toMillis(1) , time -> {
            DeploymentOptions repositoryOptions = new DeploymentOptions().setWorker(true).setConfig(config());
            vertx.deployVerticle("com.commonjava.reptoro.remoterepos.ProcessingRepositoriesVerticle",repositoryOptions , res -> {
                if(res.succeeded()) {
                    logger.info(">>> Processing Repo Verticle Deployed!");
                } else {
                    logger.info(">>> Problem Deploying Repo Processing Verticle: " + res.cause());
                }
            });
        });

    }

}
