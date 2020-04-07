package com.commonjava.reptoro.sharedimports;

import com.commonjava.reptoro.common.RepoStage;
import com.datastax.driver.core.Row;
import io.vertx.cassandra.*;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * "id" -> "build_perftest-atlas-20200228T215436"
 *
 * "storeKey": "maven:remote:central",
 * "accessChannel": "NATIVE",
 * "path": "/com/fasterxml/jackson/core/jackson-annotations/2.9.7/jackson-annotations-2.9.7.jar",
 * "originUrl": "http://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.9.7/jackson-annotations-2.9.7.jar",
 * "localUrl": "http://indy-admin-stage.psi.redhat.com/api/content/maven/remote/central/com/fasterxml/jackson/core/jackson-annotations/2.9.7/jackson-annotations-2.9.7.jar",
 * "md5": "016df80972ff1f747768d46e3b933893",
 * "sha256": "8bf8c224e9205f77a0e239e96e473bdb263772db4ab85ecd1810e14c04132c5e",
 * "sha1": "4b838e5c4fc17ac02f3293e9a558bb781a51c46d",
 * "size": 66981,
 * "timestamps": [
 * 1582927113514
 * ]
 *
 *
 */
public class SharedImportsServiceImpl implements SharedImportsService {


  Logger logger = Logger.getLogger(this.getClass().getName());

  private Vertx vertx;
  private WebClient client;
  private JsonObject config;

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

  private final String CREATE_REPTORO_IMPORTS_TABLE = "CREATE TABLE IF NOT EXISTS reptoro.sharedimports(ID text,STOREKEY text,ACCESSCHANNEL text,PATH text,ORIGINURL text,LOCALURL text,MD5 text,SHA256 text,SHA1 text,COMPARED boolean,PRIMARY KEY((ID,STOREKEY),PATH));";


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

    cassandraClientOptions
      .setKeyspace(reptoroKeyspace)
      .dataStaxClusterBuilder()
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
    String query = "SELECT * FROM reptoro.sharedimports;";

    cassandraReptoroClient.execute(query,res -> {
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
            if(!compared && originUrl.isEmpty() && localUrl.isEmpty()) {
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
                .put("compared",compared);
              handler.handle(Future.succeededFuture(repoResult));
            }
          }
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
  public void checkSharedImportInDb(String buildId, Handler<AsyncResult<JsonObject>> handler) {
    String query = "SELECT id FROM reptoro.sharedimports WHERE id='" + buildId + "' allow filtering;";

      cassandraReptoroClient.execute(query , res -> {
      if(res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        ResultSet result = res.result();
        JsonObject sharedImportJson = new JsonObject();
        result.one(sharedImport -> {
          if(sharedImport.succeeded()) {

            Row result1 = sharedImport.result();
            if(Objects.isNull(result1)) {
              handler.handle(Future.succeededFuture(sharedImportJson.put("id","")));
            } else {
              String sharedImportId = result1.getString("id");
              if(Objects.nonNull(sharedImportId) || sharedImportId.equalsIgnoreCase("")) {
                sharedImportJson.put("id",result1.getString("id"));
                handler.handle(Future.succeededFuture(sharedImportJson));
              } else {
                handler.handle(Future.succeededFuture(sharedImportJson.put("id","")));
              }
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
    String query = "DELETE FROM reptoro.sharedimports WHERE id='" + buildId + "' AND storekey='' AND path=''";
    cassandraReptoroClient.execute(query , res -> {
      if(res.failed()) {
        handler.handle(Future.failedFuture(res.cause()));
      } else {
        handler.handle(Future.succeededFuture(new JsonObject().put("operation","ok")));
      }
    });
  }


}
