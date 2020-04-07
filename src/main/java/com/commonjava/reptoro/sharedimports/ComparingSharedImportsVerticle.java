package com.commonjava.reptoro.sharedimports;

import com.commonjava.reptoro.common.Topics;
import io.vertx.cassandra.Mapper;
import io.vertx.cassandra.MappingManager;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ComparingSharedImportsVerticle extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());

  private JsonObject config;

  private io.vertx.cassandra.CassandraClient cassandraClient;
  private SharedImportsService sharedImportsService;
  private Mapper<SharedImport> sharedImportMapper;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    this.config = vertx.getOrCreateContext().config();
    this.sharedImportsService = SharedImportsService.createProxy(vertx,"shared.imports.service");
    this.cassandraClient = new com.commonjava.reptoro.common.CassandraClient(vertx,config()).getCassandraReptoroClientInstance();
    MappingManager mappingManagerRepos = MappingManager.create(this.cassandraClient);
    this.sharedImportMapper = mappingManagerRepos.mapper(SharedImport.class);
  }

  @Override
  public void start() throws Exception {

    vertx.eventBus().consumer(Topics.PROCESS_SHAREDIMPORT_REPORT , this::handleProcessingSharedImportReports);

    vertx.eventBus().consumer(Topics.SHARED_IMPORTS_BAD_PATH , this::handleSharedImportPathDoesntMatch);

    vertx.eventBus().consumer(Topics.SHARED_COMPARE_HEADERS , this::handleSharedImportCompareHeaders);

  }

  private void handleSharedImportCompareHeaders(Message<JsonObject> tMessage) {
    JsonObject body = tMessage.body();

    if(Objects.nonNull(body)) {

      changeSharedImportState(body)
        .onComplete(complete -> {
          if(complete.failed()) {
            logger.info("SAFE FAILED: " + complete.cause());
          } else {
//            logger.info("SAVE SUCESSFULL: " + complete.result());
          }
        });

//      compareSharedImportContentChecksums(body)
//        .compose(this::updateSharedImportContent)
//        .onComplete(this::handleSharedImportComplete);

    } else {
      logger.info("SHARED OBJECT MESSAGE /NULL: " + body.encodePrettily());
    }
  }

  private void handleSharedImportPathDoesntMatch(Message<JsonObject> tMessage) {
    JsonObject resultBrowsedWithDownload = tMessage.body();

  }

  private void handleProcessingSharedImportReports(Message<JsonObject> tMessage) {
    JsonObject report = tMessage.body();
    JsonObject raportKey = report.getJsonObject("key");
    String raportId = raportKey.getString("id");

    // compare shared import report downloads...
    if (report.containsKey("downloads")) {
      JsonArray downloads = report.getJsonArray("downloads");
      //TODO Filter also content paths from config and filter also only allowed content files...
      CompositeFuture.join(
        downloads.stream()
          .map(download -> new JsonObject(download.toString()))
          .filter(this::filterSslProtocols)
          .filter(this::isExceptedFilenameExtension)
          .filter(this::isAllowedContentExtensions)
          .map(download -> download.put("id",raportId))
          .map(download -> getSharedImportContent(download)
                                .compose(this::checkSharedImportContentPath)
//                                .onComplete(this::handleShareImportCompareResults)
          )
          .collect(Collectors.toList())
      ).onComplete(res -> {
        if(res.failed()) {
          logger.info("OPERATION FOR PROCESSING SHARED IMPORT DOWNLOADS FAILED: " + res.cause());
        } else {
          logger.info("OPERATION FOR PROCESSING SHARED IMPORT DOWNLOADS SUCCESSFULLY COMPLETED.\nFETCHING NEXT...");
          vertx.setTimer(TimeUnit.SECONDS.toMillis(20) , timer -> {
            vertx.eventBus().send(Topics.SHARED_GET_ONE , new JsonObject().put("type","sealed"));
          });
        }
      });
    } else {
      logger.info("THIS SEALED BUILD REPORT: " + report.getJsonObject("key") + " DOESN'T HAVE DOWNLOADS");
    }

  }

  private void handleShareImportCompareResults(AsyncResult<JsonObject> asyncResult) {
    if(Objects.nonNull(asyncResult.result())) {
      // content has match successfully in indy browsed paths...
      JsonObject resultBrowsedWithDownload = asyncResult.result();
      JsonObject download = resultBrowsedWithDownload.getJsonObject("download");

      // TODO ... or send it to SHARED_COMPARE_HEADERS topic...
      vertx.eventBus().send(Topics.SHARED_COMPARE_HEADERS,asyncResult.result());
    } else {
      // content doesn't match in browsed paths...
      logger.info("SHARED IMPORT CONTENT RESULT /NULL");
    }
  }

  private Future<JsonObject> changeSharedImportState(JsonObject browsedContent) {
    Promise<JsonObject> promise = Promise.promise();
    JsonObject download = browsedContent.getJsonObject("download");
    download.put("compared",true);
    SharedImport sharedImport = new SharedImport(download);
    sharedImportMapper.save(sharedImport , res -> {
      if(res.failed()) {
        logger.info("SAVING SHARED IMPORT FAILED: " + res.cause());
        promise.complete();
      } else {
//        logger.info("SAVING SHARED IMPORT SUCCESSFUL: " + res.result());
        promise.complete(browsedContent);
      }
    });
    return promise.future();
  }

  private Future<JsonObject> compareSharedImportContentChecksums(JsonObject download) {
    Promise<JsonObject> promise = Promise.promise();
    String originUrl = download.getString("originUrl");
    String md5 = download.getString("md5");
    String sha1 = download.getString("sha1");
    String sha256 = download.getString("sha256");

    // TODO compare local with upstream content checksums and complete...
//    logger.info("COMPARE HEADERS DOWNLOAD: \n" + download.encodePrettily());
    promise.complete();

    return promise.future();
  }

  private Future<JsonObject> updateSharedImportContent(JsonObject download) {
    Promise<JsonObject> promise = Promise.promise();
    // Update this content in content db
    promise.complete();
    return promise.future();
  }

  private void handleSharedImportComplete(AsyncResult<JsonObject> asyncResult) {
    // handle complete result
  }

  private Future<JsonObject> checkSharedImportContentPath(JsonObject browsedContent) {
    Promise<JsonObject> promise = Promise.promise();
    if (Objects.nonNull(browsedContent)) {
      JsonArray listingUrls = browsedContent.containsKey("listingUrls") ? browsedContent.getJsonArray("listingUrls") : new JsonArray();

      if (!listingUrls.isEmpty()) {
        boolean checkContentPath =
          listingUrls.stream()
            .map(listing -> new JsonObject(listing.toString()))
            .anyMatch(
              listing -> {
                if (listing.getString("path").equalsIgnoreCase(browsedContent.getString("path"))) {
                  return true;
                }
                return false;
              }
            );
        if (checkContentPath) {
          browsedContent.put("verified", true);
          browsedContent.put("pathmatch", true);
          vertx.eventBus().send(Topics.SHARED_COMPARE_HEADERS,browsedContent);
          promise.complete(browsedContent);
        } else {
          //TODO Send Downloaded Content path ( which doesn't match ) to be future proccessed?...
          browsedContent.put("verified", true);
          browsedContent.put("pathmatch", false);
          vertx.eventBus().send(Topics.SHARED_COMPARE_HEADERS,browsedContent);
//          vertx.eventBus().send(Topics.SHARED_IMPORTS_BAD_PATH, browsedContent);
          promise.complete();
        }
      } else {
        //TODO Send Downloaded Content path ( which doesn't match ) to be future proccessed?...
        logger.info("EMPTY LISTINGS FOR PATH: " + browsedContent.getString("path") + "\nBUILD ID: " + browsedContent.getString("id"));
        vertx.eventBus().send(Topics.SHARED_COMPARE_HEADERS,browsedContent);
//        vertx.eventBus().send(Topics.SHARED_IMPORTS_BAD_PATH, browsedContent);
        promise.complete();
      }
    } else {
      promise.complete();
    }
    return promise.future();
  }

  private Future<JsonObject> getSharedImportContent(JsonObject download) {
    Promise<JsonObject> promise = Promise.promise();
    String downloadContentPath = download.getString("path");
    int separatorLastIndex = downloadContentPath.lastIndexOf("/");
    String downloadParentPath = downloadContentPath.substring(0,separatorLastIndex);
    sharedImportsService.getSharedImportContent(downloadParentPath,res -> {
      if(res.failed()) {
        //handle failed...
        logger.info("FAILED GETING BROWSED CONTENT - STORE: " + download.getString("path"));
        promise.complete();
      } else {
        // returned shared import browsed path json object with headers,download report json and content download-path string
        JsonObject result = res.result();
        result.put("download",download);
        result.put("id",download.getString("id"));
        result.put("path",downloadContentPath);
        promise.complete(result);
      }
    });
    return promise.future();
  }

  private boolean filterSslProtocols(JsonObject download) {
    return Objects.nonNull(download.getString("originUrl")) && download.getString("originUrl").split(":")[0].equalsIgnoreCase("http");
  }

  private Future<JsonObject> checkReportInDb(JsonObject sharedImportReport) {
    Promise<JsonObject> promise = Promise.promise();
    if(Objects.nonNull(sharedImportReport)) {
      List<Object> objects = Arrays.asList(sharedImportReport.getString("id"), sharedImportReport.getString("storeKey"), sharedImportReport.getString("path"));
      sharedImportMapper.get(objects , res -> {
        if(res.failed()) {
          logger.info(">>> Geting Record for Shared Report: " + sharedImportReport.getString("id") + " Failed");
        } else {
          if(Objects.isNull(res.result())) {
            promise.complete(sharedImportReport);
          } else {
            promise.complete();
          }
        }
      });
    } else {
      promise.complete();
    }
    return promise.future();
  }

  private Future<JsonObject> storeReportInDb(JsonObject sharedImportReport) {
    Promise<JsonObject> promise = Promise.promise();
    if(Objects.nonNull(sharedImportReport)) {
      SharedImport sharedImport = new SharedImport(sharedImportReport);
      sharedImportMapper.save(sharedImport , res -> {
        if(res.failed()) {
          promise.complete();
        } else {
          promise.complete(sharedImportReport);
        }
      });
    } else {
      promise.complete();
    }
    return promise.future();
  }

  // [".zip",".jar",".tar",".txt",".war",".ear",".xml"]
  private Boolean isAllowedContentExtensions(JsonObject download) {
    String filename = download.getString("path");
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
  private Boolean isExceptedFilenameExtension(JsonObject download) {
    JsonArray exceptedFileExtensions = this.config.getJsonObject("reptoro").getJsonArray("except.filename.extensions");
    List<String> exceptedFileExtList =
      exceptedFileExtensions.stream()
        .map(fileExtension -> String.valueOf(fileExtension))
        .collect(Collectors.toList());
    String filePath = download.getString("path");
    String filename = filePath.substring(filePath.lastIndexOf("."),filePath.length());
    for (String ext : exceptedFileExtList) {
      if(ext.equalsIgnoreCase(filename) || filename.endsWith(ext)) {
        return false;
      }
    }
    return true;
  }


  @Override
  public void stop() throws Exception {
    super.stop();
  }
}
