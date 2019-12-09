package com.test.repomigrator;

import com.test.repomigrator.services.IndyHttpClientService;
import io.reactivex.Flowable;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
//import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.reactivestreams.ReactiveReadStream;
import io.vertx.ext.reactivestreams.ReactiveWriteStream;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.core.eventbus.MessageProducer;
//import io.vertx.reactivex.core.streams.Pump;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

public class ListingUrlProcessing extends AbstractVerticle {
  
  Logger logger = Logger.getLogger(this.getClass().getName());
  
//  CircuitBreaker circuitBreaker;
  
  @Override
  public void start() throws Exception {
  
//    HealthChecks healthChecks = HealthChecks.create(vertx.getDelegate());
//
//    CircuitBreakerOptions circuitBreakerOptions =
//      new CircuitBreakerOptions()
//        .setMaxRetries(2)
//        .setMaxFailures(2)
//        .setTimeout(6000)
//        .setFallbackOnFailure(true)
//        .setResetTimeout(10000)
//        .setNotificationPeriod(10000)
//      ;
//    circuitBreaker = CircuitBreaker.create("repomigrator-cb", vertx.getDelegate(), circuitBreakerOptions);
//    circuitBreaker.openHandler(v -> logger.info("\n\n\n\t\t[ Indy Endpoint circuit breaker is open @ " + Instant.now() + " ]\n\n\n"));
//    circuitBreaker.closeHandler(v -> logger.info("\n\n\n\t\t[ Indy Endpoint circuit breaker is closed " + Instant.now() + " ]\n\n\n"));
//    circuitBreaker.halfOpenHandler(v -> logger.info("\n\n\n\t\t[ Indy Endpoint circuit breaker is half open " + Instant.now() + " ]\n\n\n"));
//    circuitBreaker.retryPolicy(retryCount -> retryCount * 100L);
//
//    healthChecks.register("indy-test-health", 2*60000, future -> {
//      if(circuitBreaker.state().equals(CircuitBreakerState.CLOSED)) {
//        future.complete(Status.OK());
//      } else {
//        future.complete(Status.KO());
//      }
//    });
    
    vertx.eventBus().consumer("listings.urls",this::handleListingUrls)
      .exceptionHandler(t -> {
        JsonObject errorData =
          new JsonObject()
            .put("source", getClass().getSimpleName().join(".", "parse.lu.error"))
            .put("cause", t.getMessage());
        vertx.eventBus().publish("processing.errors",errorData);
      })
    ;
    
    
//    vertx.eventBus().consumer("listing.url.processing", this::handleListingUrlProcessing)
//      .exceptionHandler(t -> {
//        JsonObject errorData =
//          new JsonObject()
//            .put("source", getClass().getSimpleName().join(".", "parse.lu.error"))
//            .put("cause", t.getMessage());
//        vertx.eventBus().publish("processing.errors",errorData);
//      })
//    ;
  
  }
  
  JsonObject transformListingUrls(JsonObject listing,JsonObject bs) {
    // logger.info(Json.encodePrettily(listing));
    return new JsonObject()
      .put("path", listing.getString("path"))
      .put("listingUrl", listing.getString("listingUrl"))
      .put("sources", listing.getJsonArray("sources").getValue(0))
      .put("time", Instant.now())
      .put("browsedStore", bs.getString("storeKey"))
      .put("cert", bs.getString("cert"))
      ;
  }
  
