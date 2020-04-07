package com.commonjava.reptoro.remoterepos;


import com.commonjava.reptoro.common.Const;
import com.commonjava.reptoro.common.RepoStage;
import com.commonjava.reptoro.common.Topics;
import com.commonjava.reptoro.contents.Content;
import com.commonjava.reptoro.contents.ContentProcessingService;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import io.vertx.cassandra.*;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.commonjava.reptoro.remoterepos.RemoteRepository.toJson;

public class ProcessingRepositoriesVerticle extends AbstractVerticle {

    Logger logger = Logger.getLogger(this.getClass().getName());

    private RemoteRepositoryService repoService;
    private ContentProcessingService contentProcessingService;
    private io.vertx.cassandra.CassandraClient cassandraClient;
    private io.vertx.cassandra.CassandraClient indyCassandraClient;
    private Mapper<RemoteRepository> mapper;
    private Mapper<Content> contentMapper;
    private Session contentClientSession;
    public Boolean repoFetched;

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        this.repoFetched = false;
        DeliveryOptions options = new DeliveryOptions().setSendTimeout(180000);
        this.repoService = RemoteRepositoryService.createProxyWithOptions(vertx, Const.REPO_SERVICE,options);
        this.cassandraClient = new com.commonjava.reptoro.common.CassandraClient(vertx,config()).getCassandraReptoroClientInstance();
        MappingManager mappingManagerRepos = MappingManager.create(this.cassandraClient);
        MappingManager mappingManagerContents = MappingManager.create(this.cassandraClient);
        this.mapper = mappingManagerRepos.mapper(RemoteRepository.class);
        this.contentMapper = mappingManagerContents.mapper(Content.class);
        this.indyCassandraClient = new com.commonjava.reptoro.common.CassandraClient(vertx,config()).getCassandraIndyClientInstance();
        this.contentProcessingService = ContentProcessingService.createProxy(vertx,Const.CONTENT_SERVICE);

        CassandraClientOptions cassandraClientOptions = new CassandraClientOptions();
        JsonObject cassandraConfig = config().getJsonObject("cassandra");
        Cluster build =
                cassandraClientOptions
                .dataStaxClusterBuilder()
                .withCredentials(cassandraConfig.getString("user"), cassandraConfig.getString("pass"))
                .withPort(config().getJsonObject("cassandra").getInteger("port"))
                .addContactPoint(cassandraConfig.getString("hostname"))
                .build();
        String cassandraKeyspace = cassandraConfig.getString("keyspace");
        this.contentClientSession = build.connect(cassandraKeyspace);
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
        indyCassandraClient.close(res -> {
            if(res.failed()) {
                logger.info("INDYSTORAGE CASSANDRA CLIENT FAILED TO CLOSE CONNECTION!");
            } else {
                logger.info("INDYSTORAGE CASSANDRA CLIENT SUCCESSFULY CLOSED CONNECTION!");
            }
        });

