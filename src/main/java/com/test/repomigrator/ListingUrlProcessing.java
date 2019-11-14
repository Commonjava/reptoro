package com.test.repomigrator;

import io.reactivex.Flowable;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.reactivestreams.ReactiveReadStream;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;

import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ListingUrlProcessing extends AbstractVerticle {
  
  Logger logger = Logger.getLogger(this.getClass().getName());
  
  String indyRemoteRepoUrl;
  String authToken;
  String user;
  String pass;
  
  CircuitBreaker circuitBreaker;
  
  @Override
  public void start() throws Exception {
//    logger.log(Level.INFO,"[[START]] {0}" , this.getClass().getName());
  
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
  
    HealthChecks healthChecks = HealthChecks.create(vertx);
    
    CircuitBreakerOptions circuitBreakerOptions =
      new CircuitBreakerOptions()
        .setMaxRetries(2)
        .setMaxFailures(2)
        .setTimeout(6000)
        .setFallbackOnFailure(true)
        .setResetTimeout(10000)
        .setNotificationPeriod(10000)
      ;
    circuitBreaker = CircuitBreaker.create("repomigrator-cb", vertx, circuitBreakerOptions);
    circuitBreaker.openHandler(v -> logger.info("\n\n\n\t\t[ Indy Endpoint circuit breaker is open @ " + Instant.now() + " ]\n\n\n"));
    circuitBreaker.closeHandler(v -> {
      logger.info("\n\n\n\t\t[ Indy Endpoint circuit breaker is closed " + Instant.now() + " ]\n\n\n");
      vertx.eventBus().publish("pumping.browsed.stores",
        new JsonObject().put("action", "start"));
    });
    circuitBreaker.halfOpenHandler(v -> logger.info("\n\n\n\t\t[ Indy Endpoint circuit breaker is half open " + Instant.now() + " ]\n\n\n"));
    circuitBreaker.retryPolicy(retryCount -> retryCount * 100L);
    
    healthChecks.register("indy-test-health", 1000, future -> {
      if(circuitBreaker.state().equals(CircuitBreakerState.CLOSED)) {
        future.complete(Status.OK());
      } else {
        future.complete(Status.KO());
      }
    });
    
    vertx.eventBus().consumer("listings.urls",this::handleListingUrls)
      .exceptionHandler(t -> {
        JsonObject errorData =
          new JsonObject()
            .put("source", getClass().getSimpleName().join(".", "parse.lu.error"))
            .put("cause", t.getMessage());
        vertx.eventBus().publish("processing.errors",errorData);
      })
    ;
    
    
    vertx.eventBus().consumer("listing.url.processing", this::handleListingUrlProcessing)
      .exceptionHandler(t -> {
        JsonObject errorData =
          new JsonObject()
            .put("source", getClass().getSimpleName().join(".", "parse.lu.error"))
            .put("cause", t.getMessage());
        vertx.eventBus().publish("processing.errors",errorData);
      })
    ;
    
  }
  
  JsonObject transformListingUrls(JsonObject listing,JsonObject bs) {
    // logger.info(Json.encodePrettily(listing));
    return new JsonObject()
      .put("path", listing.getString("path"))
      .put("listingUrl", listing.getString("listingUrl"))
      .put("sources", listing.getJsonArray("sources").getValue(0))
      .put("time", Instant.now())
      .put("browsedStore", bs.getString("storeKey"))
//      .put("cert", listing.getString("cert"))
      ;
  }
  
  JsonObject toJson(Object object , String pem) {
    return new JsonObject(object.toString()).put("cert", pem);
  }
  
  void getAsyncContent(JsonObject urlListing, Promise<JsonObject> future) {
    getClient()
      .getAbs(urlListing.getString("listingUrl"))
      .basicAuthentication(user, pass)
      .as(BodyCodec.jsonObject())
      .rxSend()
      .subscribe((res) -> {
          if (res.statusCode() == 200) {
            try {
              JsonObject browsedStore = res.body();
              
              if (browsedStore.getJsonArray("listingUrls") != null && !browsedStore.getJsonArray("listingUrls").isEmpty()) {
                
                future.complete(browsedStore);
              }
            }
            catch (Exception e) {
              JsonObject errorData =
                new JsonObject()
                  .put("source", getClass().getSimpleName().join(".", "get.200"))
                  .put("cause", e.getMessage())
                  .put("body", res.bodyAsString());
              vertx.eventBus().publish("processing.errors", errorData);
              future.fail(e.getMessage());
            }
          }
          else {
            JsonObject errorData =
              new JsonObject()
                .put("source", getClass().getSimpleName().join(".", "get.200>"))
                .put("cause", res.statusCode())
                .put("body", res.bodyAsString());
            vertx.eventBus().publish("processing.errors", errorData);
            future.fail(String.valueOf(res.statusCode()));
          }
        },
        (t) -> {
          JsonObject errorData =
            new JsonObject()
              .put("source", getClass().getSimpleName().join(".", "get.lu.error"))
              .put("cause", t.getMessage())
              .put("obj", urlListing);
          vertx.eventBus().publish("processing.errors",errorData);
          future.fail(t.getMessage());
        })
    ;
  }
  
  void getAsyncHeadersAndCompare(JsonObject urlListing, Promise<JsonObject> future) {
    getClient()
      .headAbs(urlListing.getString("listingUrl"))
      .basicAuthentication(user, pass)
      .rxSend()
      .subscribe(ar -> {
        if(ar.statusCode()==200) {
          Iterator<Map.Entry<String, String>> headers = ar.headers().iterator();
          JsonObject headersJson = new JsonObject();
          while(headers.hasNext()) {
            Map.Entry<String, String> headerTuple = headers.next();
            headersJson.put(headerTuple.getKey(), headerTuple.getValue());
          }
          urlListing.put("headers", headersJson);
          urlListing.put("compare", true);
          // logger.log(Level.INFO, "[[CONTENT]] {0}", urlListing.encodePrettily());
//          vertx.eventBus().publish("content.url.processing", urlListing);
          future.complete(urlListing);
          
        } else {
          future.fail(String.valueOf(ar.statusCode()));
        }
      },
        t -> {
        JsonObject errorData =
          new JsonObject()
            .put("source", getClass().getSimpleName().join(".", "head.lu.error"))
            .put("cause", t.getMessage())
            .put("obj", urlListing);
        vertx.eventBus().publish("processing.errors",errorData);
          future.fail(t.getMessage());
      });
  }
  
  WebClient getClient() {
    WebClientOptions webClientOptions = new WebClientOptions().setKeepAlive(true);
    return WebClient.create(Vertx.newInstance(vertx), webClientOptions);
  }
  
  void handleListingUrls(Message<JsonObject> res) {
    
    try {
      JsonObject browsedStore = res.body();
      JsonArray listingUrlsJsonArr = browsedStore.getJsonArray("listingUrls");
      final String pemCert;
      if(browsedStore.containsKey("cert")) {
        pemCert = browsedStore.getString("cert");
      } else {
        pemCert = "";
      }
      List<JsonObject> listingUrls =
        listingUrlsJsonArr
          .stream()
          .map(el -> toJson(el,pemCert))
          .collect(Collectors.toList());
    
      if (browsedStore != null && listingUrlsJsonArr != null && !listingUrlsJsonArr.isEmpty()) {
        
        Flowable<JsonObject> listingUrlsFlowable = Flowable.fromIterable(listingUrls);
        ReactiveReadStream<JsonObject> readStream = ReactiveReadStream.readStream();
        MessageProducer<JsonObject> publisher = vertx.eventBus().<JsonObject>publisher("listing.url.processing");
        listingUrlsFlowable
          .map(lu -> transformListingUrls(lu, browsedStore))
          .subscribe(readStream);
      
        Pump pump = Pump.pump(readStream, publisher);
        pump.start();
      
      
      }
    } catch (Exception t) {
      JsonObject errorData =
        new JsonObject()
          .put("source", getClass().getSimpleName().join(".", "parse.lu.error"))
          .put("cause", t.getMessage());
      vertx.eventBus().publish("processing.errors",errorData);
    }
  }
  
  void handleListingUrlProcessing(Message<JsonObject> res) {
    JsonObject listingUrlsJson = res.body();
    String listingUrl = listingUrlsJson.getString("listingUrl");
    if(!listingUrl.endsWith("/")) {
      // it's a File
      if(!listingUrl.endsWith("md5") || !listingUrl.endsWith("sha1") || !listingUrl.endsWith("txt")
      ) {
      
        Handler<Promise<JsonObject>> processor = future -> {
          getAsyncHeadersAndCompare(listingUrlsJson,future);
        };
      
      
        Function<Throwable, JsonObject> fallback = future -> {
          vertx.eventBus().publish("pumping.browsed.stores",
            new JsonObject().put("action", "stop"));
          
          return new JsonObject()
            .put("state", circuitBreaker.state())
            .put("msg", future.getMessage())
            .put("time", Instant.now())
            ;
        };
      
        circuitBreaker.executeWithFallback(processor, fallback).setHandler(ar -> {
          if(ar.succeeded()) {
            JsonObject result = ar.result();
            if(result != null && !result.containsKey("state")) {
              vertx.eventBus().publish("content.url.processing", result);
              logger.info(result.encodePrettily());
            } else {
              logger.info(result.encodePrettily());
//              logger.info("CIRCUIT STATE: " + result.getString("state") + " MESSAGE: " + result.getString("msg"));
            }
          }
          else {
            Throwable cause = ar.cause();
            JsonObject errorData =
              new JsonObject()
                .put("source", getClass().getSimpleName().join(".", "cb.get.200"))
                .put("cause", cause)
                .put("body", ar.result());
            vertx.eventBus().publish("processing.errors", errorData);
          }
        });
      } else {
        listingUrlsJson.put("compare", false);
        vertx.eventBus().publish("content.url.processing", listingUrlsJson);
      }
    }
    else {
      // it's a path directory...
    
      Handler<Promise<JsonObject>> processor = future -> {
        getAsyncContent(listingUrlsJson,future);
      };
    
      Function<Throwable, JsonObject> fallback = future -> {
        return new JsonObject().put("state", circuitBreaker.state())
          .put("msg", future.getMessage())
          .put("time", Instant.now())
          ;
      };
    
      circuitBreaker.executeWithFallback(processor,fallback).setHandler(ar -> {
        if(ar.succeeded()) {
          vertx.eventBus().publish("listings.urls", ar.result());
        }
        else {
          Throwable cause = ar.cause();
          JsonObject errorData =
            new JsonObject()
              .put("source", getClass().getSimpleName().join(".", "cb.get.200"))
              .put("cause", cause)
              .put("body", ar.result());
          vertx.eventBus().publish("processing.errors", errorData);
        }
      });
    }
  }
}
