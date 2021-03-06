package com.commonjava.reptoro.remoterepos;

import com.commonjava.reptoro.common.RepoStage;
import com.datastax.driver.core.Row;
import io.vertx.cassandra.*;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

import static com.commonjava.reptoro.remoterepos.RemoteRepository.toJson;

public class RemoteRepositoryServiceImpl implements RemoteRepositoryService {

    Logger logger = Logger.getLogger(this.getClass().getName());

    private Vertx vertx;
    private WebClient client;
    private JsonObject config;
    private String indyHost;
    private Integer indyPort;
    private String indyUser;
    private String indyPass;
    private String indyApi;
    private String mavenApi;
    private String npmApi;
    private CassandraClient cassandraReptoroClient;
    private CassandraClient cassandraIndyRClient;
    private Mapper<RemoteRepository> mapper;

    private final String CREATE_REPTORO_KEYSPACE = "CREATE KEYSPACE IF NOT EXISTS reptoro WITH replication = {'class':'SimpleStrategy', 'replication_factor' : 3};";
    private final String CREATE_REPTORO_REPOS_TABLE = "CREATE TABLE IF NOT EXISTS reptoro.repos(KEY text PRIMARY KEY,TYPE text,HOST text,PACKAGETYPE text,NAME text,URL text,COMPARED boolean,STAGE text);";
    private final String CREATE_REPTORO_CONTENTS_TABLE = "CREATE TABLE IF NOT EXISTS reptoro.contents(FILESYSTEM text,LOCALHEADERS text,SOURCEHEADERS text,SOURCE text,PARENTPATH text,FILENAME text,CHECKSUM text,FILEID text,FILESTORAGE text,SIZE bigint,PRIMARY KEY((PARENTPATH,FILENAME),FILESYSTEM));";
    private final String GET_ALL_REPOS = "SELECT * FROM reptoro.repos";

    public RemoteRepositoryServiceImpl(Vertx vertx, WebClient client, JsonObject config) {
        JsonObject indyConfig = config.getJsonObject("indy");
        JsonObject cassandraConfig = config.getJsonObject("cassandra");
        JsonObject reptoroConfig = config.getJsonObject("reptoro");

        this.vertx = vertx;
        this.client = client;
        this.config = config;
        this.indyHost = indyConfig.getString("host");
        this.indyPort = indyConfig.getInteger("port");
        this.indyUser = indyConfig.getString("user");
        this.indyPass = indyConfig.getString("pass");
        this.indyApi = indyConfig.getString("api");
        this.mavenApi = indyConfig.getString("mavenApi");
        this.npmApi = indyConfig.getString("npmApi");

        CassandraClientOptions cassandraClientOptions = new CassandraClientOptions();

        JsonObject reptoroCassandraConfig = cassandraConfig.getJsonObject("reptoro");

        String user = cassandraConfig.getString("user");
        String pass = cassandraConfig.getString("pass");
        Integer port = cassandraConfig.getInteger("port");
        String cassandraHostname = cassandraConfig.getString("hostname");
        String reptoroKeyspace = reptoroCassandraConfig.getString("keyspace");
        String reptoroTablename = reptoroCassandraConfig.getString("tablename");


        cassandraClientOptions
                .setKeyspace(reptoroKeyspace)
                .dataStaxClusterBuilder()
                .withoutJMXReporting()
                .withoutMetrics()
                .withPort(port)
                .withCredentials(user, pass)
                .addContactPoint(cassandraHostname);
        this.cassandraReptoroClient = CassandraClient.create(vertx,cassandraClientOptions);

        MappingManager mappingManager = MappingManager.create(this.cassandraReptoroClient);
        this.mapper = mappingManager.mapper(RemoteRepository.class);


    }

