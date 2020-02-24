package com.reptoro.reptoro.verticles;

import com.reptoro.reptoro.common.RemoteRepos;
import com.reptoro.reptoro.common.ReptoroTopics;
import com.reptoro.reptoro.services.HttpClientService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static com.reptoro.reptoro.verticles.BrowsedStoreVerticle.INDY_HTTP_CLIENT_PROXY_SERVICE;

/**
 *
 * @author gorgigeorgievski
 */


// Worker Verticle - Fetching all remote repositories from indy and then processing them:
//        filtering,aggregating and transforming
public class RemoteRepositoryVerticle extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());
  HttpClientService proxy;


  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    proxy = HttpClientService.createProxy(vertx, INDY_HTTP_CLIENT_PROXY_SERVICE);
  }

  @Override
  public void start() throws Exception {

    vertx.eventBus().<JsonObject>consumer(ReptoroTopics.REMOTE_REPO_START , this::handleProcessingRemoteRepos);

    vertx.eventBus().<JsonObject>consumer(ReptoroTopics.REMOTE_REPO, this::handleRemoteRepositoryMsg);

    vertx.eventBus().consumer(ReptoroTopics.REMOTE_REPOS_FILTERED , this::handleRemoteRepositoryFiltered);

  }

  void handleProcessingRemoteRepos(Message<JsonObject> msg) {
    JsonObject body = msg.body();
    String remoteRepositoriesType = body.getString("remote.repos.type");

    proxy.getAllRemoteRepositories(remoteRepositoriesType, hndlr -> {
      if (hndlr.succeeded()) {
        vertx.eventBus().send(ReptoroTopics.REMOTE_REPO, hndlr.result());
      } else {
        logger.info("[[FAILED]] " + hndlr.cause());
      }
    });
  }

  void handleRemoteRepositoryMsg(Message<JsonObject> msg) {
    JsonObject remoteRepositoriesJson = msg.body();

    new RemoteRepos(config())
      .processAllRemoteRepos(remoteRepositoriesJson)
      .thenApply(this::publishFilteredRepositories)
      .thenApply((data) -> {
        final SharedData sharedData = vertx.sharedData();
        sharedData.getLocalAsyncMap("remote.repositories" , res -> {
          if(res.succeeded()) {
            AsyncMap<Object, Object> asyncMap = res.result();
            try {
              List<JsonObject> jsonObjects = data.get();
              asyncMap.putIfAbsent("repos" , jsonObjects , ar -> {
                if(ar.succeeded()) {
                  logger.info("[[ASYNCMAP.REPOS.SIZE]] " + jsonObjects.size());
                }
              });
            } catch (InterruptedException e) {
              logger.info("[[EXCEPTION.INTERRUPTED]] " + e.getCause() );
            } catch (ExecutionException e) {
              logger.info("[[EXCEPTION.EXECUTION]] " + e.getCause() );
            }


          } else {
            logger.info("[[ASYNCMAP.FAILED]] " + res.cause());
          }
        });
        return null;
      })
    ;
  }

  void handleRemoteRepositoryFiltered(Message<JsonArray> msg) {
    JsonArray filteredRepositories = msg.body();
//    logger.info("Filtered Repositories: \n");
//    logger.info(filteredRepositories.encodePrettily());
  }

  CompletableFuture<List<JsonObject>> publishFilteredRepositories(List<JsonObject> repos) {
    return CompletableFuture.supplyAsync(new Supplier<List<JsonObject>>() {
      @Override
      public List<JsonObject> get() {
        vertx.eventBus().publish(ReptoroTopics.BROWSED_STORES, new JsonArray( repos ));
        return repos;
      }
    });
  }
}
