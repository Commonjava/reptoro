package com.commonjava.reptoro.sharedimports;

import com.commonjava.reptoro.common.Const;
import com.commonjava.reptoro.common.Topics;
import io.vertx.cassandra.Mapper;
import io.vertx.cassandra.MappingManager;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ProcessingSharedImportsVerticle extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());

  private io.vertx.cassandra.CassandraClient cassandraClient;
  private SharedImportsService sharedImportsService;
  private Mapper<SharedImport> sharedImportMapper;

  private Vertx vertx;
  private JsonObject config;


  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    this.vertx = vertx;
    this.config = vertx.getOrCreateContext().config();
    DeliveryOptions options = new DeliveryOptions();
    options.setSendTimeout(TimeUnit.SECONDS.toMillis(90));
    this.sharedImportsService = SharedImportsService.createProxyWithOptions(vertx, Const.SHARED_IMPORTS_SERVICE, options);
    this.cassandraClient = new com.commonjava.reptoro.common.CassandraClient(vertx,config()).getCassandraReptoroClientInstance();
    MappingManager mappingManagerRepos = MappingManager.create(this.cassandraClient);
    this.sharedImportMapper = mappingManagerRepos.mapper(SharedImport.class);
  }

  @Override
  public void stop() throws Exception {
    super.stop();
    this.cassandraClient.close(res -> {
      if(res.failed()) {
        logger.info("FAILING CLOSING CASSANDRA CLIENT!");
      } else {
        logger.info("CASSANDRA CLIENT CLOSED!");
      }
    });
  }

  @Override
  public void start() throws Exception {

    vertx.eventBus().<JsonObject>consumer(Topics.SHARED_FETCH , this::handleSharedImportsFetchAll);

    vertx.eventBus().<JsonObject>consumer(Topics.SHARED_GET_ONE , this::handleSharedImportsGetOne);

    vertx.eventBus().<JsonObject>consumer(Topics.SHARED_START , this::handleProcessingSharedImports);

  }

  private void handleSharedImportsFetchAll(Message<JsonObject> jsonObjectMessage) {
    JsonObject cmd = jsonObjectMessage.body();

    // publish to client:
    vertx.eventBus().publish(Topics.CLIENT_TOPIC,new JsonObject().put("msg",cmd));

    logger.info("RECEIVED START VALIDATE SHARED IMPORTS| COMMAND: \n" + cmd.encodePrettily());
    // TODO different commands and different sealed records, maybe?
    logger.info("==============<FETCHING SEALED SHARED IMPORTS FROM INDY>================");
    // get all sealed records ID's
    sharedImportsService.getAllSealedTrackingRecords(res -> {
      if(res.failed()) {
        logger.info("[[SEALED.RECORDS.NOT.AVALABLE]] " + res.cause() );
      } else {
        JsonObject records = res.result();
        JsonArray sealedRecords = records.getJsonArray("sealed");

        // TODO SEPERATE PROCESSING OF SEALED RECORDS FROM SEALED BUILDS
        CompositeFuture.join(
          sealedRecords.stream()
            .map(sealed -> String.valueOf(sealed))
            .filter(this::filterTestPerformanceBuilds)
            .map(sealed -> checkSealedRecordInDb(sealed).compose(this::writeSealedRecordInDb))
            .collect(Collectors.toList())
        ).onComplete(complete -> {
          if(complete.failed()) {
            logger.info("FAILED OPERATION FOR SEALED RECORDS: " + complete.cause());
          } else {
            // TODO Send json on SHARED_GET_ONE to get one sealed record from db which is not "complete" and start processing it...
            logger.info("OPERATION FOR SEALED RECORDS HAS FINISH. SIZE: " + complete.result().size());

            vertx.eventBus().send(Topics.SHARED_GET_ONE , new JsonObject().put("type","sealed"));

          }
        });
      }
    });

  }

  private boolean filterTestPerformanceBuilds(String buildId) {
    JsonArray exceptedBuildIds = config().getJsonObject("reptoro").getJsonArray("except.shared.import.builds.ids");
    return exceptedBuildIds.stream()
      .map(buildSubstr -> String.valueOf(buildSubstr))
      .anyMatch(buildSubstr -> !buildId.startsWith(buildSubstr));
  }

  private void handleSharedImportsGetOne(Message<JsonObject> jsonObjectMessage) {
    JsonObject sealedType = jsonObjectMessage.body();

    // publish to client:
    vertx.eventBus().publish(Topics.CLIENT_TOPIC,new JsonObject().put("msg", sealedType));

    if(sealedType.getString("type").equalsIgnoreCase("sealed")) {

      sharedImportsService.getOneSealedRecord(res -> {
        if(res.failed()) {
          JsonObject dbCause =
            new JsonObject()
            .put("cause", res.cause().getMessage())
            .put("timestamp", Instant.now());
          logger.info(dbCause.encodePrettily());
        } else {
          JsonObject sharedImport = res.result();
          sharedImportsService.deleteSharedImportBuildId(sharedImport.getString("id") , id -> {
//          sharedImportMapper.delete(Collections.singletonList(sharedImport.getString("id")), id -> {
            if(id.failed()) {
              logger.info("SHARED IMPORT MARKER BUILD-ID FIELD IS NOT DELETED! " + id.cause());
            } else {
              vertx.eventBus().send(Topics.SHARED_START , sharedImport);
              logger.info("SHARED IMPORT MARKER BUILD-ID FIELD DELETED\nOPERATION RESULT: " + id.result().encodePrettily());
            }
          });
        }
      });
    } else {
      logger.info("NOT IMPLEMENTED YET FOR TYPE: " + sealedType.getString("type"));
    }

  }

  private Future<String> checkSealedRecordInDb(String sealedRecord) {
    Promise<String> promise = Promise.promise();

    sharedImportsService.checkSharedImportInDb(sealedRecord , res -> {
      if(res.failed()) {
        logger.info("PROBLEM RETRIEVING RECORD FROM DB: " + res.cause());
      } else {
        Boolean result = res.result();
        if(Objects.isNull(result) || result) {
          // record is not in db
          promise.complete(sealedRecord);
        } else {
          //record is in db
          promise.complete();
        }
      }
    });

//    sharedImportMapper.get(Collections.singletonList(sealedRecord), res -> {
//      if(res.failed()) {
//        logger.info("FAILED GET RECORD FROM DB OPERATION: " + res.cause());
//        promise.complete();
//      } else {
//        if(Objects.isNull(res.result())) {
//          promise.complete(sealedRecord);
//        } else {
//          promise.complete();
//        }
//      }
//    });
    return promise.future();
  }

  private Future<String> writeSealedRecordInDb(String sealedRecord) {
    Promise<String> promise = Promise.promise();
    if(Objects.isNull(sealedRecord)) {
      promise.complete();
    } else {
      JsonObject sealedRecordJson = new JsonObject().put("id", sealedRecord);
      sharedImportMapper.save(new SharedImport(sealedRecordJson) , res -> {
        if(res.failed()) {
          logger.info("FAILED SAVE RECORD TO DB OPERATION: " + res.cause());
          promise.complete();
        } else {
          promise.complete(sealedRecord);
        }
      });
    }
    return promise.future();
  }

  private void handleProcessingSharedImports(Message<JsonObject> jsonObjectMessage) {
    JsonObject sharedImport = jsonObjectMessage.body();

    // publish to client:
    vertx.eventBus().publish(Topics.CLIENT_TOPIC,new JsonObject().put("msg", sharedImport));

    // respond to message sender
    jsonObjectMessage.reply(new JsonObject().put("operation", "success").put("timestamp", Instant.now()));

    // get sealed record report with downloads json array
    sharedImportsService.getSealedRecordRaport(sharedImport.getString("id") , res -> {
      if(res.failed()) {
        logger.info("[[SEALED.RECORD.REPORT.UNAVAILABLE]] " + res.cause());
        // publish to client:
        vertx.eventBus().publish(Topics.CLIENT_TOPIC,new JsonObject().put("fail", res.cause()));
      } else {
        JsonObject report = res.result();

        if(report.containsKey("downloads")) {
          vertx.eventBus().send(Topics.PROCESS_SHAREDIMPORT_REPORT , report);
        } else {
          logger.info("THIS SHARED IMPORT RAPORT DOESN'T HAVE DOWNLOADS: " + report.getJsonObject("key"));
          // TODO maybe just update this shared import without downloads in db??! ...
          // change "complete" to true

          sharedImport.put("compared",true);
          updateSharedImportWithoutDownloads(sharedImport)
            .onComplete(complete -> {
              vertx.eventBus().send(Topics.SHARED_GET_ONE , new JsonObject().put("type","sealed"));
            });

//          sharedImportsService.deleteSharedImportBuildId(sharedImport.getString("id") , id -> {
//            if(id.failed()) {
//              logger.info("SHARED IMPORT MARKER BUILD-ID ROW NOT DELETED: " + id.cause());
//            } else {
//              vertx.eventBus().send(Topics.SHARED_GET_ONE , new JsonObject().put("type","sealed"));
//              logger.info("SHARED IMPORT [without downloads] MARKER BUILD-ID ROW DELETED!");
//            }
//          });
        }
      }
    });
  }


  private Future<JsonObject> updateSharedImportWithoutDownloads(JsonObject sharedImport) {
    Promise<JsonObject> promise = Promise.promise();
    sharedImportMapper.save(new SharedImport(sharedImport,true) , res -> {
      if(res.failed()) {
        logger.info("FAILED UPDATING SHARED IMPORT [without downloads] IN DB: " + res.cause());
        promise.complete();
      } else {
        promise.complete(sharedImport);
      }
    });
    return promise.future();
  }

  private Future<Void> publishRecord(JsonObject report) {
    Promise<Void> promise = Promise.promise();
    if(Objects.nonNull(report)) {
      vertx.eventBus().send(Topics.PROCESS_SHAREDIMPORT_REPORT , report);
      promise.complete();
    } else {
      promise.complete();
    }
    return promise.future();
  }
  private Future<JsonObject> getSealedRecordReport(String record) {
    Promise<JsonObject> promise = Promise.promise();

    // get sealed record report with downloads json array
    sharedImportsService.getSealedRecordRaport(record , res -> {
      if(res.failed()) {
        logger.info("[[SEALED.RECORD.REPORT.UNAVAILABLE]] " + res.cause());
        promise.complete();
      } else {
        JsonObject report = res.result();
        if(report.containsKey("downloads")) {
          promise.complete(report);
        } else {
          promise.complete();
        }
      }
    });
    return promise.future();
  }


}
