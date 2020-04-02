package com.commonjava.reptoro.sharedimports;

import com.commonjava.reptoro.common.Topics;
import com.commonjava.reptoro.remoterepos.RemoteRepository;
import io.vertx.cassandra.Mapper;
import io.vertx.cassandra.MappingManager;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ProcessingSharedImportsVerticle extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());

  private io.vertx.cassandra.CassandraClient cassandraClient;
  private SharedImportsService sharedImportsService;
  private Mapper<SharedImport> sharedImportMapper;


  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    this.sharedImportsService = SharedImportsService.createProxy(vertx,"shared.imports.service");
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

    vertx.eventBus().<JsonObject>consumer(Topics.SHARED_IMPORTS_START , this::handleProcessingSharedImports);

  }

  private void handleProcessingSharedImports(Message<JsonObject> jsonObjectMessage) {
    sharedImportsService.getAllSealedTrackingRecords(res -> {
      if(res.failed()) {
        logger.info("[[SEALED.RECORDS.NOT.AVALABLE]] " + res.cause() );
      } else {
        JsonObject records = res.result();
        JsonArray sealedRecords = records.getJsonArray("sealed");

        sealedRecords.stream()
          .map(record -> String.valueOf(record))
          .map(record -> getSealedRecordReport(record).compose(this::publishRecord))
          .collect(Collectors.toList());
      }
    });
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
