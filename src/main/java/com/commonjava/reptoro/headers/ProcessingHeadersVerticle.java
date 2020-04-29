package com.commonjava.reptoro.headers;

import com.commonjava.reptoro.common.Const;
import com.commonjava.reptoro.common.RepoStage;
import com.commonjava.reptoro.common.Topics;
import com.commonjava.reptoro.contents.Content;
import com.commonjava.reptoro.contents.ContentProcessingService;
import com.commonjava.reptoro.remoterepos.RemoteRepository;
import com.commonjava.reptoro.remoterepos.RemoteRepositoryService;
import io.vertx.cassandra.Mapper;
import io.vertx.cassandra.MappingManager;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.commonjava.reptoro.remoterepos.RemoteRepository.toJson;

public class ProcessingHeadersVerticle extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());

  private RemoteRepositoryService remoteRepositoryService;
  private ContentProcessingService contentProcessingService;
  private io.vertx.cassandra.CassandraClient cassandraClient;
  private Mapper<Content> contentMapper;
  private Mapper<RemoteRepository> repoMapper;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    DeliveryOptions options = new DeliveryOptions();
    options.setSendTimeout(TimeUnit.SECONDS.toMillis(60));
    this.remoteRepositoryService = RemoteRepositoryService.createProxy(vertx, "repo.service");
    this.contentProcessingService = ContentProcessingService.createProxyWithOptions(vertx, "content.service", options);
    this.cassandraClient = new com.commonjava.reptoro.common.CassandraClient(vertx, config()).getCassandraReptoroClientInstance();
    MappingManager mappingManagerContents = MappingManager.create(this.cassandraClient);
    this.contentMapper = mappingManagerContents.mapper(Content.class);
    MappingManager mappingManagerRepos = MappingManager.create(this.cassandraClient);
    this.repoMapper = mappingManagerRepos.mapper(RemoteRepository.class);
  }

  @Override
  public void stop() throws Exception {
    super.stop();
    cassandraClient.close(res -> {
      if (res.failed()) {
        logger.info("REPTORO CASSANDRA CLIENT FAILED TO CLOSE CONNECTION!");
      } else {
        logger.info("REPTORO CASSANDRA CLIENT SUCCESSFULY CLOSED CONNECTION!");
      }
    });
  }

  @Override
  public void start() throws Exception {

    vertx.eventBus().consumer(Topics.COMPARE_HEADERS, this::handleCompareHeaders);

  }

  private void handleCompareHeaders(Message<JsonObject> tMessage) {
    JsonObject repo = tMessage.body();
    logger.info("=============< HEADERS COMPARING >====================\n\t\t\tREPO: " + repo.getString("key"));

    // publish to client:
    vertx.eventBus().publish(Topics.CLIENT_TOPIC,new JsonObject().put("msg", "COMPARE HEADERS: " + repo ));

    getContentsWithHeadersFromDb(repo)
      .compose(this::compareLocalAndSourceHeaders)
      .onComplete(this::handleCompleteCompareHeaders)
    ;

  }

  private Future<JsonObject> getContentsWithHeadersFromDb(JsonObject repo) {
    Promise<JsonObject> promise = Promise.promise();
    contentProcessingService.getContentsFromDb(repo, res -> {
      if (res.succeeded()) {
        JsonObject contentsRepoKey = res.result();
        logger.info("[[COMPARE_HEADERS]] " + contentsRepoKey.getJsonArray("data").size() + " CONTENTS");
        promise.complete(contentsRepoKey);
      } else {
        promise.complete();
      }
    });
    return promise.future();
  }

  private Future<JsonObject> compareLocalAndSourceHeaders(JsonObject contentsAndRepo) {
    Promise<JsonObject> promise = Promise.promise();
    if (Objects.nonNull(contentsAndRepo)) {
      JsonObject repo = contentsAndRepo.getJsonObject("repo");
      JsonArray contents = contentsAndRepo.getJsonArray("data");
      logger.info("[[COMPARE>HEADERS>SIZE]] " + contents.size());
      if (!contents.isEmpty()) {
        CompositeFuture.join(
          contents.stream()
            .map(content -> new JsonObject(content.toString()))
            .filter(content -> content.containsKey("sourceheaders") && content.containsKey("localheaders"))
            .filter(content -> !content.getString("localheaders").equalsIgnoreCase("{}"))
            .filter(content -> !content.getString("sourceheaders").equalsIgnoreCase("{}"))
//                            .filter(content -> !content.getString("checksum").equalsIgnoreCase("false"))
//                            .filter(content -> !content.getString("checksum").equalsIgnoreCase("true"))
            .filter(content -> content.getString("checksum").equalsIgnoreCase(""))
            .map(content -> compareContentHashes(content))
            .map(content -> contentInDb(content).compose(this::updateContent)
//              .setHandler(this::handleContentCompare)
            )
            .collect(Collectors.toList())

        ).onComplete(res -> {
          if(res.failed()) {
            logger.info("COMPARING HEADERS FAILED: " + res.cause());
          } else {
            logger.info("CONTENT SIZE AFTER HEADERS COMPARE: " + res.result().size());
            promise.complete(contentsAndRepo);
          }
        });
      } else {
        // TODO empty contents list - send this repo for change protocol???
        logger.info("COMPARE HEADERS CONTENTS /EMPTY!");
        promise.complete();
      }
    } else {
      logger.info("REPO CONTENTS - NULL");
      promise.complete();
    }
    return promise.future();
  }

  private JsonObject compareContentHashes(JsonObject content) {

    String localheaders1 = content.containsKey(Const.LOCALHEADERS) ? content.getString(Const.LOCALHEADERS) : "";
    String sourceheaders1 = content.containsKey(Const.SOURCEHEADERS) ? content.getString(Const.SOURCEHEADERS) : "";

    if(localheaders1.equalsIgnoreCase("") || sourceheaders1.equalsIgnoreCase("")) {
      return new JsonObject();
    }

    JsonObject localheaders = new JsonObject(localheaders1);
    JsonObject sourceheaders = new JsonObject(sourceheaders1);

    String repoKey = content.getString("filesystem");

//      logger.info("COMPARING: \n" + localheaders.encodePrettily() + "\nWITH\n" + sourceheaders.encodePrettily());

//    logger.info("CONTENT: " + content.encodePrettily());

    String localMd5 = localheaders.containsKey("INDY-MD5") ? localheaders.getString("INDY-MD5") : "";
    String localSha1 = localheaders.containsKey("INDY-SHA1") ? localheaders.getString("INDY-SHA1") : "";

    // TODO if there is no indy-md5 or sha1 or sha256 checksums then recreate them...

    String sourceMd5 = "";
    String sourceSha1 = "";
    String sourceEtag = "";

    boolean compareMd5 = false;
    boolean compareSha1 = false;
    boolean compareEtag = false;

//    logger.info("PROCESSING HEADERS...");
    Set<String> fieldNames
      = sourceheaders.fieldNames();

      for (String fieldName : fieldNames) {
        if (fieldName.toLowerCase().contains("md5")) {
          sourceMd5 = sourceheaders.getString(fieldName);
        } else if (fieldName.toLowerCase().contains("sha1")) {
          sourceSha1 = sourceheaders.getString(fieldName);
        } else if (fieldName.toLowerCase().contains("etag")) {
          sourceEtag = sourceheaders.getString(fieldName);
        }
      }

//    logger.info("STARTING CHECKSUM COMPARE...");

    if (Objects.nonNull(sourceMd5) && !sourceMd5.isEmpty()) {
      compareMd5 = localMd5.equalsIgnoreCase(sourceMd5) || sourceMd5.contains(localMd5);
      content.put("checksum", Boolean.toString(compareMd5)); // String.valueOf(compareMd5));
      return content;
    } else if (Objects.nonNull(sourceSha1) && !sourceSha1.isEmpty()) {
      compareSha1 = localSha1.equalsIgnoreCase(sourceSha1) || sourceSha1.contains(localSha1);
      content.put("checksum", Boolean.toString(compareSha1)); // String.valueOf(compareSha1));
      return content;
    } else {
      if (!sourceEtag.isEmpty()) {
        // \"ETag\":\"\\\"{SHA1{c49dc5a288f0bc5598375e68583d47c6bfbfe2cc}}\\\"\"

        if(sourceEtag.toLowerCase().contains("md5")) {
          // compare localMd5 with inner md5 checksum
          if(sourceEtag.contains(localMd5)) {
            content.put("checksum","true");
            return content;
          } else {
            content.put("checksum","false");
            return content;
          }
        } else if(sourceEtag.toLowerCase().contains("sha1")) {
          // compare localSha1 with inner sha1 checksum
          if(sourceEtag.contains(localSha1)) {
            content.put("checksum","true");
            return content;
          } else {
            content.put("checksum","false");
            return content;
          }
        } else {
          // can be sha256??? but for now return false
          if(sourceEtag.toLowerCase().contains("sha256")) {
            logger.info("SHA256 ETAG: " + content.getString("filename"));
            content.put("checksum","false");
            return content;
          } else {
            content.put("checksum","false");
            return content;
          }
        }

      } else {
//        logger.info("EMPTY ETAG: " + sourceheaders.getString("ETAG"));
        compareEtag = false;
        content.put("checksum", Boolean.toString(compareEtag)); // String.valueOf(compareEtag));
        return content;
      }
    }
  }

  private Future<JsonObject> contentInDb(JsonObject contentWithLocalAndSourceHeaders) {
    Promise<JsonObject> promise = Promise.promise();

    if (Objects.isNull(contentWithLocalAndSourceHeaders.getString("parentpath")) ||
      Objects.isNull(contentWithLocalAndSourceHeaders.getString("filename")) ||
      Objects.isNull(contentWithLocalAndSourceHeaders.getString("filesystem"))
    ) {
      logger.info(contentWithLocalAndSourceHeaders.encodePrettily());
      promise.complete();
    } else {
      List<Object> objects =
        Arrays.asList(contentWithLocalAndSourceHeaders.getString("parentpath"),
          contentWithLocalAndSourceHeaders.getString("filename"),
          contentWithLocalAndSourceHeaders.getString("filesystem")
        );
      contentMapper.get(objects, res -> {
        if (res.succeeded()) {
//              logger.info("CHECKSUM: " + contentWithLocalAndSourceHeaders.getString("checksum"));
          promise.complete(contentWithLocalAndSourceHeaders);
        } else {
          logger.info("\t\t\t\tGET CONTENT FROM DB AT COMPARING CHECKSUMS FAILED: " + res.cause());
          promise.complete();
        }
      });
    }

    return promise.future();
  }

  private Future<JsonObject> updateContent(JsonObject contentWithLocalAndSourceHeaders) {
    Promise<JsonObject> promise = Promise.promise();
    if (Objects.nonNull(contentWithLocalAndSourceHeaders)) {

//          logger.info(contentWithLocalAndSourceHeaders.encodePrettily());
          Content content = Content.fromJson(contentWithLocalAndSourceHeaders);

          if(content.getFilesystem().equalsIgnoreCase("maven:remote:central")) {
            vertx.eventBus().send(Topics.SAVE_HEADERS, contentWithLocalAndSourceHeaders);
            promise.complete(contentWithLocalAndSourceHeaders);
          } else {
            contentMapper.save(content, update -> {
              if(update.succeeded()) {
//                  logger.info("\t\t\t\tUPDATED CONTENT COMPARE AT COMPARING CHECKSUMS: " + contentWithLocalAndSourceHeaders.getString("checksum"));
                promise.complete(contentWithLocalAndSourceHeaders);
              } else {
                logger.info("\t\t\t\tUPDATE CONTENT COMPARE AT COMPARING CHECKSUMS FAILED: " + update.cause());
                promise.complete();
              }
            });
          }
    } else {
      logger.info("CONTENT COMPARE GET FAILURE /NULL");
    }
    return promise.future();
  }

  private void handleContentCompare(AsyncResult<JsonObject> asyncResult) {
    if (asyncResult.failed()) {
      logger.info("============< CONTENT COMPARE HEADERS FAILED: " + asyncResult.cause() + " >=============");
    } else {
      if (Objects.nonNull(asyncResult.result())) {
        logger.info("============< CONTENT COMPARE HEADER SUCCEEDED >=============");
      } else {
        logger.info("CONTENT COMPARE HEADERS FAILED /NULL");
      }
    }
  }

  private void handleCompleteCompareHeaders(AsyncResult<JsonObject> objectAsyncResult) {
    if (objectAsyncResult.succeeded()) {
      if (Objects.nonNull(objectAsyncResult.result())) {
        JsonObject repoAndContents = objectAsyncResult.result();
        logger.info("==< HEADERS COMPARE COMPLETE / REPO: " + repoAndContents.getJsonObject("repo").getString("key") + " >====================");
        logger.info("====> CHANGING STAGE <====");
        changeRepositoryStateToFinish(repoAndContents.getJsonObject("repo").getString("key"))
          .setHandler(ar -> {
            if (ar.succeeded()) {
              logger.info("STAGE CHANGED FOR REPO: \n" + ar.result().encodePrettily() + "\n NEXT REPO...");
              vertx.setTimer(TimeUnit.SECONDS.toMillis(20), timer -> {
                vertx.eventBus().send(Topics.REPO_GET_ONE, new JsonObject().put("type", "maven"));
              });
            } else {
              logger.info("REPO STAGE UPDATE FAILED: " + ar.cause());
            }
          });
      } else {
        logger.info("HEADERS COMPARE FAILURE /NULL");
      }
    } else {
      logger.info(" FAILED FUTURE IN COMPARING HEADERS: " + objectAsyncResult.cause());
    }
  }

  private Future<JsonObject> changeRepositoryStateToFinish(String repoKey) {
    Promise<JsonObject> promise = Promise.promise();
    repoMapper.get(Collections.singletonList(repoKey), res -> {
      if (res.succeeded()) {
        RemoteRepository repo = res.result();
        repo.setStage(RepoStage.FINISH);
        repo.setCompared(true);
        repoMapper.save(repo, save -> {
          if (save.succeeded()) {
            promise.complete(toJson(repo));
          } else {
            promise.complete();
          }
        });
      }
    });
    return promise.future();
  }
}
