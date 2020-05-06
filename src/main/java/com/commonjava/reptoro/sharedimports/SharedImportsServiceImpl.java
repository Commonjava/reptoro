package com.commonjava.reptoro.sharedimports;

import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.exceptions.QueryExecutionException;
import com.datastax.driver.core.exceptions.QueryValidationException;
import io.vertx.cassandra.*;
import io.vertx.cassandra.ResultSet;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.math.BigInteger;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.commonjava.reptoro.sharedimports.SharedImport.*;
import static com.commonjava.reptoro.sharedimports.SharedImport.toSiJson;


public class SharedImportsServiceImpl implements SharedImportsService {


  Logger logger = Logger.getLogger(this.getClass().getName());

  private Vertx vertx;
  private WebClient client;
  private JsonObject config;

  private Session sharedImportsSession;
  private CassandraClient cassandraReptoroClient;
  private Mapper<SharedImport> sharedImportMapper;

  private String indyHost;
  private Integer indyPort;
  private String indyUser;
  private String indyPass;
  private String indyApi;
  private String mavenApi;
  private String npmApi;
  private String sealedRecordsApi;
  private String sealedRecordReportApi;
  private String browseSharedImportsApi;



  private final String CREATE_REPTORO_IMPORTS_TABLE =
    "CREATE TABLE IF NOT EXISTS reptoro.sharedimports(ID text,STOREKEY text,ACCESSCHANNEL text,PATH text,ORIGINURL text,LOCALURL text,MD5 text,SHA256 text,SHA1 text,COMPARED boolean,PATHMATCH boolean,SOURCEHEADERS text,CHECKSUM boolean,PRIMARY KEY((ID,STOREKEY),PATH));";

  private final String GET_NOTVALIDATED_SHARED_IMPORTS = "SELECT * FROM reptoro.sharedimports where compared=False ALLOW FILTERING";
  private final String GET_ALL_SHARED_IMPORTS = "SELECT * FROM reptoro.sharedimports";
  private final String DELETE_BUILDID_MARKER = "DELETE FROM reptoro.sharedimports WHERE id='%s' AND storekey='' AND path=''";

  public SharedImportsServiceImpl() {}

  public SharedImportsServiceImpl(Vertx vertx, WebClient client, JsonObject config) {
    this.vertx = vertx;
    this.client = client;
    this.config = config;

    JsonObject indyConfig = config.getJsonObject("indy");
    JsonObject cassandraConfig = config.getJsonObject("cassandra");
    JsonObject reptoroConfig = config.getJsonObject("reptoro");

    CassandraClientOptions cassandraClientOptions = new CassandraClientOptions();
    JsonObject reptoroCassandraConfig = cassandraConfig.getJsonObject("reptoro");

    this.indyHost = indyConfig.getString("host");
    this.indyPort = indyConfig.getInteger("port");
    this.indyUser = indyConfig.getString("user");
    this.indyPass = indyConfig.getString("pass");
    this.indyApi = indyConfig.getString("api");
    this.mavenApi = indyConfig.getString("mavenApi");
    this.npmApi = indyConfig.getString("npmApi");

    this.sealedRecordsApi = indyConfig.getString("sealedRecordsApi");
    this.sealedRecordReportApi = indyConfig.getString("sealedRecordRaportApi");
    this.browseSharedImportsApi = indyConfig.getString("browseSharedImportsApi");

    String user = cassandraConfig.getString("user");
    String pass = cassandraConfig.getString("pass");
    Integer port = cassandraConfig.getInteger("port");
    String cassandraHostname = cassandraConfig.getString("hostname");
    String reptoroKeyspace = reptoroCassandraConfig.getString("keyspace");
    String reptoroTablename = reptoroCassandraConfig.getString("tablename");

    Cluster build = cassandraClientOptions
      .dataStaxClusterBuilder()
      .withoutJMXReporting()
      .withoutMetrics()
      .withPort(port)
      .withCredentials(user, pass)
      .addContactPoint(cassandraHostname)
      .build();
    this.sharedImportsSession = build.connect(reptoroKeyspace);

    cassandraClientOptions
      .setKeyspace(reptoroKeyspace)
      .dataStaxClusterBuilder()
      .withoutJMXReporting()
      .withoutMetrics()
      .withPort(port)
      .withCredentials(user, pass)
      .addContactPoint(cassandraHostname);
    this.cassandraReptoroClient = CassandraClient.create(vertx,cassandraClientOptions);
    MappingManager mappingManagerRepos = MappingManager.create(this.cassandraReptoroClient);
    this.sharedImportMapper = mappingManagerRepos.mapper(SharedImport.class);

  }

