package com.reptoro.reptoro.common;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RemoteRepos {

  Logger logger = Logger.getLogger(this.getClass().getName());

  private Vertx vertx;
  private JsonObject config;

  public RemoteRepos() {
    this.vertx = Vertx.vertx();
  }

  public RemoteRepos(JsonObject config) {
    this.vertx = Vertx.vertx();
    this.config = config;
  }

  public Boolean compareProtocol(JsonObject repo) {
    String protocol = repo.getString("url").split("//")[0];
    return !protocol.equalsIgnoreCase("https:");
  }

  public Boolean filterPromotedRepos(JsonObject repo) {
    String name = repo.getString("name");
    return !name.contains("Promote");
  }

  public Boolean filterKojiRepos(JsonObject repo) {
    String name = repo.getString("name");
    return !name.contains("koji-");
  }

  public Boolean filterDisabledRepos(JsonObject repo) {
    return !repo.getBoolean("disabled");
  }

  public Boolean filterRemoteRepos(JsonObject repo) {
    return repo.getString("type").equalsIgnoreCase("remote");
  }

  public Boolean filterExceptedRepos(JsonObject repo) {
    List<String> exceptedRepos = config.getJsonArray("indy.except.remote.repositories").getList();

    String key = repo.getString("key");
    for(String repoKey : exceptedRepos) {
//      logger.info("=>\t\t\t Checking repository: " + key + " with excluded repo: " + repoKey);
      if(repoKey.equalsIgnoreCase(key)) {
        logger.info("=> Exclude Repository: " + key);
        return false;
      }
    }
    return true;
  }

  public JsonObject publishRepos(JsonObject repo) {
    repo.put("timestamp.rr", Instant.now());
    logger.info("=> Publishing: " + repo.getString("name"));
    vertx.eventBus().publish("browsed.store", repo);
    return repo;
  }

  public CompletableFuture<List<JsonObject>> processAllRemoteRepos(JsonObject repos) {
    return CompletableFuture.supplyAsync(new Supplier<List<JsonObject>>() {
      @Override
      public List<JsonObject> get() {
        RemoteRepos remoteRepo = new RemoteRepos(config);
        return repos
          .getJsonArray("items")
          .stream()
          .map(entry -> new JsonObject(entry.toString()))
          .filter(remoteRepo::filterRemoteRepos)
          .filter(remoteRepo::filterDisabledRepos)
          .filter(remoteRepo::compareProtocol)
          .filter(remoteRepo::filterPromotedRepos)
          .filter(remoteRepo::filterKojiRepos)
          .filter(remoteRepo::filterExceptedRepos)
//          .map(remoteRepo::publishRepos)
          .collect(Collectors.toList());
      }
    });
  }
}
