package com.reptoro.reptoro.verticles;

import com.reptoro.reptoro.common.ChecksumCompare;
import com.reptoro.reptoro.common.ReptoroTopics;
import com.reptoro.reptoro.services.HttpClientService;
import com.sun.org.apache.xpath.internal.operations.Bool;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.Objects;
import java.util.logging.Logger;

import static com.reptoro.reptoro.verticles.BrowsedStoreVerticle.INDY_HTTP_CLIENT_PROXY_SERVICE;

/**
 *
 * @author gorgigeorgievski
 */

// Worker Verticle - Executing Severall Http calls for fetching local content headers ,
// fetching source repository content headers , comparing both local and source headers checksums and
// handling of comparing result
public class ContentProcessingVerticle extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());
  HttpClientService proxy;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    proxy = HttpClientService.createProxy(vertx, INDY_HTTP_CLIENT_PROXY_SERVICE);
  }

  @Override
  public void start() throws Exception {

    EventBus eventBus = vertx.eventBus();

    eventBus.consumer(ReptoroTopics.CONTENT_PROCESSING_HEADERS,this::handleLocalContentHeaders);

    eventBus.consumer(ReptoroTopics.CONTENT_SOURCE_HEADERS, this::handleSourceContentHeaders);

    eventBus.consumer(ReptoroTopics.CONTENT_COMPARE_HEADERS , this::handleComparingLocalAndSourceHeaders);

    eventBus.consumer(ReptoroTopics.CONTENT_COMPARE_RESULT , this::handleResultProcessing);
  }

  private void handleLocalContentHeaders(Message<JsonObject> repositoryMsg) {

    JsonObject repo = repositoryMsg.body();
    String sourceUrl = repo.getString("url");
    JsonObject browsedStore = repo.getJsonObject("browsedStore");
    JsonArray content = browsedStore.getJsonArray("content");

    for (int i = 0; i < content.size() ; i++) {
      JsonObject cont = content.getJsonObject(i);
      if(allowedContentExtensions(cont)) {

        proxy.getLocalContentHeadersSync(cont, res -> {
          if(res.succeeded()) {
            cont.put("compare",true);
            cont.put("timestamp.lh" , Instant.now());
            cont.put("local.headers",res.result());
            cont.put("source",sourceUrl);

            vertx.eventBus().send(ReptoroTopics.CONTENT_SOURCE_HEADERS,cont); // send to just one processing verticle
          } else {
            logger.info("Content Headers Fetching Failed!");
            logger.info("CAUSE: " + res.cause());
          }
        });
      } else {
        logger.info("=> Content: " + cont.getString("filename") + " excluded!");
      }
    }

  }

  private void handleSourceContentHeaders(Message<JsonObject> browsedStoreMsg) {
    JsonObject browsedStore = browsedStoreMsg.body();

    proxy.getRemoteContentHeadersSync(browsedStore , res -> {
      if(res.succeeded()) {
        browsedStore.put("timestamp.sh" , Instant.now());
        browsedStore.put("source.headers" , res.result());

        vertx.eventBus().send(ReptoroTopics.CONTENT_COMPARE_HEADERS , browsedStore);
      } else {
        logger.info("Source Headers Fetching Failed!");
        logger.info("CAUSE: " + res.cause());
      }
    });

  }

  private void handleComparingLocalAndSourceHeaders(Message<JsonObject> browsedStoreMsg) {
    JsonObject browsedStore = browsedStoreMsg.body();
    JsonObject localContentHeaders = browsedStore.getJsonObject("local.headers");
    JsonObject sourceContentHeaders = browsedStore.getJsonObject("source.headers");

    if(Objects.nonNull(sourceContentHeaders.getString("X-Checksum-MD5"))
      || Objects.nonNull(sourceContentHeaders.getString("X-Checksum-SHA1"))) {

      String md5 = sourceContentHeaders.getString("X-Checksum-MD5");
      String sha1 = sourceContentHeaders.getString("X-Checksum-SHA1");
      String localMd5 = localContentHeaders.getString("INDY-MD5");
      String localSha1 = localContentHeaders.getString("INDY-SHA1");

      if(md5.equalsIgnoreCase(localMd5) || md5.equalsIgnoreCase(localSha1)
        || sha1.equalsIgnoreCase(localMd5) || sha1.equalsIgnoreCase(localSha1)) {

        browsedStore.put("content.checksum.equal" , true);
        vertx.eventBus().send(ReptoroTopics.CONTENT_COMPARE_RESULT , browsedStore);

      } else {
        // not equal headers - send event to eventbus for not equal headers ...
        browsedStore.put("content.checksum.equal" , false);
        vertx.eventBus().send(ReptoroTopics.CONTENT_COMPARE_RESULT , browsedStore);
      }

    } else if(Objects.nonNull(sourceContentHeaders.getString("ETag"))) {
      String eTag = sourceContentHeaders.getString("ETAG");
      String localMd5 = localContentHeaders.getString("INDY-MD5");
      String localSha1 = localContentHeaders.getString("INDY-SHA1");

      if(eTag.equalsIgnoreCase(localMd5) || eTag.equalsIgnoreCase(localSha1)) {

        browsedStore.put("content.checksum.equal" , true);
        vertx.eventBus().send(ReptoroTopics.CONTENT_COMPARE_RESULT , browsedStore);

      } else {
        // not equal headers - send event to eventbus for not equal headers ...
        browsedStore.put("content.checksum.equal" , false);
        vertx.eventBus().send(ReptoroTopics.CONTENT_COMPARE_RESULT , browsedStore);
      }

    } else {
       // there is no MD5 or SHA1 or ETag Headers
      browsedStore.put("content.checksum.equal" , false);
      vertx.eventBus().send(ReptoroTopics.CONTENT_COMPARE_RESULT , browsedStore);
    }
  }

  private void handleResultProcessing(Message<JsonObject> browsedStoreMsg) {
    JsonObject browsedStore = browsedStoreMsg.body();
    Boolean compareResult = browsedStore.getBoolean("content.checksum.equal");
    browsedStore.put("timestamp.finish" , Instant.now());

    String repoKey = browsedStore.getString("filesystem");

    if(ChecksumCompare.checksumCompareResults.containsKey(repoKey)) {
      ChecksumCompare.checksumCompareResults.get(repoKey).add(browsedStore);
    }else {
      ChecksumCompare.checksumCompareResults.put(repoKey,new JsonArray().add(browsedStore));
    }

    logger.info(browsedStore.encodePrettily());
  }

  Boolean allowedContentExtensions(JsonObject cont) {
    return cont.getString("filename").endsWith(".jar") || cont.getString("filename").endsWith(".pom")
      || cont.getString("filename").endsWith(".tar") || cont.getString("filename").endsWith(".zip")
      || cont.getString("filename").endsWith(".txt") || cont.getString("filename").endsWith(".war")
      || cont.getString("filename").endsWith(".ear");
  }
}
