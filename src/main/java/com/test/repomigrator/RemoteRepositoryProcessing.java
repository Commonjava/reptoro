package com.test.repomigrator;

import io.reactivex.Flowable;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.ext.reactivestreams.ReactiveReadStream;
import io.vertx.ext.reactivestreams.ReactiveWriteStream;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.core.eventbus.MessageProducer;
import io.vertx.reactivex.ext.web.client.WebClient;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class RemoteRepositoryProcessing extends AbstractVerticle {
  
  Logger logger = Logger.getLogger(this.getClass().getName());
  
  String indyRemoteRepoUrl;
  String authToken;
  String user;
  String pass;
  
  @Override
  public void start() throws Exception {
    
    ConfigRetriever configRetriever = ConfigRetriever.create(vertx);
    configRetriever.getConfig(conf -> {
      if(conf.failed()) {
        logger.info("Config retriving failed");
      }else {
        indyRemoteRepoUrl = conf.result().getString("url");
        authToken = conf.result().getString("authToken");
        user = conf.result().getString("username");
        pass = conf.result().getString("pass");
      }
    });
    
    getRemoteRepositories();
    
    vertx.eventBus().consumer("remote.repos", this::handleRemoteRepositories);
//      vertx.eventBus().<JsonObject>consumer("vertx.circuit-breaker", msg -> {});
  }
  
  
  WebClient getClient() {
     WebClientOptions webClientOptions = new WebClientOptions().setKeepAlive(true);
    return WebClient.create(vertx,webClientOptions);
  }
  
  // http://indy-admin-master-devel.psi.redhat.com/  http://indy-admin-stage.psi.redhat.com
  void getRemoteRepositories() {
    getClient()
      .getAbs(indyRemoteRepoUrl + "/api/admin/stores/maven/remote/")
      .basicAuthentication(user, pass)
      .rxSend()
      .subscribe((res) -> {
        if(res.statusCode()==200) {
          vertx.eventBus().publish("remote.repos", res.bodyAsJsonObject() );
        } else {
          logger.info("remote repos BAD RESPONSE" + res.statusMessage());
        }
      },
        (t) -> {
        JsonObject errorData =
          new JsonObject()
            .put("source", getClass().getSimpleName().join(".", "get.repo.error"))
            .put("cause", t.getMessage());
        vertx.eventBus().publish("processing.errors",errorData);
      });
  }
  
  Boolean compareProtocol(JsonObject repo) {
    String protocol = repo.getString("url").split("//")[0];
    return !protocol.equalsIgnoreCase("https:");
  }
  
  void handleRemoteRepositories(Message<JsonObject> res) {
    JsonObject body = res.body();
  
    JsonArray remoteRepos = body.getJsonArray("items");
  
    //
    List<JsonObject> repos =
      remoteRepos
        .stream()
        .map(entry -> new JsonObject(entry.toString()))
        .filter(this::filterRemoteRepos)
        .filter(this::filterDisabledRepos)
        .filter(this::compareProtocol)
        .filter(this::filterPromotedRepos)
        .collect(Collectors.toList());
  
    Flowable<JsonObject> repoFlow = Flowable.fromIterable(repos);
    ReactiveReadStream<JsonObject> repoReadStream = ReactiveReadStream.readStream();
    ReactiveWriteStream<JsonObject> writeStream = ReactiveWriteStream.writeStream(Vertx.vertx());
    MessageProducer<JsonObject> bsPublisher = vertx.eventBus().<JsonObject>publisher("browsed.stores");
    
  
    // Change http protocol on start and then process the content
    // and then after all the content files are done then change http protocol of remote repo
    
    
    Flowable<Long> interval =
      Flowable.interval(10, TimeUnit.SECONDS);
  
    Flowable
      .zip(repoFlow, interval,(obs,timer) -> obs)
      .subscribe(repoReadStream);
  
    writeStream.subscribe(bsPublisher.toSubscriber());
    
//    on end handler for setting timer for next read ...
//    repoReadStream.endHandler();
  
    Pump pump = Pump.pump(repoReadStream, writeStream);
    pump.start();
  }
  
  Boolean filterPromotedRepos(JsonObject repo) {
    String name = repo.getString("name");
    return !name.contains("Promote");
  }
  
  Boolean filterDisabledRepos(JsonObject repo) {
    return !repo.getBoolean("disabled");
  }
  
  Boolean filterRemoteRepos(JsonObject repo) {
    return repo.getString("type").equalsIgnoreCase("remote");
  }
  
}
