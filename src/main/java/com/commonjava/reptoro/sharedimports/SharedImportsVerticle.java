package com.commonjava.reptoro.sharedimports;

import com.commonjava.reptoro.common.Const;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.serviceproxy.ServiceBinder;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class SharedImportsVerticle extends AbstractVerticle {

    Logger logger = Logger.getLogger(this.getClass().getName());


    @Override
    public void start() throws Exception {
      WebClientOptions webOptions =
        new WebClientOptions()
          .setKeepAlive(true)
          .setTrustAll(true)
          .setConnectTimeout(60000);

      SharedImportsServiceImpl sharedImportsService =
        new SharedImportsServiceImpl(vertx, WebClient.create(vertx, webOptions), config());

      MessageConsumer<JsonObject> service =
        new ServiceBinder(vertx)
          .setAddress(Const.SHARED_IMPORTS_SERVICE)
          .setTimeoutSeconds(TimeUnit.SECONDS.toMillis(60))
//                    .addInterceptor(msg -> {  })
          .register(SharedImportsService.class, sharedImportsService);



      vertx.setTimer(TimeUnit.SECONDS.toMillis(1) , time -> {
        DeploymentOptions sharedImportOptions = new DeploymentOptions().setWorker(true).setConfig(config());
        vertx.deployVerticle("com.commonjava.reptoro.sharedimports.ProcessingSharedImportsVerticle",sharedImportOptions , res -> {
          if(res.succeeded()) {
            logger.info(">>> Processing Shared Imports Verticle Deployed! ID: "+ res.result());
          } else {
            logger.info(">>> Problem Deploying Shared Imports Processing Verticle: " + res.cause());
          }
        });

        DeploymentOptions comparingImportsOptions = new DeploymentOptions().setInstances(10).setWorker(true).setConfig(config());
        vertx.deployVerticle("com.commonjava.reptoro.sharedimports.ComparingSharedImportsVerticle",comparingImportsOptions , res -> {
          if(res.succeeded()) {
            logger.info(">>> Comparing Shared Imports Verticle Deployed! ID: " + res.result());
          } else {
            logger.info(">>> Problem Deploying Comparing Shared Imports Verticle: " + res.cause());
          }
        });
      });
    }


}