  void handleListingUrls(io.vertx.reactivex.core.eventbus.Message<JsonObject> res) {
    
      JsonObject browsedStore = res.body();
      
//      vertx.eventBus().publish("pump.command", new JsonObject().put("cmd", "stop"));
      
      if (browsedStore != null && browsedStore.getJsonArray("listingUrls") != null && !browsedStore.getJsonArray("listingUrls").isEmpty()) {
        JsonArray listingUrlsJsonArr = browsedStore.getJsonArray("listingUrls");
        
        Flowable<JsonObject> listingUrlsFlowable = Flowable.fromIterable(listingUrlsJsonArr.getList());
        ReactiveReadStream<JsonObject> readStream = ReactiveReadStream.readStream();
        MessageProducer<JsonObject> publisher = vertx.eventBus().<JsonObject>publisher("listing.url.processing");
        ReactiveWriteStream<JsonObject> writeStream = ReactiveWriteStream.writeStream(vertx.getDelegate());
        
        listingUrlsFlowable
          .map(lu -> transformListingUrls(lu, browsedStore))
          .subscribe(readStream);
  
        Flowable<Long> interval =
          Flowable.interval(200, TimeUnit.MILLISECONDS);
  
        Flowable
          .zip(listingUrlsFlowable, interval,(obs,timer) -> obs)
          .subscribe(readStream);
  
        writeStream.subscribe(publisher.toSubscriber());
      
        Pump pump = Pump.pump(readStream, writeStream);
        pump.start();
        
        
      }
  }
  
//  void handleListingUrlProcessing(Message<JsonObject> res) {
//    final JsonObject listingUrlsJson = res.body();
//    final String listingUrl = listingUrlsJson.getString("listingUrl");
//
//    IndyHttpClientService indyHttpClientService = IndyHttpClientService.createProxy(vertx.getDelegate(), RemoteRepositoryProcessing.INDY_HTTP_CLIENT_SERVICE);
//
//    if(!listingUrl.endsWith("/")) {
//      // it's a File
//      if(!listingUrl.endsWith("md5") && !listingUrl.endsWith("sha1") && !listingUrl.endsWith("txt")) {
//
//        Handler<Promise<JsonObject>> processor = future -> {
//          indyHttpClientService.getAndCompareHeadersAsync(listingUrlsJson , ar -> {
//            Promise<JsonObject> promise = Promise.promise();
//            if(ar.failed()) {
//              logger.info("Failed Fetching Headers! " + ar.cause() );
////              future.fail(ar.cause());
//              future.fail(ar.cause());
//            } else {
//              future.complete(ar.result());
//            }
//          });
////          getAsyncHeadersAndCompare(listingUrlsJson,future); // *************************************
//        };
//
//        Function<Throwable, JsonObject> fallback = future -> {
////          vertx.eventBus().publish("pumping.browsed.stores",new JsonObject().put("action", "stop"));
//
//          return new JsonObject()
//            .put("state", circuitBreaker.state())
//            .put("msg", future.getMessage())
//            .put("time", Instant.now())
//            ;
//        };
//
//        circuitBreaker.executeWithFallback(processor, fallback).setHandler(ar -> {
//          if(ar.succeeded()) {
//            JsonObject result = ar.result();
//            if(result != null && !result.containsKey("state")) {
//              vertx.eventBus().publish("content.url.processing", result);
//            }
////              else {
////              logger.info(result.encodePrettily()); // Circuit Breaker Open Circuit Messages...
////            }
//          }
//          else {
//            Throwable cause = ar.cause();
//            JsonObject errorData =
//              new JsonObject()
//                .put("source", getClass().getSimpleName().join(".", "cb.get.200"))
//                .put("cause", cause)
//                .put("body", ar.result());
//            vertx.eventBus().publish("processing.errors", errorData);
//          }
//        });
//      } else {
//        listingUrlsJson.put("compare", false);
//        vertx.eventBus().publish("content.url.processing", listingUrlsJson);
//      }
//    }
//    else {
//      // it's a path directory...
//
//      Handler<Promise<JsonObject>> processor = future -> {
//        indyHttpClientService.getContentAsync(listingUrlsJson , ar -> {
//          Promise<JsonObject> promise = Promise.<JsonObject>promise();
//          if(ar.failed()) {
//            logger.info("Processing Browsed Store Path Failed! " + ar.cause());
//            future.fail(ar.cause());
//          } else {
//            future.complete(ar.result());
//          }
//        });
////        getAsyncContent(listingUrlsJson,future);// ********************************
//      };
//
//      Function<Throwable, JsonObject> fallback = future -> {
//        return new JsonObject()
//          .put("state", circuitBreaker.state())
//          .put("msg", future.getMessage())
//          .put("time", Instant.now())
//          ;
//      };
//
//      circuitBreaker.executeWithFallback(processor,fallback).setHandler(ar -> {
//        if(ar.succeeded()) {
//          if(ar.result() != null && !ar.result().containsKey("state")) {
//            vertx.eventBus().publish("listings.urls", ar.result());
//          }
//        }
//        else {
//          Throwable cause = ar.cause();
//          JsonObject errorData =
//            new JsonObject()
//              .put("source", getClass().getSimpleName().join(".", "cb.get.200"))
//              .put("cause", cause)
//              .put("body", ar.result());
//          vertx.eventBus().publish("processing.errors", errorData);
//        }
//      });
//    }
//  }
}
