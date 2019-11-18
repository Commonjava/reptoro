package com.test.repomigrator.verticles;

import com.test.repomigrator.RemoteRepositoryProcessing;
import com.test.repomigrator.services.IndyHttpClientService;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.Message;

import java.time.Instant;
import java.util.function.Function;
import java.util.logging.Logger;

public class ListingProcessingVerticle extends AbstractVerticle {
  
  Logger logger = Logger.getLogger(this.getClass().getName());
  
  CircuitBreaker circuitBreaker;
  
  
  @Override
  public void start() throws Exception {
  
    CircuitBreakerOptions circuitBreakerOptions =
      new CircuitBreakerOptions()
        .setMaxRetries(2)
        .setMaxFailures(2)
        .setTimeout(6000)
        .setFallbackOnFailure(true)
        .setResetTimeout(10000)
        .setNotificationPeriod(10000)
      ;
    circuitBreaker = CircuitBreaker.create("repomigrator-cb", vertx.getDelegate(), circuitBreakerOptions);
    circuitBreaker.openHandler(v -> logger.info("\n\n\n\t\t[ Indy Endpoint circuit breaker is open @ " + Instant.now() + " ]\n\n\n"));
    circuitBreaker.closeHandler(v -> logger.info("\n\n\n\t\t[ Indy Endpoint circuit breaker is closed " + Instant.now() + " ]\n\n\n"));
    circuitBreaker.halfOpenHandler(v -> logger.info("\n\n\n\t\t[ Indy Endpoint circuit breaker is half open " + Instant.now() + " ]\n\n\n"));
    circuitBreaker.retryPolicy(retryCount -> retryCount * 100L);
  
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
  
  
  void handleListingUrlProcessing(Message<JsonObject> res) {
    final JsonObject listingUrlsJson = res.body();
    final String listingUrl = listingUrlsJson.getString("listingUrl");
    
    IndyHttpClientService indyHttpClientService = IndyHttpClientService.createProxy(vertx.getDelegate(), RemoteRepositoryProcessing.INDY_HTTP_CLIENT_SERVICE);
    
    if(!listingUrl.endsWith("/")) {
      // it's a File
      if(!listingUrl.endsWith("md5") && !listingUrl.endsWith("sha1") && !listingUrl.endsWith("txt")) {
        
        Handler<Promise<JsonObject>> processor = future -> {
          indyHttpClientService.getAndCompareHeadersAsync(listingUrlsJson , ar -> {
//            indyHttpClientService.getAndCompareHeadersSync(listingUrlsJson , ar -> {
            if(ar.failed()) {
              logger.info("Failed Fetching Headers! " + ar.cause() + " RESULT: " + ar.result() );
              future.fail(ar.cause());
            } else {
              future.complete(ar.result());
            }
          });
//          getAsyncHeadersAndCompare(listingUrlsJson,future); // *************************************
        };
        
        Function<Throwable, JsonObject> fallback = future -> {
//          vertx.eventBus().publish("pumping.browsed.stores",new JsonObject().put("action", "stop"));
          
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
            }
//              else {
//              logger.info(result.encodePrettily()); // Circuit Breaker Open Circuit Messages...
//            }
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
        indyHttpClientService.getContentAsync(listingUrlsJson , ar -> {
//          indyHttpClientService.getContentSync(listingUrlsJson , ar -> {
          if(ar.failed()) {
            logger.info("Processing Browsed Store Path Failed! " + ar.cause());
            future.fail(ar.cause());
          } else {
            future.complete(ar.result());
          }
        });
//        getAsyncContent(listingUrlsJson,future);// ********************************
      };
      
      Function<Throwable, JsonObject> fallback = future -> {
        return new JsonObject()
          .put("state", circuitBreaker.state())
          .put("msg", future.getMessage())
          .put("time", Instant.now())
          ;
      };
      
      circuitBreaker.executeWithFallback(processor,fallback).setHandler(ar -> {
        if(ar.succeeded()) {
          if(ar.result() != null && !ar.result().containsKey("state")) {
            vertx.eventBus().publish("listings.urls", ar.result());
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
    }
  }
  
  
}
