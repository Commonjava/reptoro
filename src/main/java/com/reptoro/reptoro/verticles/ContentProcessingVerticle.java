package com.reptoro.reptoro.verticles;

import com.reptoro.reptoro.common.CassandraWriteClient;
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
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static com.reptoro.reptoro.common.ChecksumCompare.*;
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
  JsonObject config;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    this.proxy = HttpClientService.createProxy(vertx, INDY_HTTP_CLIENT_PROXY_SERVICE);
    this.config = vertx.getOrCreateContext().config();
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

    if(!content.isEmpty()) {
      for (int i = 0; i < content.size(); i++) {
        JsonObject cont = content.getJsonObject(i);
        logger.info("[[CHECKING.FILE.EXTENSION]] " + cont.getString("filename"));
        if (!isExceptedFilenameExtension(cont) || allowedContentExtensions(cont)) {

          proxy.getLocalContentHeadersSync(cont, res -> {
            if (res.succeeded()) {
              cont.put("compare", true);
              cont.put("timestamp.lh", Instant.now());
              cont.put("local.headers", res.result());
              cont.put("source", sourceUrl);

              // TODO Add localheaders result json to shareddata object

              vertx.eventBus().send(ReptoroTopics.CONTENT_SOURCE_HEADERS, cont); // send to just one processing verticle
            } else {
              logger.info("[[LOCAL.HEADERS.FAILED]]\n");
              logger.info("CAUSE: " + res.cause());
              logger.info("=====================================");
            }
          });
        } else {
          logger.info("[[FILENAME.EXCLUDED]] " + cont.getString("filename") + " [[REPO.KEY]] " + repo.getString("key"));
        }
      }
    } else {
      logger.info("[[CONTENT.EMPTY]] " + browsedStore.getString("storeKey"));
    }
  }

  private void handleSourceContentHeaders(Message<JsonObject> browsedStoreMsg) {
    JsonObject browsedStore = browsedStoreMsg.body();

    proxy.getRemoteContentHeadersSync(browsedStore , res -> {
      if(res.succeeded()) {
        browsedStore.put("timestamp.sh" , Instant.now());
        browsedStore.put("source.headers" , res.result());

        // TODO Add Source Headers to shareddata object

        vertx.eventBus().send(ReptoroTopics.CONTENT_COMPARE_HEADERS , browsedStore); // send it to just one processing verticle...
      } else {
        logger.info("[[SOURCE.HEADERS.FAILED]]");
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
      String eTag = sourceContentHeaders.getString("ETag");
      String localMd5 = Objects.nonNull(localContentHeaders.getString("INDY-MD5")) ? localContentHeaders.getString("INDY-MD5") : "";
      String localSha1 = Objects.nonNull(localContentHeaders.getString("INDY-SHA1")) ? localContentHeaders.getString("INDY-SHA1") : "";

      if((!localMd5.isEmpty() || !localSha1.isEmpty()) && (eTag.equalsIgnoreCase(localMd5) || eTag.equalsIgnoreCase(localSha1))) {

        browsedStore.put("content.checksum.equal", true);
        vertx.eventBus().send(ReptoroTopics.CONTENT_COMPARE_RESULT, browsedStore);

      } else if(localMd5.isEmpty() || localSha1.isEmpty()) {
        logger.info("[[CHECKSUMS.MISSING]]\n");
        logger.info(sourceContentHeaders.encodePrettily());
        logger.info("======================================");
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


    // TODO Add Comparing result to shareddata object

    //**********************************
    // get all filtered remote repositories from shared data object and put this processing result in result key in repo object
//    SharedData sharedData = vertx.sharedData();
//    sharedData.getLocalAsyncMap("remote.repositories" , res -> {
//        if (res.succeeded()) {
//          AsyncMap<Object, Object> localDataRepos = res.result();
//          localDataRepos.get("repos", ar -> {
//            if (ar.succeeded()) {
//              // TODO PUT THEM TO CASSANDRA DB
//              logger.info("[[SHAREDDATA]] \n" + ar.result().toString());
//              logger.info("============================================================");
//
//            }
//          });
//        }
//      });
    //**********************************

//            JsonArray reposArr = (JsonArray) ar.result();
//            List<JsonObject> reposList = reposArr.getList();

//            for(JsonObject repo : reposList) {
//              if(repo.containsKey(repoKey)) {
//                repo.put("result" , browsedStore);
//                checksumCompareResults.put(repoKey , repo);
//                new CassandraWriteClient(vertx)
//                  .connect(
//                    config().getString("indy.db.cassandra.hostname"),
//                    config().getInteger("indy.db.cassandra.port"),
//                    config().getString("indy.db.cassandra.user"),
//                    config().getString("indy.db.cassandra.pass"),
//                    config().getString("") // datacentar?
//                  )
//                  .createKeySpace(
//                    config().getString("indy.db.cassandra.write.keyspace"),
//                    config().getInteger("indy.db.cassandra.port")
//                  )
//                  .useKeyspace(
//                    config().getString("indy.db.cassandra.write.keyspace")
//                  )
//                  .createReptoroTable(
//                    config().getString("indy.db.cassandra.write.tablename"),
//                    "remoterepo",
//                    "browsedstore",
//                    "contents",
//                    "timestamp"
//                  )
//                  .insertReptoroRecord(
//                    config().getString("indy.db.cassandra.write.tablename"),
//                    repo.toString() ,
//                    repo.getJsonObject("result").toString(),
//                    repo.getJsonObject("result").getJsonObject("content").toString()
//                  )
//                  .close()
//                  ;


//                logger.info(repo.encodePrettily());
//              }
//            }
//          }
//        });
//
//      }
//    });
  }

  // [".pom",".zip",".jar",".tar",".txt",".war",".ear"]
  Boolean allowedContentExtensions(JsonObject cont) {
    return cont.getString("filename").endsWith(".jar") || cont.getString("filename").endsWith(".pom")
      || cont.getString("filename").endsWith(".tar") || cont.getString("filename").endsWith(".zip")
      || cont.getString("filename").endsWith(".txt") || cont.getString("filename").endsWith(".war")
      || cont.getString("filename").endsWith(".ear");
  }

  // ["maven-metadata.xml", ".sha1", ".md5", ".asc"]
  Boolean isExceptedFilenameExtension(JsonObject cont) {
    JsonArray excteptedFileExtensions = this.config.getJsonArray("indy.except.filename.extensions");
    String filename = cont.getString("filename");
    for(Object ext : excteptedFileExtensions) {
      if(String.valueOf(ext).equalsIgnoreCase(filename) || filename.endsWith(String.valueOf(ext))) {
        return true;
      }
    }
    return false;
  }
}
