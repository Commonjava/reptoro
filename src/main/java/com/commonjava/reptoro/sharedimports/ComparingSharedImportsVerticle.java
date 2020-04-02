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
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ComparingSharedImportsVerticle extends AbstractVerticle {

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
  public void start() throws Exception {

    vertx.eventBus().consumer(Topics.PROCESS_SHAREDIMPORT_REPORT , this::handleProcessingSharedImportReports);

    vertx.eventBus().consumer(Topics.SHARED_IMPORTS_BAD_PATH , this::handleSharedImportPathDoesntMatch);

  }

  private void handleSharedImportPathDoesntMatch(Message<JsonObject> tMessage) {
    JsonObject resultBrowsedWithDownload = tMessage.body();

  }

  private void handleProcessingSharedImportReports(Message<JsonObject> tMessage) {
    JsonObject report = tMessage.body();

    // compare shared import report...
    if (report.containsKey("downloads")) {
      JsonArray downloads = report.getJsonArray("downloads");
      downloads.stream()
        .map(download -> new JsonObject(download.toString()))
        .filter(this::filterSslProtocols)
        .map(download -> getSharedImportContent(download)
                              .compose(this::checkSharedImportContentPath)
                              .onComplete(this::handleShareImportCompareResults)
        )
        .collect(Collectors.toList());
    }

  }

  private void handleShareImportCompareResults(AsyncResult<JsonObject> asyncResult) {
    if(Objects.nonNull(asyncResult.result())) {
      // content has match successfuly in browsed paths...
      JsonObject resultBrowsedWithDownload = asyncResult.result();
      JsonObject download = resultBrowsedWithDownload.getJsonObject("download");

      compareSharedImportContentChecksums(download)
        .compose(this::updateSharedImportContent)
        .onComplete(this::handleSharedImportComplete);

    } else {
      // content hasn't match in browsed paths...

    }
  }

  private Future<JsonObject> compareSharedImportContentChecksums(JsonObject download) {
    Promise<JsonObject> promise = Promise.promise();
    String originUrl = download.getString("originUrl");
    String md5 = download.getString("md5");
    String sha1 = download.getString("sha1");
    String sha256 = download.getString("sha256");

    // TODO compare local with upstream content checksums and complete...
    return promise.future();
  }

  private Future<JsonObject> updateSharedImportContent(JsonObject download) {
    Promise<JsonObject> promise = Promise.promise();

    return promise.future();
  }

  private void handleSharedImportComplete(AsyncResult<JsonObject> asyncResult) {

  }

  private Future<JsonObject> checkSharedImportContentPath(JsonObject browsedContent) {
    Promise<JsonObject> promise = Promise.promise();
    if(Objects.nonNull(browsedContent)) {
      JsonArray listingUrls = browsedContent.getJsonArray("listingUrls");
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
      if(checkContentPath) {
        browsedContent.put("verified",true);
        browsedContent.put("pathmatch",true);
        promise.complete(browsedContent);
      } else {
        //TODO Send Downloaded Content path ( which doesn't match ) to be future proccessed?...
        browsedContent.put("verified",true);
        browsedContent.put("pathmatch",false);
        vertx.eventBus().send(Topics.SHARED_IMPORTS_BAD_PATH , browsedContent);
        promise.complete();
      }
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
        promise.complete();
      } else {
        // handle success...
        JsonObject result = res.result();
        result.put("download",download);
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


  @Override
  public void stop() throws Exception {
    super.stop();
  }
}
