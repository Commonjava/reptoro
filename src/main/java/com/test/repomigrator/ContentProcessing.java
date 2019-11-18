package com.test.repomigrator;

import com.test.repomigrator.services.IndyHttpClientService;
import io.reactivex.Flowable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

//import io.vertx.reactivex.core.AbstractVerticle;
//import io.vertx.reactivex.ext.web.client.WebClient;

public class ContentProcessing extends AbstractVerticle {
  
  Logger logger = Logger.getLogger(this.getClass().getName());
  
  public static List<JsonObject> contentList = new ArrayList<>();
  public static Flowable<JsonObject> contentFlow = Flowable.fromIterable(contentList);
  
  
  static final String HTTPS = "https";
  static final String INDY_MD5 = "INDY-MD5";
  static final String INDY_SHA1 = "INDY-SHA1";
  static final String INDY_ETAG = "ETAG";
  static final String INDY_VERSION_ETAG = "Indy-Min-API-Version";
  static final String X_CHECKSUM_MD5 = "X-CHECKSUM-MD5";
  static final String X_CHECKSUM_SHA1 = "X-CHECKSUM-SHA1";
  static final String ETAG = "ETAG";
  
  @Override
  public void start() throws Exception {
  
    vertx.eventBus().<JsonObject>consumer("content.url.processing" , this::handleContentComparing)
    .exceptionHandler(t -> {
        JsonObject errorData =
          new io.vertx.core.json.JsonObject()
            .put("source", getClass().getSimpleName().join(".", "content.error"))
            .put("cause", t.getMessage());
        vertx.eventBus().publish("error.processing", errorData );
      })
    ;
    
  }
  
//  private void handleContentComparing(io.vertx.reactivex.core.eventbus.Message<JsonObject> res) {
//    JsonObject msgBody = res.body();
//    if(msgBody.containsKey("compare") && msgBody.getBoolean("compare")) {
//      compareContentsAsync(msgBody);
//    }
//  }
  
  private void handleContentComparing(Message<JsonObject> res) {
    JsonObject msgBody = res.body();
    if(msgBody.containsKey("compare") && msgBody.getBoolean("compare")) {
      compareContents(msgBody);
      contentList.add(msgBody);
//      String[] sourcesUrl = msgBody.getString("sources").split("/");
//      logger.info("Comparing file: " + sourcesUrl[sourcesUrl.length-1]);
    } else {
      contentList.add(msgBody);
    }
  }
  
  

//  void compareContentsAsync(JsonObject urlListing) {
//    try {
//      URL listingUrl = new URL(urlListing.getString("sources"));
//      URL httpsListingUrl = new URL(HTTPS, listingUrl.getHost(), listingUrl.getPort() , listingUrl.getFile());
//      logger.info(httpsListingUrl.toString());
//      getClient()
////        .headAbs(httpsListingUrl.toString())
//        .head(443,httpsListingUrl.getHost(),httpsListingUrl.getPath())
//        .ssl(true)
//        .rxSend()
//        .subscribe(res -> {
//          if (res.statusCode() == 200) {
//            Iterator<Map.Entry<String, String>> iterator = res.headers().iterator();
//            boolean repoValidChange = false;
//            while (iterator.hasNext()) {
//              Map.Entry<String, String> headerTuple = iterator.next();
//
//              switch (headerTuple.getKey()) {
//                case X_CHECKSUM_MD5:
//                  if(urlListing.getString(INDY_MD5).equalsIgnoreCase(headerTuple.getValue())) {
//                    repoValidChange = true;
//                    break;
//                  }
//                case X_CHECKSUM_SHA1:
//                  if(urlListing.getString(INDY_SHA1).equalsIgnoreCase(headerTuple.getValue())){
//                    repoValidChange = true;
//                    break;
//                  }
//                case ETAG:
//                  if(urlListing.getString(INDY_ETAG).equalsIgnoreCase(headerTuple.getValue())) {
//                    repoValidChange = true;
//                    break;
//                  }
//                default:
//                  if(urlListing.getString(INDY_VERSION_ETAG).equalsIgnoreCase(res.headers().get(ETAG))) {
//                    repoValidChange = true;
//                    break;
//                  } else {
//                    repoValidChange = false;
//                  }
//                  break;
//              }
//              urlListing.put("validated", true);
//              if(repoValidChange) {
//                urlListing.put("validssl",true);
//                vertx.eventBus().publish("remote.repository.valid.change", urlListing);
//              }else {
//                urlListing.put("validssl", false);
//                vertx.eventBus().publish("remote.repository.not.valid.change", urlListing);
//              }
//            }
//          }
//        })
//      ;
//    }
//    catch (MalformedURLException mue) {
//      JsonObject errorData =
//        new JsonObject()
//          .put("source", getClass().getSimpleName().join(".", "malformed.url.error"))
//          .put("cause", mue.getMessage())
//          .put("obj", urlListing);
//      vertx.eventBus().publish("processing.errors",errorData);
//    }
//    catch (Exception t) {
//      JsonObject errorData =
//        new JsonObject()
//          .put("source", getClass().getSimpleName().join(".", "head.content.error"))
//          .put("cause", t.getMessage())
//          .put("obj", urlListing);
//      vertx.eventBus().publish("processing.errors",errorData);
//    }
//  }
  
