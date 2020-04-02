package com.commonjava.reptoro.contents;


import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.exceptions.QueryExecutionException;
import com.datastax.driver.core.exceptions.QueryValidationException;
import io.vertx.cassandra.CassandraClient;
import io.vertx.cassandra.CassandraClientOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContentProcessingServiceImpl implements ContentProcessingService {


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
    private String browsedStoreApi;
    private String contentStoreApi;

    private Session indystorage;
    private CassandraClient cassandraIndyRClient;
    private CassandraClient cassandraReptoroClient;


    public ContentProcessingServiceImpl(Vertx vertx, WebClient webClient, JsonObject config) {

        JsonObject indyConfig = config.getJsonObject("indy");
        JsonObject cassandraConfig = config.getJsonObject("cassandra");

        this.vertx = vertx;
        this.client = webClient;
        this.config = config;
        this.indyHost = indyConfig.getString("host");
        this.indyPort = indyConfig.getInteger("port");
        this.indyUser = indyConfig.getString("user");
        this.indyPass = indyConfig.getString("pass");
        this.indyApi = indyConfig.getString("api");
        this.mavenApi = indyConfig.getString("mavenApi");
        this.npmApi = indyConfig.getString("npmApi");
        this.browsedStoreApi = indyConfig.getString("browsedStoreApi");
        this.contentStoreApi = indyConfig.getString("contentStoreApi");

        CassandraClientOptions cassandraClientOptions = new CassandraClientOptions();

        String user = cassandraConfig.getString("user");
        String pass = cassandraConfig.getString("pass");
        Integer port = cassandraConfig.getInteger("port");
        String cassandraHostname = cassandraConfig.getString("hostname");
        String cassandraKeyspace = cassandraConfig.getString("keyspace");

        JsonObject reptoroCassandraConfig = cassandraConfig.getJsonObject("reptoro");
        String reptoroKeyspace = reptoroCassandraConfig.getString("keyspace");

        Cluster build = cassandraClientOptions
                .dataStaxClusterBuilder()
                .withPort(port)
                .withCredentials(user, pass)
                .addContactPoint(cassandraHostname)
                .build();
        this.indystorage = build.connect(cassandraKeyspace);

        cassandraClientOptions
                .setKeyspace(cassandraKeyspace)
                .dataStaxClusterBuilder()
                .withPort(port)
                .withCredentials(user, pass)
                .addContactPoint(cassandraHostname);

        this.cassandraIndyRClient = CassandraClient.create(vertx,cassandraClientOptions);

        CassandraClientOptions cassandraReptoroClientOptions = new CassandraClientOptions();
        cassandraReptoroClientOptions
                .setKeyspace(reptoroKeyspace)
                .dataStaxClusterBuilder()
                .withPort(port)
                .withCredentials(user, pass)
                .addContactPoint(cassandraHostname);
        this.cassandraReptoroClient = CassandraClient.create(vertx,cassandraReptoroClientOptions);
    }

    @Override
    public void getLocalHeadersSync(JsonObject content, Handler<AsyncResult<JsonObject>> handler) {
        String repoKey = content.getString("filesystem");
        String repoName = repoKey.split(":")[repoKey.split(":").length - 1];
        String path = indyApi + contentStoreApi + repoName + content.getString("parentpath") +"/" + content.getString("filename");

//        logger.info("[[CONTENT>LOCAL>URL]]: " + "http://" + indyHost + ":" + indyPort + path);

        client
                .head(indyPort,indyHost,path)
                .followRedirects(true)
//                .basicAuthentication(indyUser, indyPass)
                .timeout(TimeUnit.SECONDS.toMillis(90))
                .send(res -> {
                    if (res.succeeded()) {
                        HttpResponse<Buffer> result = res.result();
                        if (result.statusCode() == 200) {
                            Iterator<Map.Entry<String, String>> headers = result.headers().iterator();
                            JsonObject headersJson = new JsonObject();
                            while (headers.hasNext()) {
                                Map.Entry<String, String> headerTuple = headers.next();
                                headersJson.put(headerTuple.getKey(), headerTuple.getValue());
                            }
                            content.put("localheaders",headersJson);
                            handler.handle(Future.succeededFuture(content));

                        } else {
                            JsonObject badHeadResponse = new JsonObject();
                            badHeadResponse.put("badresponse" , true);
                            badHeadResponse.put("timestamp" , Instant.now());
                            badHeadResponse.put("result" , result.bodyAsString());
                            badHeadResponse.put("http", "HEAD");
                            badHeadResponse.put("content","local");
                            content.put("localheaders",badHeadResponse);
                            handler.handle(Future.succeededFuture(content));
                        }
                    } else {
                        JsonObject failed = new JsonObject();
                        failed.put("timestamp" , Instant.now());
                        failed.put("success" , false);
                        failed.put("cause" , res.cause().getMessage());
                        content.put("localheaders",failed);
                        handler.handle(Future.succeededFuture(content));
                    }
                });
    }

    @Override
    public void getSourceHeadersSync(String source , JsonObject content, Handler<AsyncResult<JsonObject>> handler) {
        String path = content.getString("parentpath") + "/" + content.getString("filename");
//        String source = content.getString("source");
        URL sourceUrl = null;
        String HTTPS = "https";
        URL httpsSourceUrl = null;

        try {
            sourceUrl = new URL(source);
            httpsSourceUrl = new URL(HTTPS, sourceUrl.getHost(), sourceUrl.getPort(), sourceUrl.getPath().substring(0,sourceUrl.getPath().length()-1) + path); //sourceUrl.getPath().substring(0,sourceUrl.getPath().length()-1) + path);

//          logger.info("[[CONTENT>SOURCE>URL]]: " + httpsSourceUrl.toString());


            client
              .headAbs(httpsSourceUrl.toString())
              .followRedirects(true)
              .timeout(TimeUnit.SECONDS.toMillis(90))
              .send(res -> {
              if (res.succeeded()) {
                HttpResponse<Buffer> result = res.result();
                if (result.statusCode() == 200) {
                  Iterator<Map.Entry<String, String>> headers = result.headers().iterator();
                  JsonObject headersJson = new JsonObject();
                  while (headers.hasNext()) {
                    Map.Entry<String, String> headerTuple = headers.next();
                    headersJson.put(headerTuple.getKey(), headerTuple.getValue());
                  }
                  content.put("sourceheaders",headersJson);
                  handler.handle(Future.succeededFuture(content));
                } else {
                  JsonObject badHeadResponse = new JsonObject();
                  badHeadResponse.put("badresponse" , true);
                  badHeadResponse.put("timestamp" , Instant.now());
                  badHeadResponse.put("result" , result.bodyAsString());
                  badHeadResponse.put("http", "HEAD");
                  badHeadResponse.put("content","source");
                  Iterator<Map.Entry<String, String>> headers = result.headers().iterator();
                  while (headers.hasNext()) {
                    Map.Entry<String, String> headerTuple = headers.next();
                    badHeadResponse.put(headerTuple.getKey(), headerTuple.getValue());
                  }
                  content.put("sourceheaders",badHeadResponse);
                  handler.handle(Future.succeededFuture(content));
                }
              } else {
                JsonObject failed = new JsonObject();
                failed.put("timestamp" , Instant.now());
                failed.put("success" , false);
                failed.put("cause" , res.cause().getMessage());
                content.put("sourceheaders",failed);
                handler.handle(Future.succeededFuture(content));
              }
            });


        }
        catch (Exception e) {
          JsonObject me =
            new JsonObject().put("url", sourceUrl)
            .put("path", path)
            .put("exception", e.getMessage())
            .put("timestamp", Instant.now())
            .put("badresponse", true)
            .put("http", "HEAD")
            .put("content", "source");
          content.put("sourceheaders",me);
          handler.handle(Future.succeededFuture(content));
        }

    }

    @Override
    public void getContentsForRemoteRepository(JsonObject repo, Handler<AsyncResult<JsonObject>> handler) {
        String query = "select * from indystorage.pathmap where filesystem='" + repo.getString("key") +  "' and size>0 allow filtering";

        try {
//            ResultSet resultSet = indystorage.execute(query);

            ResultSetFuture resultSetFuture = indystorage.executeAsync(query);
            ResultSet resultSet = resultSetFuture.getUninterruptibly();

            JsonArray dataArr = new JsonArray();

            Iterator<Row> iterator = resultSet.iterator();
            while ((iterator.hasNext())) {
                if(resultSet.getAvailableWithoutFetching() == 100 && !resultSet.isFullyFetched()) {
                    resultSet.fetchMoreResults();
                }
                Row row = iterator.next();
                JsonObject data = new JsonObject();
                data.put("filesystem", row.getString("filesystem"));
                data.put("parentpath", row.getString("parentpath"));
                data.put("filename", row.getString("filename"));
                data.put("checksum", ""); // row.getString("checksum")
                data.put("fileid", row.getString("fileid"));
                data.put("filestorage", row.getString("filestorage"));
                data.put("size", row.getLong("size"));
                dataArr.add(data);
            }

            JsonObject data = new JsonObject();
            data.put("data", dataArr);
            data.put("repo",repo);
            handler.handle(Future.succeededFuture(data));

        } catch (NoHostAvailableException nhaexc) {
            logger.log(Level.WARNING , ">>> \tNo Host Available Exception: {0}" , nhaexc.getMessage());
            handler.handle(Future.failedFuture(Json.encode(new JsonObject().put("exception",nhaexc.getMessage()))));
        } catch (QueryExecutionException qeexc) {
            logger.log(Level.WARNING , ">>> \tQuery Execution Exception: {0}" , qeexc.getMessage());
            handler.handle(Future.failedFuture(Json.encode(new JsonObject().put("exception",qeexc.getMessage()))));
        } catch (QueryValidationException qvexc) {
            logger.log(Level.WARNING , ">>> \tQuery Validation Exception: {0}" , qvexc.getMessage());
            handler.handle(Future.failedFuture(Json.encode(new JsonObject().put("exception",qvexc.getMessage()))));
        } catch (Exception e) {
            logger.log(Level.WARNING , ">>> \tException: {0}" , e.getMessage());
            handler.handle(Future.failedFuture(Json.encode(new JsonObject().put("exception",e.getMessage()))));
        }
    }

    @Override
    public void getContentsForRemoteRepositoryAsync(JsonObject repo, Handler<AsyncResult<JsonObject>> handler) {
        String query = "select * from indystorage.pathmap where filesystem='" + repo.getString("key") + "' and size>0 allow filtering";

        cassandraIndyRClient.execute(query, res -> {
            if (res.succeeded()) {
                io.vertx.cassandra.ResultSet result = res.result();
//                List<Row> results = res.result();
                JsonArray dataArr = new JsonArray();

                result.all(all -> {
                    if(all.succeeded()) {
                        List<Row> results = all.result();
                        Iterator<Row> rows = results.iterator();
                        while (rows.hasNext()) {
                            if(result.getAvailableWithoutFetching() == 100 && !result.isFullyFetched()) {
                                result.fetchMoreResults(more -> {
                                    logger.info("... Fetching more results....");
                                });
                            }
                            Row row = rows.next();
                            JsonObject data = new JsonObject();
                            data.put("filesystem", row.getString("filesystem"));
                            data.put("parentpath", row.getString("parentpath"));
                            data.put("filename", row.getString("filename"));
                            data.put("checksum", row.getString("checksum"));
                            data.put("fileid", row.getString("fileid"));
                            data.put("filestorage", row.getString("filestorage"));
                            data.put("size", row.getLong("size"));
                            dataArr.add(data);

                        }
                    }
                });

                JsonObject repoAndContents = new JsonObject();
                repoAndContents.put("data", dataArr);
                repoAndContents.put("repo", repo);
                handler.handle(Future.succeededFuture(repoAndContents));
            } else {
                handler.handle(Future.failedFuture(res.cause()));
            }
        });
    }

    @Override
    public void getContentsFromDb(JsonObject repo, Handler<AsyncResult<JsonObject>> handler) {
        String query = "select * from reptoro.contents where filesystem='" + repo.getString("key") + "' allow filtering";

        cassandraReptoroClient.execute(query , res -> {
           if(res.succeeded()) {
               io.vertx.cassandra.ResultSet result = res.result();
               JsonArray dataArr = new JsonArray();

               result.all(all -> {
                   if(all.succeeded()) {
                       List<Row> results = all.result();
                       Iterator<Row> rows = results.iterator();
                       while (rows.hasNext()) {
                           if(result.getAvailableWithoutFetching() == 100 && !result.isFullyFetched()) {
                               result.fetchMoreResults(more -> {
                                   logger.info("... Fetching more results....");
                               });
                           }
                           Row row = rows.next();
                           JsonObject data = new JsonObject();
                           data.put("filesystem", row.getString("filesystem"));
                           data.put("parentpath", row.getString("parentpath"));
                           data.put("filename", row.getString("filename"));
                           data.put("checksum", row.getString("checksum"));
                           data.put("fileid", row.getString("fileid"));
                           data.put("filestorage", row.getString("filestorage"));
                           data.put("size", row.getLong("size"));
                           data.put("sourceheaders" , row.getString("sourceheaders"));
                           data.put("localheaders" , row.getString("localheaders"));
                           data.put("source" , row.getString("source"));
                           dataArr.add(data);

                       }
                       JsonObject repoAndContents = new JsonObject();
                       repoAndContents.put("data", dataArr);
                       repoAndContents.put("repo", repo);
                       repoAndContents.put("key", repo.getString("key"));
                       handler.handle(Future.succeededFuture(repoAndContents));
                   } else {
                       handler.handle(Future.failedFuture(all.cause()));
                   }
               });

           } else {
               handler.handle(Future.failedFuture(res.cause()));
           }
        });

    }
}