    @Override
    public void fetchRemoteRepositories(String packageType, Handler<AsyncResult<JsonObject>> handler) {
        client
                .get(indyPort, indyHost,
                        (packageType.equalsIgnoreCase("maven") || packageType.isEmpty()) ? this.indyApi + this.mavenApi : this.indyApi + this.npmApi)
//                .basicAuthentication(indyUser, indyPass)
                .send(res -> {
                    HttpResponse<Buffer> result = res.result();
                    if (result.statusCode() == 200) {
                        logger.info("\t ===> INDY HAS RESPONDED '"+result.statusCode()+"' <===\n\t\tRESPONSE: " + result.bodyAsJsonObject().getJsonArray("items").size());
                        handler.handle(Future.succeededFuture(result.bodyAsJsonObject()));
                    } else {
                        logger.info("\t ===> INDY HAS RESPONDED '"+result.statusCode()+"' <===\n\t\tRESPONSE: " + result.bodyAsString());
                        JsonObject entries = new JsonObject();
                        Iterator<Map.Entry<String, String>> headers = result.headers().iterator();
                        JsonObject headersJson = new JsonObject();
                        while (headers.hasNext()) {
                          Map.Entry<String, String> headerTuple = headers.next();
                          headersJson.put(headerTuple.getKey(), headerTuple.getValue());
                        }
                        entries.put("timestamp", Instant.now());
                        entries.put("result",result.bodyAsString());
                        entries.put("headers", headersJson);
                        entries.put("statuscode",result.statusCode());
                        handler.handle(Future.succeededFuture(entries));
                    }
                });
    }

    @Override
    public void checkCassandraConnection(Handler<AsyncResult<JsonObject>> handler) {
        cassandraReptoroClient.execute("select release_version from system.local" , res -> {
            if (res.succeeded()) {
                ResultSet result = res.result();
                result.one(one -> {
                    if (one.succeeded()) {
                        Row row = one.result();
                        String releaseVersion = row.getString("release_version");
                        handler.handle(Future.succeededFuture(new JsonObject().put("release",releaseVersion)));
                    } else {
                        one.cause().printStackTrace();
                    }
                });
            } else {
                JsonObject cause = new JsonObject();
                cause.put("timestamp", Instant.now());
                cause.put("cause",res.cause().getMessage());
                handler.handle(Future.succeededFuture(cause));
            }
        });
    }

    @Override
    public void createReptoroRepositoriesKeyspace(Handler<AsyncResult<JsonObject>> handler) {
        cassandraReptoroClient.execute( CREATE_REPTORO_KEYSPACE , res -> {
            if(res.succeeded()) {
                JsonObject createKeyspace = new JsonObject().put("result", "done").put("timestamp", Instant.now());
                handler.handle(Future.succeededFuture(createKeyspace));
            } else {
                JsonObject cause = new JsonObject();
                cause.put("timestamp", Instant.now());
                cause.put("cause",res.cause().getMessage());
                handler.handle(Future.succeededFuture(cause));
            }
        });
    }

    @Override
    public void creteReptoroRepositoriesTable(Handler<AsyncResult<JsonObject>> handler) {
        cassandraReptoroClient.execute(CREATE_REPTORO_REPOS_TABLE , res -> {
            if(res.succeeded()) {
                JsonObject createTable = new JsonObject().put("result", "done").put("timestamp", Instant.now());
                handler.handle(Future.succeededFuture(createTable));
            } else {
                JsonObject cause = new JsonObject();
                cause.put("timestamp", Instant.now());
                cause.put("cause",res.cause().getMessage());
                handler.handle(Future.succeededFuture(cause));
            }
        });
    }

    @Override
    public void creteReptoroContentsTable(Handler<AsyncResult<JsonObject>> handler) {
        cassandraReptoroClient.execute(CREATE_REPTORO_CONTENTS_TABLE , res -> {
            if(res.succeeded()) {
                JsonObject createTable = new JsonObject().put("result", "done").put("timestamp", Instant.now());
                handler.handle(Future.succeededFuture(createTable));
            } else {
                JsonObject cause = new JsonObject();
                cause.put("timestamp", Instant.now());
                cause.put("cause",res.cause().getMessage());
                handler.handle(Future.succeededFuture(cause));
            }
        });
    }

    @Override
    public void storeRemoteRepository(JsonObject repo , Handler<AsyncResult<JsonObject>> handler) {

        RemoteRepository remoteRepository = new RemoteRepository(repo);

        mapper.save(remoteRepository , res -> {
            if(res.succeeded()) {
                JsonObject storeRepo =
                        new JsonObject()
                                    .put("result", repo)
                                .put("key",repo.getString("key"))
                                .put("success" , true)
                                .put("timestamp", Instant.now());
                handler.handle(Future.succeededFuture(storeRepo));
            } else  {
                JsonObject cause = new JsonObject();
                cause.put("timestamp", Instant.now());
                cause.put("success" , false);
                cause.put("cause",res.cause().getMessage());
                handler.handle(Future.succeededFuture(cause));
            }
        });
    }

