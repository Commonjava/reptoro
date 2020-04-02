package com.commonjava.reptoro.contents;


import com.commonjava.reptoro.common.RepoStage;
import com.commonjava.reptoro.common.Topics;
import com.commonjava.reptoro.remoterepos.RemoteRepository;
import com.commonjava.reptoro.remoterepos.RemoteRepositoryService;
import io.vertx.cassandra.Mapper;
import io.vertx.cassandra.MappingManager;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.commonjava.reptoro.contents.Content.toJson;
import static com.commonjava.reptoro.remoterepos.RemoteRepository.toJson;

public class ProcessingContentVerticle extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());

  private JsonObject config;
  private RemoteRepositoryService remoteRepositoryService;
  private ContentProcessingService contentProcessingService;

  private io.vertx.cassandra.CassandraClient cassandraClient;
  private Mapper<Content> contentMapper;
  private Mapper<RemoteRepository> repoMapper;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    DeliveryOptions options = new DeliveryOptions();
    options.setSendTimeout(TimeUnit.SECONDS.toMillis(120));
    this.remoteRepositoryService = RemoteRepositoryService.createProxy(vertx, "repo.service");
    this.contentProcessingService = ContentProcessingService.createProxyWithOptions(vertx, "content.service", options);
    this.config = vertx.getOrCreateContext().config();

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

    EventBus eventBus = vertx.eventBus();

    eventBus.consumer(Topics.CONTENT_HEADERS, this::handleContentHeaders);


  }

  private void handleContentHeaders(Message<JsonObject> tMessage) {
    JsonObject contentsObj = tMessage.body();
    JsonArray contents = new JsonArray();
    if (contentsObj.containsKey("contents")) {
      contents.addAll(contentsObj.getJsonArray("contents"));
    } else {
      contents.addAll(contentsObj.getJsonArray("data"));
    }
//        JsonArray contents = contentsObj.getJsonArray("contents");
    logger.info("> PROCESSING HEADERS / CONTENTS SIZE: " + contents.size() + "\n\t\tFOR REPO: " + contentsObj.getString("key"));

    if (!contents.isEmpty()) {
      CompositeFuture.join(
        contents
          .stream()
          .map(repoObj -> new JsonObject(repoObj.toString()))
          .filter(this::isExceptedFilenameExtension)
          .filter(this::isAllowedContentExtensions)
          //                    .peek(this::peekContents)
          .map(content -> fetchLocalHeaders(content)
              .compose(this::fetchSourceHeaders)
              .compose(this::checkContentInDb)
              .compose(this::updateContentWithHeader)
//                                            .setHandler(this::handleHeaders)
          )
          .collect(Collectors.toList())
      ).onComplete(res -> {

        if (res.succeeded()) {
          CompositeFuture result = res.result();
          int size = result.size();

          logger.info("GETTING ALL HEADERS SUCCEEDED. SIZE: " + size);

          changeRepositoryStateToHeaders(contentsObj.getString("key"))
            .setHandler(ar -> {
              if (ar.succeeded()) {
                logger.info("REPO STAGE CHANGED: " + ar.result().encodePrettily() + "\n NEXT STAGE...");
                vertx.setTimer(TimeUnit.SECONDS.toMillis(30), timer -> {
                  vertx.eventBus().send(Topics.COMPARE_HEADERS, ar.result());
                });
              } else {
                logger.info("REPO STAGE UPDATE FAILED: " + ar.cause());
              }
            });

        } else {
          logger.info("GETTING ALL HEADERS FAILED." );
          // TODO Send this repo to waiting or post-processing queue???

          // for now we will just change repo stage to this repo in problematic and start process next one...

          changeRepositoryStateToPromlematic(contentsObj.getString("key"))
            .onComplete(update -> {
              if (update.succeeded()) {
                logger.info("REPO STAGE CHANGED: " + update.result().encodePrettily() + "\n NEXT REPO...");
                vertx.setTimer(TimeUnit.SECONDS.toMillis(5), action -> {
                  vertx.eventBus().send(Topics.REPO_GET_ONE, new JsonObject().put("type", "maven"));
                });
              } else {
                logger.info("PROBLEM CHANGING REPO STAGE: " + update.result().encodePrettily() + "\nFIX? >>>");
              }
            });

        }
      });

    } else {
      logger.info("==> EMPTY CONTENTS FROM REPO: " + contentsObj.getString("key") + "\nCHANGE REPO STAGE...");
      changeRepositoryStateToFinish(contentsObj.getString("key"))
        .onComplete(res -> {
          if (res.succeeded()) {
            // send it for protocol change
            // TODO create change protocol verticle...
            if (Objects.nonNull(res.result())) {
              vertx.eventBus().send(Topics.CHANGE_PROTOCOL, new JsonObject().put("repokey", contentsObj.getString("key")));
              // send msg for next repo "REPO_GET_ONE"
              vertx.eventBus().send(Topics.REPO_GET_ONE, new JsonObject().put("type", "maven"));


            } else {
              logger.info("--- CHANGE REPO STAGE TO FINISH ON EMPTY REPO CONTENTS FAILED /NULL");
            }
          } else {
            logger.info("--- CHANGE REPO STAGE TO FINISH ON EMPTY REPO CONTENTS FAILED: " + res.cause());
          }
        });

    }
  }

  private void handleUpdateHeaders(AsyncResult<JsonObject> asyncResult) {
    if (asyncResult.succeeded()) {
      logger.info("CONTENT WITH HEADERS UPDATED | REPO: " + asyncResult.result().getString("filesystem"));
    } else {
      logger.info("CONTENT WITH HEADERS FAILED | CAUSE: " + asyncResult.cause());
    }
  }

  private void handleHeaders(AsyncResult<JsonObject> asyncResult) {
    if (asyncResult.succeeded()) {
      if (Objects.nonNull(asyncResult.result())) {
//                logger.info("HEADERS FOR REPO: " + asyncResult.result().getString("filesystem") + "\nSTORAGE: " + asyncResult.result().getString("filestorage"));
      } else {
        logger.info("HEADERS FAILED /NULL");
      }
    } else {
      logger.info("HEADERS FAILED: " + asyncResult.cause());
    }
  }

  private void peekContents(JsonObject entries) {
    logger.info(entries.encodePrettily());
  }

  private Future<JsonObject> fetchLocalHeaders(JsonObject content) {
    Promise<JsonObject> promise = Promise.promise();

    contentProcessingService.getLocalHeadersSync(content, res -> {
      if (res.succeeded()) {
        JsonObject contentWithLocalHeaders = res.result();
        promise.complete(contentWithLocalHeaders);
      } else {
        logger.info("FETCHING LOCAL HEADERS FAILED: " + res.cause());
        promise.complete();
      }
    });
    return promise.future();
  }

  private Future<JsonObject> fetchSourceHeaders(JsonObject content) {
    Promise<JsonObject> promise = Promise.promise();
    if (Objects.nonNull(content)) {
      contentProcessingService.getSourceHeadersSync(content.getString("source"), content, res -> {
        if (res.succeeded()) {
          JsonObject contentWithLocalAndSourceHeaders = res.result();
            promise.complete(contentWithLocalAndSourceHeaders);
        } else {
          logger.info("FETCHING SOURCE HEADERS FAILED: " + res.cause());
          promise.complete();
        }
      });
    } else {
//            logger.info("LOCAL HEADERS FAILED /NULL");
      promise.complete();
    }
    return promise.future();
  }

  private Future<JsonObject> checkContentInDb(JsonObject contentWithLocalAndSourceHeaders) {
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
          promise.complete(contentWithLocalAndSourceHeaders);
        } else {
          logger.info("CONTENT IS NOT IN DB, REPO: " + contentWithLocalAndSourceHeaders.getString("filesystem") +
            "\nCONTENT" + contentWithLocalAndSourceHeaders.getString("filename") +
            "\nFILESTORAGE: " + contentWithLocalAndSourceHeaders.getString("filestorage"));
          promise.complete();
        }
      });
    }
    return promise.future();
  }

  private Future<JsonObject> updateContentWithHeader(JsonObject contentWithLocalAndSourceHeaders) {
    Promise<JsonObject> promise = Promise.promise();
    if (Objects.nonNull(contentWithLocalAndSourceHeaders)) {
//            contentMapper.save(new Content(contentWithLocalAndSourceHeaders), update -> {
//                if(update.succeeded()) {
//                    promise.complete(contentWithLocalAndSourceHeaders);
//                } else {
//                    logger.info("UPDATE CONTENT FAILED: " + update.cause());
//                    promise.complete();
//                }
//            });
      vertx.eventBus().send(Topics.SAVE_HEADERS, contentWithLocalAndSourceHeaders);
      promise.complete(contentWithLocalAndSourceHeaders);
    } else {
//      logger.info("CONTENT GET FAILURE /NULL");
      promise.complete();
    }
    return promise.future();
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

  private Future<JsonObject> changeRepositoryStateToPromlematic(String repoKey) {
    Promise<JsonObject> promise = Promise.promise();
    repoMapper.get(Collections.singletonList(repoKey), res -> {
      if (res.succeeded()) {
        RemoteRepository repo = res.result();
        repo.setStage(RepoStage.PROBLEMATIC);
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

  // [".zip",".jar",".tar",".txt",".war",".ear",".xml"]
  private Boolean isAllowedContentExtensions(JsonObject cont) {
    String filename = cont.getString("filename");
    JsonArray allowedExtensions = this.config.getJsonObject("reptoro").getJsonArray("allowed.file.extensions");
    String extension = "";
    int i = filename.lastIndexOf('.');
    if (i > 0) {
      extension = filename.substring(i + 1);
    }
    // example:  pom.[xml] -> true
    return allowedExtensions.contains(extension);
  }

  // ["maven-metadata.xml", ".sha1", ".md5", ".asc",".listing.txt"]
  private Boolean isExceptedFilenameExtension(JsonObject cont) {
    JsonArray exceptedFileExtensions = this.config.getJsonObject("reptoro").getJsonArray("except.filename.extensions");
    List<String> exceptedFileExtList =
      exceptedFileExtensions.stream()
      .map(fileExtension -> String.valueOf(fileExtension))
      .collect(Collectors.toList());
    String filename = cont.getString("filename");
    for (String ext : exceptedFileExtList) {
      if(ext.equalsIgnoreCase(filename) || filename.endsWith(ext)) {
        return false;
      }
//      return String.valueOf(ext).equalsIgnoreCase(filename) || filename.endsWith(String.valueOf(ext));
//      example: "maven-metadata.xml" -> false |  "primername.sha1" -> false
    }
    return true;
  }

  private Future<JsonObject> changeRepositoryStateToHeaders(String repoKey) {
    Promise<JsonObject> promise = Promise.promise();
    repoMapper.get(Collections.singletonList(repoKey), res -> {
      if (res.succeeded()) {
        RemoteRepository repo = res.result();
        repo.setStage(RepoStage.HEADERS);
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