  void compareContents(final JsonObject urlListing) {
  
    IndyHttpClientService indyHttpClientService =
      IndyHttpClientService.createProxy(vertx, RemoteRepositoryProcessing.INDY_HTTP_CLIENT_SERVICE);
    
    indyHttpClientService.getAndCompareSourceHeaders(urlListing, res -> {
      if(res.failed()) {
        logger.info("Something wrong with source HTTPS request! " + res.cause() );// FAILED TO OPEN [ SSL ERROR ] (server_certificate_pem)
      } else {
        JsonObject httpSourceHeaders = res.result();
  
        boolean headerKeyMatch =
          httpSourceHeaders.stream()
            .anyMatch(entries -> anyHeaderKeyMatch(entries, urlListing));
        
        if(headerKeyMatch) {
          boolean headerValueMatch =
            httpSourceHeaders.stream()
              .anyMatch(entry -> anyHeaderValueMatch(entry, urlListing));
          if(headerValueMatch) {
            urlListing.put("validated", true);
            vertx.eventBus().publish("remote.repository.valid.change", urlListing);
          } else {
            urlListing.put("validssl", false);
            vertx.eventBus().publish("remote.repository.not.valid.change", urlListing);
          }
        } else {
          // There is no source url header key which is matching to indy content url header key
          logger.info("No Header Key is matching! " + urlListing.getString("sources"));
        }
        
      }
    });
    
//    URL listingUrl = null;
//    try {
//      listingUrl = new URL(urlListing.getString("sources"));
//      URL httpsListingUrl = new URL(HTTPS, listingUrl.getHost(), listingUrl.getPort() , listingUrl.getFile());
//      logger.info("\n\n\n" + httpsListingUrl.toString() + "\n\n");
//      getClient()
//        .headAbs(httpsListingUrl.toString())
//        .ssl(true)
//        .send(res -> {
//          final JsonObject urlListingJson = urlListing;
//          if(res.succeeded()) {
//
//            HttpResponse<Buffer> result1 = res.result();
//            MultiMap headers = result1.headers();
//            boolean headerKeyMatch =
//              headers.entries().stream()
//              .anyMatch(entries -> anyHeaderKeyMatch(entries, urlListingJson));
//
//            if(headerKeyMatch) {
//              boolean headerValueMatch =
//                headers.entries().stream()
//                .anyMatch(entry -> anyHeaderValueMatch(entry, urlListingJson));
//              if(headerValueMatch) {
//                urlListingJson.put("validated", true);
//                vertx.eventBus().publish("remote.repository.valid.change", urlListingJson);
//              } else {
//                urlListingJson.put("validssl", false);
//                vertx.eventBus().publish("remote.repository.not.valid.change", urlListingJson);
//              }
//            } else {
//              // There is no source url header key which is matching to indy content url header key
//              logger.info("No Header Key is matching! " + urlListingJson.getString("sources"));
//            }

//            HttpResponse<Buffer> result = res.result();
//            Iterator<Map.Entry<String, String>> iterator = result.headers().iterator();
//            boolean repoValidChange = false;
//            while (iterator.hasNext()) {
//              Map.Entry<String, String> headerTuple = iterator.next();
//              String headerKey = headerTuple.getKey();
//              String headerValue = headerTuple.getValue();
//              logger.info("[[HEADERS]] " + headerKey + " : " + headerValue);
//              switch (headerKey.toUpperCase()) {
//                case X_CHECKSUM_MD5:
//                  logger.info("[[X_CHECKSUM_MD5]] COMPARING: " + urlListing.getJsonObject("headers").getString(INDY_ETAG) + " WITH " + headerTuple.getValue());
//                  if(urlListing.getJsonObject("headers").getString(INDY_MD5).equalsIgnoreCase(headerTuple.getValue())) {
//                    repoValidChange = true;
//                    break;
//                  }
//                case X_CHECKSUM_SHA1:
//                  if(urlListing.getJsonObject("headers").getString(INDY_SHA1).equalsIgnoreCase(headerTuple.getValue())){
//                    repoValidChange = true;
//                    break;
//                  }
//                case ETAG:
//                  logger.info("[[ETAG]] COMPARING: " + urlListing.getJsonObject("headers").getString(INDY_ETAG) + " WITH " + headerTuple.getValue());
//                  if(urlListing.getJsonObject("headers").getString(INDY_ETAG).equalsIgnoreCase(headerTuple.getValue())) {
//                    repoValidChange = true;
//                    break;
//                  }
//              }
//              urlListing.put("validated", true);
//              if(repoValidChange) {
//                urlListing.put("validssl",true);
//                vertx.eventBus().publish("remote.repository.valid.change", urlListing);
//              }else {
//                urlListing.put("validssl", false);
//                vertx.eventBus().publish("remote.repository.not.valid.change", urlListing);
//              }
//            }
//          }

//        });
//    }
//    catch (MalformedURLException t) {
//      JsonObject errorData =
//        new io.vertx.core.json.JsonObject()
//          .put("source", getClass().getSimpleName().join(".", "content.compare.error"))
//          .put("cause", t.getMessage());
//      vertx.eventBus().publish("error.processing", errorData );
//    }
//    catch (Exception e) {
//      JsonObject errorData =
//        new io.vertx.core.json.JsonObject()
//          .put("source", getClass().getSimpleName().join(".", "content.compare.error"))
//          .put("cause", e.getMessage());
//      vertx.eventBus().publish("error.processing", errorData );
//    }

  }
  
  private boolean anyHeaderValueMatch(Map.Entry<String, Object> entry, JsonObject urlListingJson) {
    return urlListingJson.getJsonObject("headers")
      .getMap().values().stream()
      .anyMatch(value -> value.toString().equalsIgnoreCase(String.valueOf(entry.getValue())));
  }
  
  WebClient getClient() {
     WebClientOptions webClientOptions =
       new WebClientOptions()
         .setKeepAlive(true)
         .setSsl(true)
         .setTrustAll(true)
//        .setPemKeyCertOptions()
       ;
//    return WebClient.create(vertx);
    return WebClient.create(vertx,webClientOptions);
  }
  
  private boolean anyHeaderKeyMatch(Map.Entry<String,Object> values,JsonObject urlListing) {
    return urlListing.getJsonObject("headers")
      .getMap().keySet().stream()
      .anyMatch(key -> key.equalsIgnoreCase(values.getKey()));
  }
  
}
