package com.test.repomigrator.handlers;

import io.reactivex.Flowable;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.ext.reactivestreams.ReactiveReadStream;
import io.vertx.ext.reactivestreams.ReactiveWriteStream;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.core.eventbus.MessageProducer;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RemoteRepoHandler implements Handler<io.vertx.reactivex.core.eventbus.Message<JsonObject>> {
  
  Vertx vertx = Vertx.vertx();
  
  @Override
  public void handle(Message<JsonObject> msg) {
  
    // System.out.println(msg.body().encodePrettily());
    
    List<JsonObject> repos = msg.body().getJsonArray("items")
      .stream()
      .map(entry -> new JsonObject(entry.toString()))
      .filter(repo -> !repo.getBoolean("disabled"))
      .filter(repo -> compareProtocol(repo))
//                            .map(this::printElements)
//                            .map(this::publishBrowsedStore)
      .collect(Collectors.toList());
  
    Flowable<JsonObject> repoFlow = Flowable.fromIterable(repos);
    ReactiveReadStream<JsonObject> repoReadStream = ReactiveReadStream.readStream();
    ReactiveWriteStream<JsonObject> writeStream = ReactiveWriteStream.writeStream(io.vertx.core.Vertx.vertx());
    MessageProducer<JsonObject> bsPublisher = vertx.eventBus().<JsonObject>publisher("browsed.stores");
  
  
    // TODO: 11/6/19   Connect it with /healthcheck from indy endpoint ...
    Flowable<Long> interval =
      Flowable.interval(500, TimeUnit.MILLISECONDS);
  
    Flowable
      .zip(repoFlow, interval,(obs,timer) -> obs)
      .subscribe(repoReadStream);
  
    writeStream.subscribe(bsPublisher.toSubscriber());
  
    Pump pump = Pump.pump(repoReadStream, writeStream);
    pump.start();
  }
  
  Boolean compareProtocol(JsonObject repo) {
    String protocol = repo.getString("url").split("//")[0];
    return !protocol.equalsIgnoreCase("https:");
  }
  
}