        if(!contentClientSession.isClosed()) {
            logger.info("CLOSING CASSANDRA CLIENT CONNECTION...");
            contentClientSession.close();
        }

    }

    @Override
    public void start() throws Exception {

        EventBus eventBus = vertx.eventBus();

        eventBus.consumer(Topics.REPO_FETCH , this::handleRemoteRepoFetch);

        eventBus.consumer(Topics.REPO_GET_ONE , this::handleGetOneRepository);

        eventBus.consumer(Topics.REPO_START , this::handleRemoteRepoStart);

    }

    private void handleRemoteRepoFetch(Message<JsonObject> tMessage) {
        logger.info("==============> FETCHING REMOTE REPOSITORIES FROM INDY <================");
        processRemoteRepositories()
                .setHandler(res -> {
                    if (res.succeeded()) {
                       CompositeFuture.join(
                                res.result().stream()
                                    .map(repo -> recordInDb(repo).compose(this::recordSave)
//                                      .setHandler(this::handleSuccessOrFailure)
                                    )
                                .collect(Collectors.toList())
                       ).onComplete(ar -> {
                            if(ar.succeeded()) {
                                logger.info("REMOTE REPOSITORIES ARE STORED IN DB \n ... START PROCESSING ONE REPOSITORY");
                                vertx.eventBus().send(Topics.REPO_GET_ONE , new JsonObject().put("type" , "maven"));
                            } else {
                                logger.info("REMOTE REPOSITORIES NOT WRITEN IN DB: " + ar.cause());
                            }
                        });

                    } else {
                        logger.info("=> FAILURE: " + res.cause());
                    }
                })
        ;
    }

    private void handleGetOneRepository(Message<JsonObject> tMessage) {
        JsonObject packageType = tMessage.body();
        if(packageType.getString("type").equalsIgnoreCase("maven")) {
            logger.info("===> FETCHING ONE MAVEN REPOSITORY FROM DB <===");
            repoService.getOneRemoteRepository(asyncResult -> {
                if(asyncResult.succeeded()) {
                    logger.info("... STARTING PROCESSING REPO: " + asyncResult.result().getString("key") + " At " + Instant.now());
                    JsonObject repo = asyncResult.result();
                    vertx.eventBus().send(Topics.REPO_START , repo );
                } else {
                    logger.info("FAILED FETCHING REPO FROM DB! OR ALL REPOSITORIES ARE PROCESSED!");
                    // TODO Failure starting processing repo handler!
                }
            });
        } else {
            logger.info("====  PACKAGE TYPE " + packageType + " NOT SUPPORTED YET! ");
        }

    }

    private void handleRemoteRepoStart(Message<JsonObject> tMessage) {
        JsonObject repo = tMessage.body();
        logger.info(repo.encodePrettily());

        // TODO check and test different stage logic...

        switch (repo.getString("stage")) {
            case RepoStage.START:
                getProxyRepoContents(repo)
                        .compose(this::storeContentsInRepoJson)
                        .compose(this::saveContentsInDb)
                        .setHandler(this::handleContentsRepoResult);
                break;

            case RepoStage.CONTENT:
                getContentsFromDb(repo)
                        .setHandler(res -> {
                            if (res.succeeded()) {
                                logger.info("CONTENTS: " + res.result().getJsonArray("data").size());
                                vertx.eventBus().send(Topics.CONTENT_HEADERS, res.result()); // contents,repo and key json
                            } else {
                                logger.info("PROBLEM LOADING CONTENTS FROM DB: " + res.cause());
                            }
                        });

                break;

            case RepoStage.HEADERS:
                vertx.eventBus().send(Topics.COMPARE_HEADERS , repo); // repo from db json
                break;

            case RepoStage.COMPARE_HEADERS:
            case RepoStage.FINISH:
            default:
                logger.info("REPO: " + repo.getString("key") + " IS IN " + repo.getString("stage") + " STAGE");
                break;
        }
    }

    private Future<JsonObject> storeContentsInRepoJson(JsonObject resultData) {
        Promise<JsonObject> promise = Promise.promise();
        if(Objects.nonNull(resultData)) {
            JsonArray contents = resultData.getJsonArray("data");
            JsonObject repo = resultData.getJsonObject("repo");
            String url = repo.getString("url");
            String key = repo.getString("key");

            List<JsonObject> contentsList =
                    contents.stream()
                        .map(content -> new JsonObject(content.toString()))
                        .map(content -> content.put("source", url))
                        .map(content -> content.put("checksum",""))
                        .map(content -> content.put(Const.LOCALHEADERS,new JsonObject()))
                        .map(content -> content.put(Const.SOURCEHEADERS,new JsonObject()))
                        .collect(Collectors.toList());
            JsonArray objects = new JsonArray(contentsList);

            JsonObject repoContentsKey = new JsonObject().put("contents", objects).put("key",key);

            logger.info("... FETCHED: " + contents.size() + " CONTENTS FOR " + repo.getString("key") + " REPOSITORY...");
            promise.complete(repoContentsKey);
        } else {
            promise.complete();
        }
        return promise.future();
    }

    private Future<JsonObject> saveContentsInDb(JsonObject contentsAndKey) {
        Promise<JsonObject> promise = Promise.promise();
        if(Objects.nonNull(contentsAndKey)) {
            JsonArray contentsArr = contentsAndKey.getJsonArray("contents");

            CompositeFuture.join(
                    contentsArr
                            .stream()
                            .map(content -> new JsonObject(content.toString()))
                            .map(content -> contentInDb(content)
                                                .compose(this::saveContent)
//                                                .setHandler(this::handleSavedContents)
                            )
                            .collect(Collectors.toList())
            ).onComplete(res -> {
                if(res.succeeded()) {
                    promise.complete(contentsAndKey);
                } else {
                    promise.complete();
                }
            });

        } else {
            //TODO Handle contents not saved operation...
            logger.info(" --- CONTENTS NOT SAVED! /NULL");
        }
        return promise.future();
    }

    private Future<JsonObject> contentInDb(JsonObject content) {
        Promise<JsonObject> promise = Promise.promise();
        List<Object> objects =
                Arrays.asList(content.getString("parentpath"), content.getString("filename"), content.getString("filesystem"));
        contentMapper.get(objects, res -> {
            if(res.succeeded()) {
                if(Objects.isNull(res.result())) {
                    promise.complete(content);
                } else {
                    promise.complete();
                }
            } else {
                logger.info("CAUSE: " + res.cause());
                promise.complete();
            }
        });
        return promise.future();
    }

    private Future<JsonObject> saveContent(JsonObject content) {
        Promise<JsonObject> promise = Promise.promise();
        if(Objects.nonNull(content)) {
//            contentMapper.save(new Content(content), res -> {
//                if(res.succeeded()) {
//                    promise.complete(content);
//                } else {
//                    promise.complete();
//                }
//            });
            vertx.eventBus().send(Topics.SAVE_CONTENT , content);
            promise.complete(content);
        } else {
            promise.complete();
        }
        return promise.future();
    }

    private void handleSavedContents(AsyncResult<JsonObject> voidAsyncResult) {
        if(voidAsyncResult.succeeded()) {
            if(Objects.nonNull(voidAsyncResult.result())) {
//                logger.info("SUCESS STORING CONTENT: " + voidAsyncResult.result().getString("filename"));
            } else {
                logger.info("CONTENT NOT SAVED /ALREADY IN DB");
            }
        } else {
            logger.info("FAILURE STORING CONTENT: " + voidAsyncResult.cause());
        }
    }

    private void handleContentsRepoResult(AsyncResult<JsonObject> ar) {
        if(ar.succeeded()) {
            JsonObject contentsAndKey = ar.result();
            if(Objects.nonNull(contentsAndKey)) {
                logger.info("...FINISH FETCHING CONTENTS FOR REPO: " + contentsAndKey.getString("key") + "\n...CHANGE REPO STAGE... ");

                changeRepositoryStateToContent(contentsAndKey.getString("key"))
                        .setHandler(res -> {
                            if(res.succeeded()) {
                              JsonObject repo = res.result();
                              logger.info("REPO STAGE CHANGED: " + repo.encodePrettily() + "\n NEXT STAGE...");
                              contentsAndKey.put("repo",repo);
                                vertx.setTimer(TimeUnit.SECONDS.toMillis(30) , timer -> {
                                    vertx.eventBus().send(Topics.CONTENT_HEADERS , contentsAndKey);
                                });
                            } else {
                                logger.info("REPO STAGE UPDATE FAILED: " + res.cause());
                            }
                        });

            } else {
                logger.info("...REPO NOT UPDATED / NULL: " + ar.result());
            }

        } else {
            logger.info("...REPO NOT UPDATED / FAIL: " + ar.result() + " CAUSE: " + ar.cause());
        }
    }

    private void handleSuccessOrFailure(AsyncResult<JsonObject> ar) {
        if (ar.succeeded()) {
            if(Objects.nonNull(ar.result())) {
                logger.info("SUCCESS: " + ar.result().getString("key"));
            } else {
                System.out.print(".");
            }
        } else {
            logger.info("FAILURE: " + ar.cause());
        }
    }

    private Future<List<JsonObject>> processRemoteRepositories() {
        Promise<List<JsonObject>> promise = Promise.promise();
        String packageType = "maven";
        repoService.fetchRemoteRepositories(packageType , res -> {
            if(res.succeeded()) {
              if(Objects.nonNull(res.result()) && !res.result().containsKey("result")) {
                JsonObject result = res.result();
                logger.info("= ITEMS SIZE: " + result.getJsonArray("items").size());
                List<JsonObject> repoList = new RemoteRepositories(config(),vertx).filterMavenNonSslRemoteRepositories(result);
                logger.info("== FILTERED ITEMS: " + repoList.size());
                promise.complete(repoList);
              } else {
                logger.info("INDY-BAD RESPONSE: " + res.result().encodePrettily());
              }
            } else {
                promise.fail(res.cause());
            }
        });
        return promise.future();
    }

    private Future<JsonObject> recordInDb(JsonObject repo) {
        Promise<JsonObject> promise = Promise.promise();
        mapper.get(Collections.singletonList(repo.getString("key")) , res -> {
            if(res.succeeded()) {
                if(Objects.isNull(res.result())) {
                    promise.complete(repo);
                } else {
                    promise.complete();
                }
            } else {
                logger.info("CAUSE: " + res.cause());
                promise.complete();
            }
        });
        return promise.future();
    }

    private Future<JsonObject> recordSave(JsonObject repo) {
        Promise<JsonObject> promise = Promise.promise();
        if(Objects.nonNull(repo)) {
            mapper.save(new RemoteRepository(repo) , dbRes -> {
                if (dbRes.succeeded()) {
                    promise.complete(repo);
                } else {
                    logger.info("SAVE FAILURE CAUSE: " + dbRes.cause());
                    promise.complete();
                }
            });
        } else {
            promise.complete();
        }
        return promise.future();
    }

    private Future<JsonObject> getProxyRepoContents(JsonObject repo) {
        Promise<JsonObject> promise = Promise.promise();
        contentProcessingService.getContentsForRemoteRepository(repo , res -> {
            if(res.succeeded()) {
                logger.info("> CASSANDRA FETCHED: " + res.result().getJsonArray("data").size() + " for repo: " + res.result().getJsonObject("repo").getString("key"));
                JsonObject repoContentsObj = res.result();
                promise.complete(repoContentsObj);
            } else {
                promise.complete();
            }
        });
        return promise.future();
    }

    private Future<JsonObject> getContentsFromDb(JsonObject repo) {
        Promise<JsonObject> promise = Promise.promise();
        contentProcessingService.getContentsFromDb(repo , res -> {
            if(res.succeeded()) {
                JsonObject contentsAndRepo = res.result();
                promise.complete(contentsAndRepo);
            } else {
                promise.complete();
            }
        });
        return promise.future();
    }

    private Future<JsonObject> changeRepositoryStateToContent(String repoKey) {
        Promise<JsonObject> promise = Promise.promise();
        mapper.get(Collections.singletonList(repoKey) , res -> {
            if(res.succeeded()) {
                RemoteRepository repo = res.result();
                repo.setStage(RepoStage.CONTENT);
                mapper.save(repo , save -> {
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
