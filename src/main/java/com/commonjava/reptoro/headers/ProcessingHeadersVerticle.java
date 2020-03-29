package com.commonjava.reptoro.headers;

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
        this.remoteRepositoryService = RemoteRepositoryService.createProxy(vertx , "repo.service");
        this.contentProcessingService = ContentProcessingService.createProxyWithOptions(vertx,"content.service" ,options );
        this.cassandraClient = new com.commonjava.reptoro.common.CassandraClient(vertx,config()).getCassandraReptoroClientInstance();
        MappingManager mappingManagerContents = MappingManager.create(this.cassandraClient);
        this.contentMapper = mappingManagerContents.mapper(Content.class);
        MappingManager mappingManagerRepos = MappingManager.create(this.cassandraClient);
        this.repoMapper = mappingManagerRepos.mapper(RemoteRepository.class);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        cassandraClient.close(res -> {
            if(res.failed()) {
                logger.info("REPTORO CASSANDRA CLIENT FAILED TO CLOSE CONNECTION!");
            } else {
                logger.info("REPTORO CASSANDRA CLIENT SUCCESSFULY CLOSED CONNECTION!");
            }
        });
    }

    @Override
    public void start() throws Exception {

        vertx.eventBus().consumer(Topics.COMPARE_HEADERS , this::handleCompareHeaders);

    }

    private void handleCompareHeaders(Message<JsonObject> tMessage) {
        JsonObject repo = tMessage.body();
        logger.info("=============< HEADERS COMPARING >====================\n\t\t\tREPO: " + repo.getString("key"));

        getContentsWithHeadersFromDb(repo)
                .compose(this::compareLocalAndSourceHeaders)
                .onComplete(this::handleCompleteCompareHeaders)
        ;

    }

    private Future<JsonObject> getContentsWithHeadersFromDb(JsonObject repo) {
        Promise<JsonObject> promise = Promise.promise();
        contentProcessingService.getContentsFromDb(repo , res -> {
            if(res.succeeded()) {
                JsonObject contentsAndRepo = res.result();
                logger.info("[[COMPARE_HEADERS]] " + contentsAndRepo.getJsonArray("data").size() + " CONTENTS");
                promise.complete(contentsAndRepo);
            } else {
                promise.complete();
            }
        });
        return promise.future();
    }

    private Future<JsonObject> compareLocalAndSourceHeaders(JsonObject contentsAndRepo) {
        Promise<JsonObject> promise = Promise.promise();
        if(Objects.nonNull(contentsAndRepo)) {
            JsonObject repo = contentsAndRepo.getJsonObject("repo");
            JsonArray contents = contentsAndRepo.getJsonArray("data");
            if(!contents.isEmpty()) {
                CompositeFuture.join(
                    contents.stream()
                            .map(content -> new JsonObject(content.toString()))
                            .filter(content -> !content.containsKey("sourceheaders") || !content.containsKey("localheaders"))
                            .filter(content -> !content.getString("checksum").equalsIgnoreCase("false") || !content.getString("checksum").equalsIgnoreCase("true"))
                            .map(content -> compareContentHashes(content))
                            .map(content -> contentInDb(content).compose(this::updateContent).onComplete(this::handleContentCompare))
                            .collect(Collectors.toList())

                ).onComplete(res -> {
                   if(res.succeeded()) {
                       promise.complete(contentsAndRepo);
                   } else {
                       promise.complete();
                   }
                });
            } else {
                // TODO empty contents list - send this repo for change protocol???
                logger.info("COMPARE HEADERS CONTENTS/NULL!");
            }
        } else {
            logger.info("REPO CONTENTS - NULL");
            promise.complete();
        }
        return promise.future();
    }

    private JsonObject compareContentHashes(JsonObject content) {

        JsonObject localheaders = content.getJsonObject("localheaders");
        JsonObject sourceheaders = content.getJsonObject("sourceheaders");

        String localMd5 = localheaders.getString("INDY-MD5");
        String localSha1 = localheaders.getString("INDY-SHA1");

        String sourceMd5 = "";
        String sourceSha1 = "";
        String sourceEtag = "";

        boolean compareMd5 = false;
        boolean compareSha1 = false;
        boolean compareEtag = false;

        Set<String> fieldNames = sourceheaders.fieldNames();
        for(String fieldName : fieldNames) {
            if(fieldName.toLowerCase().contains("md5")) {
                sourceMd5 = sourceheaders.getString(fieldName);
            } else if(fieldName.toLowerCase().contains("sha1")) {
                sourceSha1 = sourceheaders.getString(fieldName);
            } else if(fieldName.toLowerCase().equalsIgnoreCase("etag")) {
                sourceEtag = sourceheaders.getString(fieldName);
            }
        }
        if(!sourceMd5.isEmpty()) {
            compareMd5 = localMd5.equalsIgnoreCase(sourceMd5);
            content.put("checksum" , String.valueOf(compareMd5));
        } else if(!sourceSha1.isEmpty()) {
            compareSha1 = localSha1.equalsIgnoreCase(sourceSha1);
            content.put("checksum" , String.valueOf(compareSha1));
        } else {
            if(!sourceEtag.isEmpty()) {
                String etagChecksum = sourceEtag.split("\\{")[2];
                etagChecksum = etagChecksum.split("\\}")[0];
//                                logger.info(etagChecksum + "\n" +localMd5 + "\n" + localSha1);
                if(etagChecksum.equalsIgnoreCase(localMd5) || etagChecksum.equalsIgnoreCase(localSha1)) {
                    compareEtag = true;
                    content.put("checksum" , String.valueOf(compareEtag));
                }
            } else {
                compareEtag = false;
                content.put("checksum" , String.valueOf(compareEtag));
            }
        }

        return content;
    }

    private Future<JsonObject> contentInDb(JsonObject contentWithLocalAndSourceHeaders) {
        Promise<JsonObject> promise = Promise.promise();
        List<Object> objects =
                Arrays.asList(contentWithLocalAndSourceHeaders.getString("parentpath"),
                        contentWithLocalAndSourceHeaders.getString("filename"),
                        contentWithLocalAndSourceHeaders.getString("filesystem")
                );
        contentMapper.get(objects , res -> {
            if(res.succeeded()) {
                promise.complete(contentWithLocalAndSourceHeaders);
            } else {
                logger.info("\t\t\t\tGET CONTENT COMPARE FAILED: " + res.cause());
                promise.complete();
            }
        });
        return promise.future();
    }

    private Future<JsonObject> updateContent(JsonObject contentWithLocalAndSourceHeaders) {
        Promise<JsonObject> promise = Promise.promise();
        if(Objects.nonNull(contentWithLocalAndSourceHeaders)) {
            contentMapper.save(new Content(contentWithLocalAndSourceHeaders), update -> {
                if(update.succeeded()) {
                    promise.complete(contentWithLocalAndSourceHeaders);
                } else {
                    logger.info("\t\t\t\tUPDATE CONTENT COMPARE FAILED: " + update.cause());
                    promise.complete();
                }
            });
        } else {
            logger.info("CONTENT COMPARE GET FAILURE /NULL");
        }
        return promise.future();
    }

    private void handleContentCompare(AsyncResult<JsonObject> asyncResult) {
        if(asyncResult.succeeded()) {
            if(Objects.nonNull(asyncResult.result())) {
                logger.info("============< CONTENT COMPARE HEADERS SUCCEEDED >=============");
            } else {
                logger.info("CONTENT COMPARE HEADERS FAILED /NULL");
            }
        } else {
            logger.info("============< CONTENT COMPARE HEADERS FAILED: " + asyncResult.cause() + " >=============");
        }
    }

    private void handleCompleteCompareHeaders(AsyncResult<JsonObject> objectAsyncResult) {
        if(objectAsyncResult.succeeded()) {
            if(Objects.nonNull(objectAsyncResult.result())) {
                JsonObject repoAndContents = objectAsyncResult.result();
                logger.info("==< HEADERS COMPARE COMPLETE / REPO: \n" + repoAndContents.getJsonObject("repo") + " >====================");
                logger.info("====> CHANGING STAGE <====");
                changeRepositoryStateToFinish(repoAndContents.getJsonObject("repo").getString("key"))
                        .setHandler(ar -> {
                            if(ar.succeeded()) {
                                logger.info("REPO STAGE CHANGED: " + ar.result().encodePrettily() + "\n NEXT REPO...");
                                vertx.setTimer(TimeUnit.SECONDS.toMillis(10) , timer -> {
                                    vertx.eventBus().send(Topics.REPO_GET_ONE , new JsonObject().put("type" , "maven"));
                                });
                            } else {
                                logger.info("REPO STAGE UPDATE FAILED: " + ar.cause());
                            }
                        });
            } else {
                logger.info("HEADERS COMPARE FAILURE /NULL");
            }
        } else {
            logger.info(" PROBLEM COMPARING HEADERS: " + objectAsyncResult.cause());
        }
    }

    private Future<JsonObject> changeRepositoryStateToFinish(String repoKey) {
        Promise<JsonObject> promise = Promise.promise();
        repoMapper.get(Collections.singletonList(repoKey) , res -> {
            if(res.succeeded()) {
                RemoteRepository repo = res.result();
                repo.setStage(RepoStage.FINISH);
                repo.setCompared(true);
                repoMapper.save(repo , save -> {
                    if(save.succeeded()) {
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
