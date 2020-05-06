package com.commonjava.reptoro.sharedimports;

import com.commonjava.reptoro.common.Const;
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
import java.util.Set;
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

    // publish to client:
    vertx.eventBus().publish(Topics.CLIENT_TOPIC,new JsonObject().put("msg",body));

    if(Objects.nonNull(body)) {
      JsonObject download = body.getJsonObject("download");

      // get originUrl headers with checksums
      getOriginUrlHeaders(download)
        // compare local checksums with origin checksums
        .compose(this::compareChecksums)
        // update download record in db
        .compose(this::changeSharedImportState)
        .onComplete(this::handleCompleteCompareChecksums);


//      compareSharedImportContentChecksums(body)
//        .compose(this::updateSharedImportContent)
//        .onComplete(this::handleSharedImportComplete);

    } else {
      logger.info("SHARED OBJECT MESSAGE /NULL: " + body.encodePrettily());
    }
  }

  private void handleSharedImportPathDoesntMatch(Message<JsonObject> tMessage) {
    JsonObject resultBrowsedWithDownload = tMessage.body();
    // TODO handle all downloads which are not present in indy browsed paths
  }

  private void handleProcessingSharedImportReports(Message<JsonObject> tMessage) {
    JsonObject report = tMessage.body();
    JsonObject raportKey = report.getJsonObject("key");
    String raportId = raportKey.getString("id");

    // compare shared import report downloads...
    if (report.containsKey("downloads")) {
      JsonArray downloads = report.getJsonArray("downloads");
      CompositeFuture.join(
        downloads.stream()
          .map(download -> new JsonObject(download.toString()))
          .filter(download -> !download.getString("accessChannel").equalsIgnoreCase("GENERIC_PROXY"))
          .filter(download -> download.containsKey("originUrl") && !download.getString("originUrl").isEmpty())
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
            // publish to client:
            CompositeFuture result = res.result();
            List<Object> resultList = result.list();
            vertx.eventBus().publish(Topics.CLIENT_TOPIC,new JsonObject().put("operation", "success").put("msg", resultList.toString()));
            // get next one...
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

  private Future<JsonObject> changeSharedImportState(JsonObject download) {
    Promise<JsonObject> promise = Promise.promise();
//    JsonObject download = browsedContent.getJsonObject("download");
    download.put("compared",true);
    SharedImport sharedImport = new SharedImport(download);
    sharedImportMapper.save(sharedImport , res -> {
      if(res.failed()) {
        logger.info("SAVING SHARED IMPORT FAILED: " + res.cause());
        promise.complete();
      } else {
//        logger.info("SAVING SHARED IMPORT SUCCESSFUL: " + res.result());
        promise.complete(download);
      }
    });
    return promise.future();
  }

  private Future<JsonObject> compareSharedImportContentChecksums(JsonObject browsedContent) {
    Promise<JsonObject> promise = Promise.promise();
    JsonObject download = browsedContent.getJsonObject("download");




    return promise.future();
  }

  private void handleCompleteCompareChecksums(AsyncResult<JsonObject> asyncResult) {
    if(asyncResult.failed()) {
      logger.info("OPERATION COMPARING SHARED IMPORT HEADERS FAILED: " + asyncResult.cause());
    } else {
      // successful compare and update...

      //send to client..
      vertx.eventBus().publish(Topics.CLIENT_TOPIC,new JsonObject().put("operation", "success").put("msg", asyncResult.result()));
    }
  }

  private Future<JsonObject> compareChecksums(JsonObject download) {
    Promise<JsonObject> promise = Promise.promise();
    if(Objects.nonNull(download)) {
      // TODO Implement compare checksums logic...
      String localMd5 = download.getString("md5");
      String localSha1 = download.getString("sha1");
      String localSha256 = download.getString("sha256");

      String originMd5 = "";
      String originSha1 = "";
      String originSha256 = "";
      String etagChecksum = "";

      Boolean compareChecksum = false;

      JsonObject sourceHeaders = download.getJsonObject(Const.SOURCEHEADERS);
      Set<String> keys = sourceHeaders.fieldNames();
      for(String key : keys) {
        if(key.toLowerCase().contains("md5")) {
          originMd5 = sourceHeaders.getString(key);
        } else if(key.toLowerCase().contains("sha1")) {
          originSha1 = sourceHeaders.getString(key);
        } else if(key.toLowerCase().contains("sha256")) {
          originSha256 = sourceHeaders.getString(key);
        } else if (key.toLowerCase().contains("etag")) {
          etagChecksum = sourceHeaders.getString(key);
        }
      }

      if(!originMd5.isEmpty()) {
        compareChecksum = originMd5.equalsIgnoreCase(localMd5);
        download.put("checksum",compareChecksum);
        promise.complete(download);
      } else if(!originSha1.isEmpty()) {
        compareChecksum = originSha1.equalsIgnoreCase(localSha1);
        download.put("checksum",compareChecksum);
        promise.complete(download);
      } else if(!originSha256.isEmpty()) {
        compareChecksum = originSha256.equalsIgnoreCase(localSha256);
        download.put("checksum",compareChecksum);
        promise.complete(download);
      } else {
        // ETAG with checksum inside value string...
        if(!etagChecksum.isEmpty()) {
          if(etagChecksum.toLowerCase().contains("md5")) {
            // compare localMd5 with inner md5 checksum
            if(etagChecksum.contains(localMd5)) {
              download.put("checksum",true);
              promise.complete(download);
            } else {
              download.put("checksum",false);
              promise.complete(download);
            }
          } else if(etagChecksum.toLowerCase().contains("sha1")) {
            // compare localSha1 with inner sha1 checksum
            if(etagChecksum.contains(localSha1)) {
              download.put("checksum",true);
              promise.complete(download);
            } else {
              download.put("checksum",false);
              promise.complete(download);
            }
          } else {
            // can be sha256??? but for now return false
            if(etagChecksum.toLowerCase().contains("sha256")) {
              logger.info("SHA256 FOR " + download.getString("originUrl"));
              download.put("checksum",false);
              promise.complete(download);
            } else {
              download.put("checksum",false);
              promise.complete(download);
            }
          }
        } else {
          download.put("checksum",compareChecksum);
          promise.complete(download);
        }
      }
    } else {
      promise.complete();
    }
    return promise.future();
  }

  private Future<JsonObject> getOriginUrlHeaders(JsonObject download) {
    Promise<JsonObject> promise = Promise.promise();
    String originUrl = download.getString("originUrl");
    sharedImportsService.getOriginUrlHeaders(originUrl,res -> {
      if(res.failed()) {
        logger.info("FAILED FETCHING SOURCE HEADERS FOR SHARED IMPORT PATH: \n" + download.getString("originUrl"));
        promise.complete();
      } else {
        JsonObject headers = res.result();
        download.put(Const.SOURCEHEADERS , headers);
        promise.complete(download);
      }
    });
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
      JsonArray listingUrls =
        browsedContent.containsKey("listingUrls") ? browsedContent.getJsonArray("listingUrls") : new JsonArray();
      JsonObject download = browsedContent.getJsonObject("download");

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
          download.put("verified", true);
          download.put("pathmatch", true);
          vertx.eventBus().send(Topics.SHARED_COMPARE_HEADERS,browsedContent);
          promise.complete(browsedContent);
        } else {
          //TODO Send Downloaded Content path ( which doesn't match ) to be future proccessed?...
          download.put("verified", true);
          download.put("pathmatch", false);
          vertx.eventBus().send(Topics.SHARED_COMPARE_HEADERS,browsedContent);
//          vertx.eventBus().send(Topics.SHARED_IMPORTS_BAD_PATH, browsedContent);
          promise.complete();
        }
      } else {
        //TODO Send Downloaded Content path ( which doesn't match ) to be future proccessed?...
        download.put("verified", true);
        download.put("pathmatch", false);
        logger.info("EMPTY LISTINGS FOR PATH: " + browsedContent.getString("path") +
                          "\nBUILD ID: " + browsedContent.getString("id"));
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
    cassandraClient.close();
  }
}
