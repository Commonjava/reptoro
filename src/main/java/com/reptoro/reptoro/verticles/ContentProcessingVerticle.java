package com.reptoro.reptoro.verticles;

import com.reptoro.reptoro.common.ChecksumCompare;
import com.reptoro.reptoro.services.HttpClientService;
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

public class ContentProcessingVerticle extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());
  HttpClientService proxy;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    proxy = HttpClientService.createProxy(vertx,"indy.http.client.service");
  }

  @Override
  public void start() throws Exception {

    EventBus eventBus = vertx.eventBus();

    eventBus.consumer("content.processing.headers",this::fetchLocalContentHeaders);

    eventBus.consumer("content.source.headers", this::fetchSourceContentHeaders);

    eventBus.consumer("content.compare.headers" , this::compareLocalAndSourceHeaders);

    eventBus.consumer("content.comparing.result" , this::comparingResultProcessing);

  }


  private void fetchLocalContentHeaders(Message<JsonObject> repositoryMsg) {

    JsonObject repo = repositoryMsg.body();
    String sourceUrl = repo.getString("url");
    JsonObject browsedStore = repo.getJsonObject("browsedStore");
    JsonArray content = browsedStore.getJsonArray("content");

    for (int i = 0; i < content.size() ; i++) {
//    for(JsonObject cont : contentList) {
      JsonObject cont = content.getJsonObject(i);
      if(cont.getString("filename").endsWith(".jar") || cont.getString("filename").endsWith(".pom")
        || cont.getString("filename").endsWith(".tar") || cont.getString("filename").endsWith(".zip")
        || cont.getString("filename").endsWith(".txt") || cont.getString("filename").endsWith(".war")
        || cont.getString("filename").endsWith(".ear")
      ) {
        proxy.getLocalContentHeadersSync(cont, res -> {
          if(res.succeeded()) {
            cont.put("compare",true);
            cont.put("timestamp.lh" , Instant.now());
            cont.put("local.headers",res.result());
            cont.put("source",sourceUrl);
            vertx.eventBus().send("content.source.headers",cont); // send to just one processing verticle
          } else {
            logger.info("Content Headers Fetching Failed!");
            logger.info("CAUSE: " + res.cause());
          }
        });
      }
    }

  }

  private void fetchSourceContentHeaders(Message<JsonObject> browsedStoreMsg) {
    JsonObject browsedStore = browsedStoreMsg.body();

    proxy.getRemoteContentHeadersSync(browsedStore , res -> {
      if(res.succeeded()) {
        browsedStore.put("timestamp.sh" , Instant.now());
        browsedStore.put("source.headers" , res.result());

        vertx.eventBus().send("content.compare.headers" , browsedStore);
      } else {
        logger.info("Source Headers Fetching Failed!");
        logger.info("CAUSE: " + res.cause());
      }
    });

  }

  private void compareLocalAndSourceHeaders(Message<JsonObject> browsedStoreMsg) {
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
        vertx.eventBus().send("content.comparing.result" , browsedStore);

      } else {
        // not equal headers - send event to eventbus for not equal headers ...
        browsedStore.put("content.checksum.equal" , false);
        vertx.eventBus().send("content.comparing.result" , browsedStore);
      }

    } else if(Objects.nonNull(sourceContentHeaders.getString("ETag"))) {
      String eTag = sourceContentHeaders.getString("ETAG");
      String localMd5 = localContentHeaders.getString("INDY-MD5");
      String localSha1 = localContentHeaders.getString("INDY-SHA1");

      if(eTag.equalsIgnoreCase(localMd5) || eTag.equalsIgnoreCase(localSha1)) {

        browsedStore.put("content.equal" , true);
        vertx.eventBus().send("content.comparing.result" , browsedStore);

      } else {
        // not equal headers - send event to eventbus for not equal headers ...
        browsedStore.put("content.equal" , false);
        vertx.eventBus().send("content.comparing.result" , browsedStore);
      }

    } else {
       // there is no MD5 or SHA1 or ETag Headers
      browsedStore.put("content.equal" , false);
      vertx.eventBus().send("content.comparing.result" , browsedStore);
    }


  }

  private void comparingResultProcessing(Message<JsonObject> browsedStoreMsg) {
    JsonObject browsedStore = browsedStoreMsg.body();
    Boolean compareResult = browsedStore.getBoolean("content.checksum.equal");
    browsedStore.put("timestamp.finish" , Instant.now());

    String repoKey = browsedStore.getString("filesystem");

    if(ChecksumCompare.checksumCompareResults.containsKey(repoKey)) {
      ChecksumCompare.checksumCompareResults.get(repoKey).add(browsedStore);
    }else {
      ChecksumCompare.checksumCompareResults.put(repoKey,new JsonArray().add(browsedStore));
    }


    ChecksumCompare.checksumCompareResultsFlowable
      .subscribe((nxt) -> {
        logger.info("Result Map size: " + ChecksumCompare.checksumCompareResults.size() );
        logger.info("Result Array size: " + ChecksumCompare.checksumCompareResults.get("maven:remote:central").size());
        logger.info("=============================================================================");
      });

//    if(compareResult) {
//      logger.info("\n\n\n === COMPARING RESULTS - TRUE === \n");
//      logger.info(browsedStore.encodePrettily());
//      logger.info("=============================================");
//    } else {
//      logger.info("\n\n\n === COMPARING RESULTS - FALSE === \n");
//      logger.info(browsedStore.encodePrettily());
//      logger.info("=============================================");
//    }


  }
}