    @Override
    public void getRemoteRepository(JsonObject repo, Handler<AsyncResult<JsonObject>> handler) {

        mapper.get(Collections.singletonList(repo.getString("key")) , res -> {
            if(res.succeeded()) {
                if(Objects.nonNull(res.result())) {
                    RemoteRepository result = res.result();
                    JsonObject repoJson = toJson(result);
                    handler.handle(Future.succeededFuture(repoJson));
                } else {
                    JsonObject repo1 = new JsonObject();
                    repo1.put("exists" , false);
                    handler.handle(Future.succeededFuture(repo1));
                }

            } else {
                JsonObject cause = new JsonObject();
                cause.put("timestamp", Instant.now());
                cause.put("exists",false);
                cause.put("cause",res.cause().getMessage());
                handler.handle(Future.succeededFuture(cause));
            }
        });
    }

    @Override
    public void updateRemoteRepository(JsonObject repo, Handler<AsyncResult<JsonObject>> handler) {

        mapper.get(Collections.singletonList(repo.getString("key")) , res -> {
            if(res.succeeded()) {
                if(Objects.nonNull(res.result())) {
                    mapper.save(new RemoteRepository(repo) , ar -> {
                        if(ar.succeeded()) {
                            JsonObject updateOperation =
                                    new JsonObject()
                                            .put("success", true)
                                            .put("operation", "update")
                                            .put("timestamp" , Instant.now())
                                            .put("key" , repo.getString("key"))
                                            .put("result" , repo)
                                    ;
                            logger.info("CONTENTS UPDATE: " + repo.getJsonArray("contents").size() + " SUCCEEDED");
                            handler.handle(Future.succeededFuture(updateOperation));
                        } else {
                            logger.info("CONTENTS UPDATE: " + repo.getJsonArray("contents").size() + " FAILED");
                            handler.handle(Future.succeededFuture(
                                    new JsonObject()
                                            .put("key" , repo.getString("key"))
                                            .put("success", false)
                                            .put("operation", "update")
                                            .put("cause",res.cause())
                                            .put("timestamp" , Instant.now())));
                        }

                    });

                } else {
                    handler.handle(Future.succeededFuture(
                            new JsonObject().put("success", false).put("operation", "update").put("timestamp" , Instant.now())));
                }
            } else {
                JsonObject cause = new JsonObject();
                cause.put("timestamp", Instant.now());
                cause.put("success" , false);
                cause.put("cause",res.cause().getMessage());
                handler.handle(Future.succeededFuture(cause));
            }
        } );
    }

    @Override
    public void getOneRemoteRepository(Handler<AsyncResult<JsonObject>> handler) {

        cassandraReptoroClient.execute(GET_ALL_REPOS , res -> {
           if(res.failed()) {
               handler.handle(Future.failedFuture(res.cause()));
           } else {
               ResultSet result = res.result();
               result.all(repos -> {
                   List<Row> results = repos.result();
                   JsonObject repoResult = new JsonObject();

                   for(Row row : results) {
                       String key = row.getString("key");
                       String host = row.getString("host");
                       String name = row.getString("name");
                       String packagetype = row.getString("packagetype");
                       String stage = row.getString("stage");
                       String type = row.getString("type");
                       String url = row.getString("url");
                       boolean compared = row.getBool("compared");
                       if(!compared &&
                               (       stage.equalsIgnoreCase(RepoStage.START) ||
                                       stage.equalsIgnoreCase(RepoStage.CONTENT) ||
                                       stage.equalsIgnoreCase(RepoStage.HEADERS) ||
                                       stage.equalsIgnoreCase(RepoStage.COMPARE_HEADERS)
                               )
                       ) {
                           repoResult.put("key",key).put("host",host).put("name",name).put("packagetype",packagetype).put("stage",stage)
                                   .put("type",type).put("url",url).put("compared",compared);
                           handler.handle(Future.succeededFuture(repoResult));
                       }
                   }
               });
           }
        });
    }

    @Override
    public void updateNewRemoteRepositories(List<JsonObject> repos, Handler<AsyncResult<JsonObject>> handler) {

        for(JsonObject repo : repos) {
            String key = repo.getString("key");
            mapper.get(Collections.singletonList(key) , res -> {
                if(res.succeeded()) {
                    if (Objects.isNull(res.result())) {
                        RemoteRepository remoteRepository = new RemoteRepository(repo);
                        mapper.save(remoteRepository , ar -> {
                            if (ar.succeeded()) {
                                handler.handle(Future.succeededFuture(repo));
                            } else {
                                handler.handle(Future.succeededFuture(
                                        new JsonObject().put("success", false).put("operation", "checkandsave").put("timestamp" , Instant.now())));
                            }
                        });
                    }
                } else {
                    handler.handle(Future.succeededFuture(
                            new JsonObject().put("success", false)
                                    .put("operation", "checkandsave")
                                    .put("cause",res.cause())
                                    .put("timestamp" , Instant.now())));
                }
            });
        }

    }