  @Override
  public void createTableSharedImports(Handler<AsyncResult<JsonObject>> handler) {
    cassandraReptoroClient.execute(CREATE_REPTORO_IMPORTS_TABLE , res -> {
      if(res.failed()) {
        JsonObject cause = new JsonObject();
        cause.put("timestamp", Instant.now());
        cause.put("cause",res.cause().getMessage());
        handler.handle(Future.succeededFuture(cause));
      } else {
        JsonObject createTable = new JsonObject().put("result", "done").put("timestamp", Instant.now());
        handler.handle(Future.succeededFuture(createTable));
      }
    });
  }

  @Override
  public void getAllSealedTrackingRecords(Handler<AsyncResult<JsonObject>> handler) {
    client
      .get(indyPort,indyHost,this.indyApi + this.sealedRecordsApi)
      .followRedirects(true)
      .timeout(TimeUnit.SECONDS.toMillis(60))
//      .basicAuthentication(indyUser, indyPass)
      .send(res -> {
        if (res.failed()) {
          handler.handle(Future.failedFuture(res.cause()));
        } else {
          if (res.result().statusCode() == 200) {
            JsonObject sealedRecords = res.result().bodyAsJsonObject();
            handler.handle(Future.succeededFuture(sealedRecords));
          } else {
            handler.handle(Future.failedFuture(res.result().bodyAsString()));
          }
        }
      });
  }

  @Override
  public void getOneSealedRecord(Handler<AsyncResult<JsonObject>> handler) {

    cassandraReptoroClient.execute(GET_NOTVALIDATED_SHARED_IMPORTS,res -> {
      if(res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        ResultSet result = res.result();
        result.all(repos -> {
          List<Row> results = repos.result();
          JsonObject repoResult = new JsonObject();

          for(Row row : results) {
            String id = row.getString("id");
            String storeKey = row.getString("storekey");
            String accessChannel = row.getString("accesschannel");
            String path = row.getString("path");
            String originUrl = row.getString("originurl");
            String localUrl = row.getString("localurl");
            String md5 = row.getString("md5");
            String sha256 = row.getString("sha256");
            String sha1 = row.getString("sha1");
            boolean compared = row.getBool("compared");
            boolean checksum = row.getBool("checksum");
            boolean pathmatch = row.getBool("pathmatch");
            String sourceheaders = row.getString("sourceheaders");
            if(originUrl.isEmpty() && localUrl.isEmpty()) {
              repoResult
                .put("id",id)
                .put("storekey",storeKey)
                .put("accesschannel",accessChannel)
                .put("path",path)
                .put("originurl",originUrl)
                .put("localurl",localUrl)
                .put("md5",md5)
                .put("sha256",sha256)
                .put("sha1",sha1)
                .put("compared",compared)
                .put("checksum",checksum)
                .put("pathmatch",pathmatch)
                .put("sourceheaders",sourceheaders)
              ;
              handler.handle(Future.succeededFuture(repoResult));
              return;
            }
          }
        });
      }
    });

  }


