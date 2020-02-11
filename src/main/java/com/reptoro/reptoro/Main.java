package com.reptoro.reptoro;

import com.reptoro.reptoro.common.ChecksumCompare;
import com.reptoro.reptoro.common.EventBusChannels;
import com.reptoro.reptoro.common.ReptoroTopics;
import com.reptoro.reptoro.common.ReptoroConfig;
import com.reptoro.reptoro.verticles.*;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.logging.Logger;

public class Main {

	private static Logger logger = Logger.getLogger(Main.class.getName());
	private final static String EXCLUDED_REMOTE_REPOSITORIES = "indy.except.remote.repositories";

	public static void main(String[] args) {

		Vertx vertx = Vertx.vertx();
    EventBus eb = vertx.eventBus();
    ConfigRetriever configRetriever = ConfigRetriever.create(vertx, ReptoroConfig.getConfiguration());

		configRetriever.getConfig(ar -> {
			if (ar.succeeded()) {

        JsonObject config = ar.result();

        JsonArray exceptRepos = config.getJsonArray(EXCLUDED_REMOTE_REPOSITORIES);
        logger.info("Excepted Repos: " + exceptRepos.encodePrettily());

        // ---
//        vertx.deployVerticle(BrowsedStoreVerticle.class.getName(), res -> {
//          if (res.succeeded()) {
//            logger.info("-\t\t\t\t " + BrowsedStoreVerticle.class.getName() + " Deployed!");
//          } else {
//            logger.info("- BrowsedVerticle NOT Deployed!");
//            logger.info("- Cause: " + res.cause());
//          }
//        });
//
//        DeploymentOptions contentOptions = new DeploymentOptions().setWorker(true).setInstances(4).setConfig(config);
//        vertx.deployVerticle(ContentProcessingVerticle.class.getName(), contentOptions, res -> {
//          if (res.succeeded()) {
//            logger.info("-\t\t\t\t " + ContentProcessingVerticle.class.getName() + " Deployed!");
//          } else {
//            logger.info("- ContentProcessingVerticle NOT Deployed!");
//            logger.info("- Cause: " + res.cause());
//          }
//        });
//
//        DeploymentOptions repoOptions = new DeploymentOptions().setWorker(true).setInstances(1).setConfig(config);
//        vertx.deployVerticle(RemoteRepositoryVerticle.class.getName(), repoOptions, res -> {
//          if (res.succeeded()) {
//            logger.info("-\t\t\t\t " + RemoteRepositoryVerticle.class.getName() + " Deployed!");
//          } else {
//            logger.info("- RemoteRepositoryVerticle NOT Deployed!");
//            logger.info("- Cause: " + res.cause());
//          }
//        });

        DeploymentOptions proxyOptions = new DeploymentOptions().setWorker(true).setInstances(1).setConfig(config);
        vertx.deployVerticle(ProxyClientVerticle.class.getName(),proxyOptions, res -> {
					if (res.succeeded()) {
						logger.info("-\t\t\t\t " + ProxyClientVerticle.class.getName() + " Deployed!!!");

						vertxDeployVerticle(vertx,RemoteRepositoryVerticle.class.getName(),"",1,true,config);
						vertxDeployVerticle(vertx,BrowsedStoreVerticle.class.getName(),"",1,true,config);
						vertxDeployVerticle(vertx,ContentProcessingVerticle.class.getName(),"",4,true,config);
            vertxDeployVerticle(vertx, ChangeProtocolVerticle.class.getName(),"",1,true,config);
            vertxDeployVerticle(vertx,ReptoroServeVerticle.class.getName(),"",0,false,config);

						eb.send(ReptoroTopics.REMOTE_REPO_START , new JsonObject().put("remote.repos.type" , "maven"));
					}
				});
			} else {
				logger.info("- Problem loadding main configuration...");
			}
		});

		configRetriever.listen(change -> {
      JsonObject newConfiguration = change.getNewConfiguration();
      JsonObject previousConfiguration = change.getPreviousConfiguration();

      vertx.eventBus().publish(ReptoroTopics.CONFIGURATION_CHANGED , newConfiguration);

    });

    ChecksumCompare.checksumCompareResultsFlowable
      .subscribe((nxt) -> {
        logger.info("Result Map size: " + ChecksumCompare.checksumCompareResults.size() );
        logger.info("Result Array size: " + ChecksumCompare.checksumCompareResults.get("maven:remote:central").size());
        logger.info("=============================================================================");
      });

	}

	private static void vertxDeployVerticle(Vertx vertx,String name,String type,Integer instances,Boolean worker,JsonObject config) {
	  DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config);
	  if(instances!=0) { deploymentOptions.setInstances(instances); }
	  if(worker) { deploymentOptions.setWorker(worker); }
    vertx.deployVerticle(name,deploymentOptions,res -> {
      if(res.succeeded()) {
        logger.info("=> Verticle [" + name + "] is deployed @ " + Instant.now());
      } else {
        logger.info("=> Verticle [" + name + "] failed to deploy @ " + Instant.now());
      }
    });
  }
}