  @Override
  public void getAllRemoteRepositoriesFromDb(Handler<AsyncResult<JsonArray>> handler) {
    cassandraReptoroClient.execute(GET_ALL_REPOS , res -> {
      if(res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        ResultSet result = res.result();
        result.all(repos -> {
          List<Row> results = repos.result();

          JsonArray reposList = new JsonArray();

          for(Row row : results) {
            JsonObject repo = new JsonObject();
            repo
              .put("key",row.getString("key"))
              .put("host",row.getString("host"))
              .put("name",row.getString("name"))
              .put("packagetype",row.getString("packagetype"))
              .put("stage",row.getString("stage"))
              .put("type",row.getString("type"))
              .put("url",row.getString("url"))
              .put("compared",row.getBool("compared"));
            reposList.add(repo);

          }
          handler.handle(Future.succeededFuture(reposList));
        });
      }
    });
  }

  @Override
  public void getAllNotComparedRemoteRepositories(Handler<AsyncResult<JsonObject>> handler) {
    String query = "SELECT COUNT(*) FROM reptoro.repos WHERE compared=False ALLOW FILTERING;";

    cassandraReptoroClient.execute(query , res -> {
      if(res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        ResultSet result = res.result();
        result.one(rowResult -> {
          Row row = rowResult.result();
          if(Objects.nonNull(row)) {
            long count = row.getLong("count");
            handler.handle(Future.succeededFuture(new JsonObject().put("count", count)));
          } else {
            handler.handle(Future.succeededFuture(new JsonObject().put("count", 0)));
          }
        });
      }
    });
  }

  @Override
  public void getRemoteRepositoryCount(Handler<AsyncResult<JsonObject>> handler) {
    String query = "SELECT COUNT(*) FROM reptoro.repos ALLOW FILTERING;";

    cassandraReptoroClient.execute(query, res -> {
      if(res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        ResultSet result = res.result();
        result.one(rowResult -> {
          Row row = rowResult.result();
          handler.handle(Future.succeededFuture(new JsonObject().put("count", row.getLong("count"))));
        });
      }
    });
  }

  @Override
  public void changeRemoteRepositoryProtocol(JsonObject repoChange,String protocol,Handler<AsyncResult<JsonObject>> handler) {
    String url = repoChange.getString("url");

    String bearerToken = repoChange.getString("token");

    String[] urlArr = url.split("\\:");
    String previousProtocol = urlArr[0];
    String hostPath = urlArr[1];

    String changeUrl = String.format("%s:%s", protocol , hostPath);

    repoChange.put("url", changeUrl);
    repoChange.remove("token");


    logger.info(repoChange.encodePrettily());
    logger.info(changeUrl);
    logger.info("http://" + indyHost + ":" + indyPort + indyApi + mavenApi + "/" + repoChange.getString("name"));

    client
      .put(indyPort, indyHost, indyApi + mavenApi + "/" + repoChange.getString("name") )
//      .basicAuthentication(indyUser, indyPass)
      .bearerTokenAuthentication(bearerToken)
      .putHeader("Accept", "application/json, text/plain, */*")
      .putHeader("Content-Type", "application/json;charset=UTF-8")
      .sendJsonObject(repoChange,res -> {
        if(res.failed()) {
          handler.handle(Future.failedFuture(res.cause()));
        } else {
          HttpResponse<Buffer> result = res.result();
          int statusCode = result.statusCode();
          if(statusCode >= 400) {
            handler.handle(
              Future.failedFuture(
                new JsonObject().put("statuscode", statusCode).put("body", result.bodyAsString()).encode())
            );
          }

          JsonObject response = new JsonObject();
          Iterator<Map.Entry<String, String>> headers = result.headers().iterator();
          JsonObject headersJson = new JsonObject();
          while (headers.hasNext()) {
            Map.Entry<String, String> headerTuple = headers.next();
            headersJson.put(headerTuple.getKey(), headerTuple.getValue());
          }
          response.put("timestamp", Instant.now());
          response.put("result",repoChange);
          response.put("headers", headersJson);
          response.put("statuscode",statusCode);
          handler.handle(Future.succeededFuture(response));
        }
      });
  }


}