  //  {"results":[
//    [{sharedImport},{sharedImport},{sharedImport}], <- sharedImportsArray of buildId's
//    [{sharedImport},{sharedImport},{sharedImport}]  <- sharedImportsArray of buildId's
//  ]}
  @Override
  public void getAllSharedImportsFromDb(Handler<AsyncResult<JsonArray>> handler) {
    try {
      ResultSetFuture resultSetFuture = sharedImportsSession.executeAsync(GET_ALL_SHARED_IMPORTS);
      com.datastax.driver.core.ResultSet resultSet = resultSetFuture.getUninterruptibly();
      Iterator<Row> iterator = resultSet.iterator();
      JsonArray sharedImports = new JsonArray();

      while (iterator.hasNext()) {
        if (resultSet.getAvailableWithoutFetching() == 100 && !resultSet.isFullyFetched()) {
          resultSet.fetchMoreResults();
        }
        Row row = iterator.next();
        if(row.getBool("compared")) {
          sharedImports.add(toSiJson(row));
        }
      }
      handler.handle(Future.succeededFuture(sharedImports));

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
  public void getNotComparedSharedImportsFromDB(Handler<AsyncResult<JsonObject>> handler) {
    String query = "SELECT COUNT(*) FROM reptoro.sharedimports WHERE compared=False ALLOW FILTERING";

    cassandraReptoroClient.execute(query, res -> {
      if(res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        ResultSet result = res.result();
        result.one(rowResult -> {
          Row row = rowResult.result();
          if(Objects.nonNull(row)) {
            handler.handle(Future.succeededFuture(new JsonObject().put("count", row.getLong("count"))));
          } else {
            handler.handle(Future.succeededFuture(new JsonObject().put("count", 0)));
          }
        });
      }
    });
  }

  @Override
  public void getSharedImportContentCount(Handler<AsyncResult<JsonObject>> handler) {
    String query = "SELECT COUNT(*) FROM reptoro.sharedimports ALLOW FILTERING;";

    cassandraReptoroClient.execute(query, res -> {
      if(res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        ResultSet result = res.result();
        result.one(rowResult -> {
          Row row = rowResult.result();
          handler.handle(Future.succeededFuture(new JsonObject().put("count",row.getLong("count"))));
        });
      }
    });
  }

  @Override
  public void getSharedImportCount(Handler<AsyncResult<JsonArray>> handler) {
    try {
      ResultSetFuture resultSetFuture = sharedImportsSession.executeAsync(GET_ALL_SHARED_IMPORTS);
      com.datastax.driver.core.ResultSet resultSet = resultSetFuture.getUninterruptibly();
      Iterator<Row> iterator = resultSet.iterator();
      JsonArray sharedImports = new JsonArray();

      while (iterator.hasNext()) {
        if (resultSet.getAvailableWithoutFetching() == 100 && !resultSet.isFullyFetched()) {
          resultSet.fetchMoreResults();
        }
        Row row = iterator.next();
        sharedImports.add(row.getString("id"));
      }
      handler.handle(Future.succeededFuture(sharedImports));

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
  public void getSharedImportDownloads(String buildId, Handler<AsyncResult<JsonObject>> handler) {
    String query = "SELECT pathmatch,checksum FROM reptoro.sharedimports WHERE id='" + buildId + "' ALLOW FILTERING;";
//    String query = "SELECT * FROM reptoro.sharedimports WHERE id='" + buildId + "' ALLOW FILTERING;";

    cassandraReptoroClient.execute(query, res -> {
      if(res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        ResultSet result = res.result();

        result.all(rowResult -> {
          List<Row> rows = rowResult.result();
          Iterator<Row> iterator = rows.iterator();

          JsonArray downloads = new JsonArray();

          while (iterator.hasNext()) {
            Row row = iterator.next();
            JsonObject entry = new JsonObject();
            entry.put("pathmatch", row.getBool("pathmatch"))
              .put("checksum", row.getBool("checksum"));
            downloads.add(entry);
          }

//          while (iterator.hasNext()) {
//            Row row = iterator.next();
//            downloads.add(toSiJson(row));
//          }

          Stream<JsonObject> checksums = downloads.stream().map(el -> new JsonObject(el.toString()));
          Stream<JsonObject> pathmatchs = downloads.stream().map(el -> new JsonObject(el.toString()));
          boolean pathmatchCompared = pathmatchs.allMatch(el -> el.getBoolean("pathmatch"));
          boolean checksumCompared = checksums.allMatch(el -> el.getBoolean("checksum"));

          JsonObject sharedImportDownloads =
            new JsonObject()
              .put("checksums", checksumCompared)
              .put("pathmatch", pathmatchCompared)
              .put("count", downloads.size())
//              .put("downloads", downloads)
            ;

          handler.handle(Future.succeededFuture(sharedImportDownloads));

        });
      }
    });
  }

  @Override
  public void getDownloads(String buildId, Handler<AsyncResult<JsonObject>> handler) {
    String query = "SELECT * FROM reptoro.sharedimports WHERE id='" + buildId + "' ALLOW FILTERING;";

    cassandraReptoroClient.execute(query, res -> {
      if(res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        ResultSet result = res.result();

        result.all(rowResult -> {
          List<Row> rows = rowResult.result();
          Iterator<Row> iterator = rows.iterator();

          JsonArray downloads = new JsonArray();

          while (iterator.hasNext()) {
            Row row = iterator.next();
            downloads.add(toSiJson(row));
          }

          handler.handle(Future.succeededFuture(new JsonObject().put("results", downloads)));

        });
      }
    });

  }

  @Override
  public void getSealedRecordRaport(String buildId, Handler<AsyncResult<JsonObject>> handler) {
    logger.info("RECORD URL: http://" +indyHost+":"+indyPort+indyApi+sealedRecordReportApi+buildId+"/report" );
    client
      .get(this.indyPort,this.indyHost,this.indyApi + this.sealedRecordReportApi + buildId + "/report")
      .followRedirects(true)
      .timeout(TimeUnit.SECONDS.toMillis(60))
//      .basicAuthentication(indyUser, indyPass)
      .send(res -> {
        if (res.failed()) {
          handler.handle(Future.failedFuture(res.cause()));
        } else {
          if (res.result().statusCode() == 200) {
            JsonObject sealedRecord = res.result().bodyAsJsonObject();
            handler.handle(Future.succeededFuture(sealedRecord));
          } else {
            handler.handle(Future.failedFuture(res.result().bodyAsString()));
          }
        }
      });
  }

  @Override
  public void checkSharedImportInDb(String buildId, Handler<AsyncResult<Boolean>> handler) {
    String query = "SELECT id FROM reptoro.sharedimports WHERE id='" + buildId + "' allow filtering;";

      cassandraReptoroClient.execute(query , res -> {
      if(res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        ResultSet result = res.result();
        result.one(sharedImport -> {
          if(sharedImport.succeeded()) {

            Row result1 = sharedImport.result();
            if(Objects.isNull(result1)) {
              handler.handle(Future.succeededFuture(Boolean.TRUE));
            } else {
              handler.handle(Future.succeededFuture(Boolean.FALSE));
            }
          } else {
            handler.handle(Future.failedFuture(sharedImport.cause()));
          }
        });
      }
    });
  }

  @Override
  public void getSharedImportContent(String path, Handler<AsyncResult<JsonObject>> handler) {
    client
      .get(indyPort,indyHost,this.indyApi + this.browseSharedImportsApi + path)
      .followRedirects(true)
      .timeout(TimeUnit.SECONDS.toMillis(60))
//      .basicAuthentication(indyUser, indyPass)
      .send(res -> {
        if (res.failed()) {
          handler.handle(Future.failedFuture(res.cause()));
        } else {
          HttpResponse<Buffer> result = res.result();
          if (res.result().statusCode() == 200) {

            JsonObject sharedImportContent = result.bodyAsJsonObject();

            MultiMap sourceHeaders = result.headers();
            HashMap<String, Object> headers = new HashMap<>();
            Iterator<Map.Entry<String, String>> iterator = sourceHeaders.iterator();
            while (iterator.hasNext()) {
              Map.Entry<String, String> next = iterator.next();
              headers.put(next.getKey(),next.getValue());
            }
            sharedImportContent.put("headers", headers);
            handler.handle(Future.succeededFuture(sharedImportContent));
          } else {
            JsonObject sharedImportBadResponse = new JsonObject();

            MultiMap sourceHeaders = result.headers();
            HashMap<String, Object> headers = new HashMap<>();
            Iterator<Map.Entry<String, String>> iterator = sourceHeaders.iterator();
            while (iterator.hasNext()) {
              Map.Entry<String, String> next = iterator.next();
              headers.put(next.getKey(),next.getValue());
            }
            sharedImportBadResponse.put("headers", headers);
            sharedImportBadResponse.put("timestamp",Instant.now());
            sharedImportBadResponse.put("result", res.result().bodyAsString());
            sharedImportBadResponse.put("badresponse" , true);
            sharedImportBadResponse.put("http", "GET");
            handler.handle(Future.succeededFuture(sharedImportBadResponse));
          }
        }
      });
  }

  @Override
  public void deleteSharedImportBuildId(String buildId, Handler<AsyncResult<JsonObject>> handler) {
    String query = String.format(DELETE_BUILDID_MARKER, buildId);
    cassandraReptoroClient.execute(query , res -> {
      if(res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        ResultSet result = res.result();
        ColumnDefinitions columnDefinitions = result.getColumnDefinitions();
        List<ColumnDefinitions.Definition> definitions = columnDefinitions.asList();

        List<String> columns =
          definitions.stream()
            .map(column -> String.valueOf(column))
            .collect(Collectors.toList());
        handler.handle(Future.succeededFuture(new JsonObject().put("buildId", buildId).put("operation","delete-marker").put("columns", columns )));
      }
    });
  }

  @Override
  public void getOriginUrlHeaders(String originUrl, Handler<AsyncResult<JsonObject>> handler) {
    URL sourceUrl = null;
    String HTTPS = "https";
    URL httpsSourceUrl = null;
    try {
      sourceUrl = new URL(originUrl);
      httpsSourceUrl = new URL(HTTPS, sourceUrl.getHost(), sourceUrl.getPort(), sourceUrl.getPath());
//          logger.info("[[CONTENT>SOURCE>URL]]: " + httpsSourceUrl.toString());
    }
    catch (Exception e) {
      JsonObject me =
        new JsonObject().put("url", sourceUrl)
          .put("originUrl", originUrl)
          .put("exception", e.getMessage())
          .put("timestamp", Instant.now())
          .put("badresponse", true)
          .put("http", "HEAD")
          .put("content", "source");
      handler.handle(Future.succeededFuture(me));
    }
    client
      .headAbs(httpsSourceUrl.toString())
      .followRedirects(true)
      .timeout(TimeUnit.SECONDS.toMillis(110))
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
            handler.handle(Future.succeededFuture(headersJson));
          }
          else {
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
            handler.handle(Future.succeededFuture(badHeadResponse));
          }
        } else {
          JsonObject failed = new JsonObject();
          failed.put("timestamp" , Instant.now());
          failed.put("success" , false);
          failed.put("cause" , res.cause().getMessage());
          handler.handle(Future.succeededFuture(failed));
        }
      });
  }

}
