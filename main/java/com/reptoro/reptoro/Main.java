package com.reptoro.reptoro;

import com.reptoro.reptoro.config.ReptoroConfig;
import com.reptoro.reptoro.services.HttpClientService;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.logging.Logger;

public class Main {

	private static Logger logger = Logger.getLogger(Main.class.getName());

	public static void main(String[] args) {

		Vertx vertx = Vertx.vertx();

		ConfigRetrieverOptions configOption = ReptoroConfig.getConfiguration();
		ConfigRetriever configRetriever = ConfigRetriever.create(vertx, configOption);


		// com.reptoro.reptoro.verticles.BrowsedStoreVerticle
//		DeploymentOptions bsOptions = new DeploymentOptions().setInstances(1).setWorker(true);
		vertx.deployVerticle("com.reptoro.reptoro.verticles.BrowsedStoreVerticle", res -> {
			if (res.succeeded()) {
				logger.info("- BrowsedStoreVerticle Deployed!");
			} else {
				logger.info("- BrowsedVerticle NOT Deployed!");
				logger.info("- Cause: " + res.cause());
			}
		});

		// com.reptoro.reptoro.verticles.RemoteRepositoryVerticle
		DeploymentOptions repoOptions = new DeploymentOptions().setWorker(true).setInstances(1);
		vertx.deployVerticle("com.reptoro.reptoro.verticles.RemoteRepositoryVerticle", repoOptions, res -> {
			if (res.succeeded()) {
				logger.info("- RemoteRepositoryVerticle Deployed!");
			} else {
				logger.info("- RemoteRepositoryVerticle NOT Deployed!");
				logger.info("- Cause: " + res.cause());
			}
		});

		// com.reptoro.reptoro.verticles.ContentProcessingVerticle
    DeploymentOptions contentOptions = new DeploymentOptions().setWorker(true).setInstances(4);
    vertx.deployVerticle("com.reptoro.reptoro.verticles.ContentProcessingVerticle", contentOptions, res -> {
      if (res.succeeded()) {
        logger.info("- ContentProcessingVerticle Deployed!");
      } else {
        logger.info("- ContentProcessingVerticle NOT Deployed!");
        logger.info("- Cause: " + res.cause());
      }
    });
		configRetriever.getConfig(ar -> {
			if (ar.succeeded()) {

				vertx.deployVerticle("com.reptoro.reptoro.verticles.ProxyClientVerticle",
				  new DeploymentOptions().setConfig(ar.result()), res -> {
					if (res.succeeded()) {
						logger.info("- ProxyClientVerticle Deployed!!!");
						HttpClientService proxy = HttpClientService.createProxy(vertx, "indy.http.client.service");
						proxy.getAllRemoteRepositories("maven", hndlr -> {
							if (hndlr.succeeded()) {
								vertx.eventBus().<JsonObject>send("remote.repo", hndlr.result());
							}
						});
					}
				});
			} else {
				logger.info("- Problem loadding main configuration...");
			}
		});


	}
}
