package com.test.repomigrator;

import com.test.repomigrator.services.IndyHttpClientService;
import io.reactivex.Flowable;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.ext.reactivestreams.ReactiveReadStream;
import io.vertx.ext.reactivestreams.ReactiveWriteStream;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.MessageProducer;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class RemoteRepositoryProcessing extends AbstractVerticle {
  
  public static final String INDY_HTTP_CLIENT_SERVICE = "indy.http.client.service";
  Logger logger = Logger.getLogger(this.getClass().getName());
  IndyHttpClientService indyHttpClientService;
  
  @Override
  public void start() throws Exception {
    
    indyHttpClientService = IndyHttpClientService.createProxy(vertx.getDelegate(), INDY_HTTP_CLIENT_SERVICE);
    processRemoteRepositories(indyHttpClientService);
  }
  
  private void processRemoteRepositories(IndyHttpClientService indyHttpClientService) {
    indyHttpClientService.getAllRemoteRepositories("maven", res -> {
      if(res.failed()) {
        System.out.println("Fetch RemoteRepositories Failed! Msg:  " + res.cause());
      } else {
        handleRemoteRepositories(res.result());
      }
    });
  }
  
  
  Boolean compareProtocol(JsonObject repo) {
    String protocol = repo.getString("url").split("//")[0];
    return !protocol.equalsIgnoreCase("https:");
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
  
  void handleRemoteRepositories(JsonObject res) {
    //JsonObject body = res.body();
    
    JsonArray remoteRepos = res.getJsonArray("items");
    
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
    ReactiveWriteStream<JsonObject> writeStream = ReactiveWriteStream.writeStream(vertx.getDelegate());
    MessageProducer<JsonObject> bsPublisher = vertx.eventBus().<JsonObject>publisher("browsed.stores");
    
    
    // Change http protocol on start and then process the content
    // and then after all the content files are done then change http protocol of remote repo
  
  
    Flowable<Long> interval =
      Flowable.interval(20, TimeUnit.SECONDS);
  
    Flowable<JsonObject> zipFlow = Flowable.zip(repoFlow, interval, (obs, timer) -> obs);
    
    zipFlow.subscribe(ar -> {
         vertx.eventBus().<JsonObject>request("browsed.stores", ar , handler -> {
           if(handler.failed()) {
             System.out.println("FAIL! cause: " + handler.cause());
           } else {
             System.out.println("Msg from Consumer: " + handler.result().body());
           }
         });
      })
    ;
    
    
    
    
    /**
    Flowable<Long> interval =
      Flowable.interval(20, TimeUnit.SECONDS);
    
    Flowable
      .zip(repoFlow, interval,(obs,timer) -> obs)
      .subscribe(repoReadStream);
    
    writeStream.subscribe(bsPublisher.toSubscriber());
    
    Pump pump = Pump.pump(repoReadStream, writeStream);
    pump.start();
    
    repoReadStream.endHandler(hndl -> {
      pump.stop();
      logger.info("\t\t\t ==========| END |==========");
      logger.info("\n\n\t\t\t ==========| STARTING in 1 Hour |==========\n\n\n");
      vertx.setTimer(6*3600000 , ar -> {
        if(indyHttpClientService != null) {
          processRemoteRepositories(indyHttpClientService);
        }
      });
    });
     */
  }
}
