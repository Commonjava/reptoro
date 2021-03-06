package com.commonjava.reptoro;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Slf4jReporter;
import com.commonjava.reptoro.common.ApiController;
import com.commonjava.reptoro.common.ReptoroConfig;
import com.commonjava.reptoro.common.Topics;
import com.commonjava.reptoro.contents.ContentProcessingVerticle;
import com.commonjava.reptoro.headers.HeadersProcessingVerticle;
import com.commonjava.reptoro.remoterepos.RemoteRepositoryService;
import com.commonjava.reptoro.remoterepos.RemoteRepositoryVerticle;
import com.commonjava.reptoro.sharedimports.SharedImportsService;
import com.commonjava.reptoro.sharedimports.SharedImportsVerticle;
import com.commonjava.reptoro.stores.BrowsedStoreVerticle;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;

import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

  static Logger logger = Logger.getLogger(Main.class.getClass().getName());

  private static String remoteRepositoryVerticleName = "repositories";
  private static String browsedStoreVerticleName = "stores";
  private static String contentProcessingVerticleName = "contents";
  private static String headersProcessingVerticleName = "headers";
  private static String sharedImportsVerticleName = "sharedimports";
  private static String apiControllerVerticleName = "reptoroapicontroller";

  private static Vertx vertx;
  private static RemoteRepositoryService repoService;
  private static SharedImportsService sharedImportsService;

  public static void main(String[] args) {

    System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");

    // Initialize metric registry
    String registryName = "registry";
    MetricRegistry registry = SharedMetricRegistries.getOrCreate(registryName);
    SharedMetricRegistries.setDefault(registryName);

    Slf4jReporter reporter = Slf4jReporter.forRegistry(registry)
        .outputTo(LoggerFactory.getLogger(Main.class))
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .build();
    reporter.start(1, TimeUnit.MINUTES);

    // Initialize vertx with the metric registry
    DropwizardMetricsOptions metricsOptions = new DropwizardMetricsOptions()
        .setEnabled(true)
        .setMetricRegistry(registry);

    VertxOptions options = new VertxOptions();

    // Dropwizard Metrics options
    options.setMetricsOptions(metricsOptions);

    // check for blocked threads every 10s
    options.setBlockedThreadCheckInterval(10);
    options.setBlockedThreadCheckIntervalUnit(TimeUnit.SECONDS);

    // warn if an event loop thread handler took more than 10s to execute
    options.setMaxEventLoopExecuteTime(10);
    options.setMaxEventLoopExecuteTimeUnit(TimeUnit.SECONDS);

    // warn if an worker thread handler took more than 120s to execute
    options.setMaxWorkerExecuteTime(120);
    options.setMaxWorkerExecuteTimeUnit(TimeUnit.SECONDS);

    // log the stack trace if an event loop or worker handler took more than 30s to execute
    options.setWarningExceptionTime(30);
    options.setWarningExceptionTimeUnit(TimeUnit.SECONDS);

    // metrics
    options.setMetricsOptions(new DropwizardMetricsOptions().setEnabled(true).setJmxEnabled(false));

    vertx = Vertx.vertx(options);
    EventBus eb = vertx.eventBus();

    vertx.exceptionHandler(res -> {
      String message = res.getMessage();

      JsonObject vertxException = new JsonObject()
        .put("reason", "vertx_exception")
        .put("message", message)
        .put("time", Instant.now());
      logger.info("==================\n" + vertxException.encodePrettily() + "\n=====================");
      // publish to client:
      vertx.eventBus().publish(Topics.CLIENT_TOPIC,new JsonObject().put("msg", vertxException ));
    });

    ConfigRetrieverOptions configRetrivierOptions =
      new ReptoroConfig(vertx).defaultConfigOptions();

    ConfigRetriever retriever = ConfigRetriever.create(vertx, configRetrivierOptions);
    retriever.getConfig(res -> {

      if (res.succeeded()) {

        Future apiVerticleFuture = Future.future(promise -> {
          ApiController apiVerticle = new ApiController();
          DeploymentOptions apiControllerOptions = new DeploymentOptions().setWorker(true).setConfig(res.result());

          vertx.deployVerticle(apiVerticle, apiControllerOptions,
            ar -> ar.map(id -> handleSucessfullDeployment(apiControllerVerticleName, id, promise))
              .otherwise(t -> handleFailedDeployment(apiControllerVerticleName, t, promise))
          );
        });

        Future repoVerticleFuture = Future.future(promise -> {
          RemoteRepositoryVerticle repositoryVerticle = new RemoteRepositoryVerticle();
          DeploymentOptions repositoriesOptions = new DeploymentOptions().setWorker(true).setConfig(res.result());

          vertx.deployVerticle(repositoryVerticle, repositoriesOptions,
            ar -> ar.map(id -> handleSucessfullDeployment(remoteRepositoryVerticleName, id, promise))
              .otherwise(t -> handleFailedDeployment(remoteRepositoryVerticleName, t, promise))
          );
        });

//        Future browsedVerticleFuture = Future.future(promise -> {
//          BrowsedStoreVerticle browsedStoreVerticle = new BrowsedStoreVerticle();
//          DeploymentOptions browsedStoreOptions = new DeploymentOptions().setWorker(true).setConfig(res.result());
//
//          vertx.deployVerticle(browsedStoreVerticle, browsedStoreOptions,
//            ar -> ar.map(id -> handleSucessfullDeployment(browsedStoreVerticleName, id, promise))
//              .otherwise(t -> handleFailedDeployment(browsedStoreVerticleName, t, promise))
//          );
//        });

        Future contentVerticleFuture = Future.future(promise -> {
          ContentProcessingVerticle contentRepositoryVerticle = new ContentProcessingVerticle();
          DeploymentOptions contentOptions = new DeploymentOptions().setConfig(res.result());

          vertx.deployVerticle(contentRepositoryVerticle, contentOptions,
            ar -> ar.map(id -> handleSucessfullDeployment(contentProcessingVerticleName, id, promise))
              .otherwise(t -> handleFailedDeployment(contentProcessingVerticleName, t, promise))
          );
        });

        Future headersVerticleFuture = Future.future(promise -> {
          HeadersProcessingVerticle headersProcessingVerticle = new HeadersProcessingVerticle();
          DeploymentOptions headersOptions = new DeploymentOptions().setConfig(res.result());

          vertx.deployVerticle(headersProcessingVerticle, headersOptions,
            ar -> ar.map(id -> handleSucessfullDeployment(headersProcessingVerticleName, id, promise))
              .otherwise(t -> handleFailedDeployment(headersProcessingVerticleName, t, promise))
          );
        });

        Future sharedImportsVerticleFuture = Future.future(promise -> {
          SharedImportsVerticle sharedImportsVerticle = new SharedImportsVerticle();
          DeploymentOptions sharedImportsOptions = new DeploymentOptions().setConfig(res.result());

          vertx.deployVerticle(sharedImportsVerticle, sharedImportsOptions,
            ar -> ar.map(id -> handleSucessfullDeployment(sharedImportsVerticleName, id, promise))
              .otherwise(t -> handleFailedDeployment(sharedImportsVerticleName, t, promise))
          );
        });

        // Create CompositeFuture to wait for verticles to start
        CompositeFuture.join(
          apiVerticleFuture,
          repoVerticleFuture,
          contentVerticleFuture,
          headersVerticleFuture,
          sharedImportsVerticleFuture
        )
          .onSuccess(ar -> handleApplicationStartedSucess(ar))
          .onFailure(t -> handleApplicationStartedFailure(t));


        //******************************** REPLICATE REPTORO START ***********************************
        // TEST START REMOTE MAVEN REPOS...
//        vertx.setTimer(TimeUnit.SECONDS.toMillis(10), ar -> {
//          eb.send(Topics.REPO_FETCH, new JsonObject().put("cmd", "start").put("validate", "remote-repos").put("packageType", "maven"));
//        });

        // TEST START SHARED IMPORTS PROCESS...
//        vertx.setTimer(TimeUnit.SECONDS.toMillis(10), ar -> {
//          eb.send(Topics.SHARED_FETCH, new JsonObject().put("cmd", "start").put("validate", "shared-imports").put("type", "sealed"));
//        });

        //********* END OF TESTING SHARED IMPORTS AND REMOTE REPOSITORY VALIDATION **********************
      }
    });

  }

  private static Void handleFailedDeployment(String verticleName, Throwable t, Promise promise) {
    logger.log(Level.INFO, "--- Verticle {0} failed to deployed: {1} ---", new Object[]{verticleName, t});
    promise.complete();
    return null;
  }

  private static Void handleSucessfullDeployment(String verticleName, String id, Promise promise) {
    logger.log(Level.INFO, "--- Verticle {0} deployed: {1} ---", new Object[]{verticleName, id});
    promise.complete();
    return null;
  }

  private static Void handleApplicationStartedSucess(CompositeFuture future) {
    logger.info("===< Application has started, " + System.getenv("HOSTNAME").toUpperCase() + " >===");

    // Create Keyspace and Create Table in Cassandra DB!
    repoService = RemoteRepositoryService.createProxy(vertx, "repo.service");
    sharedImportsService = SharedImportsService.createProxy(vertx, "shared.imports.service");

    // Create Reptoro Remote Repositories Keyspace (If not exists) ...
    repoService.createReptoroRepositoriesKeyspace(res -> {
      if (res.succeeded()) {
        logger.info("\t\t\t\t\t[[KEYSPACE>REPTORO>SUCESS]] " + res.result());
      } else {
        logger.info("[[KEYSPACE>REPTORO>FAILED]] " + res.cause());
      }
    });
    // Create Reptoro Remote Repositories Table (If not exists) ...
    repoService.creteReptoroRepositoriesTable(res -> {
      if (res.succeeded()) {
        logger.info("\t\t\t\t\t[[TABLE>REPOSITORIES>SUCESS]] " + res.result());
      } else {
        logger.info("[[TABLE>REPOSITORIES>FAILURE]] " + res.cause());
      }
    });

    // Create Reptoro Contents Repositories Table (If not exists) ...
    repoService.creteReptoroContentsTable(res -> {
      if (res.succeeded()) {
        logger.info("\t\t\t\t\t[[TABLE>CONTENTS>SUCESS]] " + res.result());
      } else {
        logger.info("[[TABLE>CONTENTS>FAILURE]] " + res.cause());
      }
    });
    // Create Reptoro SharedImports Table (If not exists) ...
    sharedImportsService.createTableSharedImports(res -> {
      if (res.failed()) {
        logger.info("[[TABLE>SHAREDIMPORTS>FAILURE]] " + res.cause());
      } else {
        logger.info("\t\t\t\t\t[[TABLE>SHAREDIMPORTS>SUCESS]] " + res.result());
      }
    });

    return null;
  }

  private static Void handleApplicationStartedFailure(Throwable value) {
    logger.log(Level.INFO, "Application failed to start: {0}", value.getCause());
    return null;
  }
}
