package com.commonjava.reptoro.common;

import com.commonjava.reptoro.contents.ContentProcessingService;
import com.commonjava.reptoro.remoterepos.RemoteRepositoryService;
import com.commonjava.reptoro.sharedimports.SharedImportsService;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.impl.OAuth2TokenImpl;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.dropwizard.MetricsService;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.handler.sockjs.SockJSHandlerOptions;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static io.vertx.core.http.HttpHeaders.LOCATION;

public class ApiController extends AbstractVerticle {


  Logger logger = Logger.getLogger(this.getClass().getName());

  private static final int PORT = 8080;
  private static final String EVENTBUS_ADDRESS = "/eventbus/*";
  private static final String API_REPTORO_PROTECTED = "/reptoro/*";
  private static final String LOGIN = "/reptoro/login";
  private static final String LOGOUT = "/reptoro/logout";
  private static final String TEST = "/reptoro/test";
  private static final String STATIC_CONTENT = "/*";
  private static final String REPTORO_CALLBACK = "/callback";
  private static final String API_REPOSITORY_ALL = "/reptoro/repository";
  private static final String REDIRECT_REPTORO_INDEX_HTML = "/reptoro/index.html";
  public static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json; charset=utf-8";
  public static final String API_SHAREDIMPORTS_ALL = "/reptoro/sharedimport";
  public static final String API_PROCESS_START = "/reptoro/start/:process";
  public static final String API_PROCESS_STOP = "/reptoro/stop/:process";
  public static final String API_REPOS_NOTVALIDATED_COUNT = "/reptoro/repos/notvalidated/count";
  public static final String API_IMPORTS_NOTVALIDATED_COUNT = "/reptoro/imports/notvalidated/count";
  public static final String API_REPOS_CONTENT_COUNT = "/reptoro/repos/contents/count";
  public static final String API_REPOS_COUNT = "/reptoro/repos/count";
  public static final String API_SHAREDIMPORT_CONTENT_COUNT = "/reptoro/imports/contents/count";
  public static final String API_SHAREDIMPORT_COUNT = "/reptoro/imports/count";
  public static final String API_REPOSITORY_CHANGE = "/reptoro/repo/change";
  public static final String API_REPOSITORY_REVERSE = "/reptoro/repo/reverse";
  public static final String API_REPOSITORY_SCAN = "/reptoro/repo/scan";
  public static final String API_SHAREDIMPORT_SCAN = "/reptoro/import/scan";
  public static final String API_SHAREDIMPORT_DOWNLOADS_FROM_BUILDID = "/reptoro/import/downloads/:id";



  private OAuth2Auth oAuth2Auth;
  private JsonObject config;
  private RemoteRepositoryService remoteRepositoryService;
  private SharedImportsService sharedImportsService;
  private ContentProcessingService contentProcessingService;

  long startRepoTimer = 0;
  long startImportTimer = 0;


  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    this.remoteRepositoryService = RemoteRepositoryService.createProxy(vertx,Const.REPO_SERVICE);
    this.sharedImportsService = SharedImportsService.createProxy(vertx,Const.SHARED_IMPORTS_SERVICE);
    this.contentProcessingService = ContentProcessingService.createProxy(vertx,Const.CONTENT_SERVICE);
  }

  @Override
  public void stop() throws Exception {
    super.stop();
  }

  // API Gateway Controller Verticle for Reptoro [ handle SSO , Web Routing , SSE , Client Messaging ... ]
  @Override
  public void start() throws Exception {

    EventBus eventBus = vertx.eventBus();
    this.config = config();

    HttpServer server = vertx.createHttpServer();
    Router router = Router.router(vertx);

    SockJSHandlerOptions sockJSHandlerOptions = new SockJSHandlerOptions();
    SockJSHandler sockJSHandler = SockJSHandler.create(vertx);

    enableCorsSupport(router);

    // Store session information on the server side
    SessionStore sessionStore = LocalSessionStore.create(vertx);
    SessionHandler sessionHandler = SessionHandler.create(sessionStore);
    router.route().handler(sessionHandler);//***

    // send metrics message to the event bus
    MetricsService metricsService = MetricsService.create(vertx);
    vertx.setPeriodic(TimeUnit.SECONDS.toMillis(10), t -> {
      JsonObject metrics = metricsService.getMetricsSnapshot(vertx);
      vertx.eventBus().publish(Topics.VERTX_METRICS, metrics);
    });


    // CSRF handler setup required for logout form
//    String csrfSecret = config().getJsonObject("reptoro").getString("session.secret");
//    CSRFHandler csrfHandler = CSRFHandler.create(csrfSecret);
//    router.route("/").handler(ctx -> {
//        ctx.request().setExpectMultipart(true);
//        ctx.request().endHandler(v -> csrfHandler.handle(ctx));
//      }
//    );//***

//    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

    router.errorHandler(HttpStatus.SC_INTERNAL_SERVER_ERROR.getValue(), this::handleHttpError);
    router.errorHandler(HttpStatus.SC_UNAUTHORIZED.getValue(), this::handleHttpError);

    sockJSHandler
      .bridge(
        new BridgeOptions()
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.CLIENT_TOPIC).setMatch(new JsonObject()))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.CLIENT_TOPIC).setMatch(new JsonObject()))
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.VERTX_METRICS).setMatch(new JsonObject()))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.VERTX_METRICS).setMatch(new JsonObject()))
      );

    JsonObject keycloakConfig = config.getJsonObject("keycloak");

    logger.info(keycloakConfig.encodePrettily());

    HttpClientOptions httpClientOptions = new HttpClientOptions().setSsl(true).setVerifyHost(false).setTrustAll(true);

    oAuth2Auth = KeycloakAuth.create(vertx, OAuth2FlowType.AUTH_CODE, keycloakConfig,httpClientOptions);

    OAuth2AuthHandler oAuth2AuthHandler1 = OAuth2AuthHandler.create(oAuth2Auth);
    oAuth2AuthHandler1.setupCallback(router.get(REPTORO_CALLBACK));

    // session handler needs access to the authenticated user, otherwise we get an infinite redirect loop
    sessionHandler.setAuthProvider(oAuth2Auth);//***

//    router.route("/callback").handler(ctx -> authCallback(oAuth2Auth,buildHostURI(),ctx));

    router.route(EVENTBUS_ADDRESS).handler(sockJSHandler);

    // handle request data as json object
    router.route().handler(BodyHandler.create());

    router.route(API_REPTORO_PROTECTED).handler(oAuth2AuthHandler1);

    router.get(API_REPOSITORY_ALL).handler(this::handleGetAllRepositories);
    router.get(API_REPOS_NOTVALIDATED_COUNT).handler(this::handleNotValidatedRemoteRepositories);
    router.get(API_REPOS_CONTENT_COUNT).handler(this::handleGetRemoteRepoContentCount);
    router.get(API_REPOS_COUNT).handler(this::handleRemoteRepoCount);
    router.post(API_REPOSITORY_CHANGE).handler(this::handleRemoteRepoChange);
    router.post(API_REPOSITORY_REVERSE).handler(this::handleRemoteRepoReverse);
    router.post(API_REPOSITORY_SCAN).handler(this::handleRemoteRepoRescan);

    router.get(API_SHAREDIMPORTS_ALL).handler(this::handleGetAllSharedImports);
    router.get(API_IMPORTS_NOTVALIDATED_COUNT).handler(this::handleNotValidatedSharedImports);
    router.get(API_SHAREDIMPORT_CONTENT_COUNT).handler(this::handleSharedImportContentCount);
    router.get(API_SHAREDIMPORT_COUNT).handler(this::handleSharedImportsCount);
    router.post(API_SHAREDIMPORT_SCAN).handler(this::handleSharedImportRescan);
    router.get(API_SHAREDIMPORT_DOWNLOADS_FROM_BUILDID).handler(this::handleSharedImportDownloads);

    // start repos or imports process
    router.post(API_PROCESS_START).handler(this::handleStartProcess);
    router.post(API_PROCESS_STOP).handler(this::handleStopProcess);

    router.route(REDIRECT_REPTORO_INDEX_HTML).handler(this::handleReroutingIndex);

//    router.get("/uaa").handler(this::currentuser);
    router.route(LOGIN).handler(this::currentuser);
    router.post(LOGOUT).handler(this::handleLogoutSession);

    router.route(STATIC_CONTENT).handler(StaticHandler.create());


    router.route(TEST).handler(this::handleTest);

    server
      .requestHandler(router)
      .listen(config().getJsonObject("reptoro").getInteger("api.gateway.http.port",PORT));
  }




  private void handleSharedImportDownloads(RoutingContext context) {
    @Nullable String buildId = context.request().getParam("id");

    logger.info("REQUESTED DOWNLOADS FOR BUILD: " + buildId);


    sharedImportsService.getDownloads(buildId, res -> {
      if(res.failed()) {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("cause", res.cause()).put("results", new JsonObject()).encodePrettily());
      } else {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(res.result().encodePrettily());
      }
    });
  }

  private void handleSharedImportRescan(RoutingContext context) {
    // handle shared import rescan logic...
    @Nullable JsonObject sharedImportReq = context.getBodyAsJson();
    JsonObject sharedImport = sharedImportReq.getJsonObject("import");

    // publish / request this shared import ('import') to SHARED_START topic for processing ...
    vertx.eventBus().<JsonObject>request(Topics.SHARED_START, sharedImport, rspn -> {
      if(rspn.failed()) {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("operation", "failed").put("result", new JsonObject()).encodePrettily());
      } else {
        Message<JsonObject> result = rspn.result();
        JsonObject msgResponse = result.body();
        if(msgResponse.getString("operation").equalsIgnoreCase("success")) {
          context.response()
            .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
            .end(new JsonObject().put("operation", "success").put("result", msgResponse).encodePrettily());
        } else {
          context.response()
            .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
            .end(new JsonObject().put("operation", "failed").put("result", msgResponse).encodePrettily());
        }
      }
    });

  }

  private void handleRemoteRepoRescan(RoutingContext context) {
    // handle remote repository rescan logic...
    @Nullable JsonObject repo = context.getBodyAsJson();
    logger.info(repo.encodePrettily());

    // publish / request message on eventbus for repo start processing process...
    vertx.eventBus().request(Topics.REPO_START, repo.getJsonObject("repo"), rspn -> {
      if(rspn.failed()) {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("operation", "failed").put("result", new JsonObject()).encodePrettily());
      } else {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("operation", "success").put("result", rspn.result().body()).encodePrettily());
      }
    });

  }

  private void handleRemoteRepoReverse(RoutingContext context) {
    // handle remote repository reverse back to previous protocol logic...
    @Nullable JsonObject repo = context.getBodyAsJson();

    if(Objects.isNull(repo) || !repo.containsKey("repo")) {
      context.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
    }

    logger.info("RECIEVED REVERSE PROTOCOL REQUEST FOR: " + repo.getJsonObject("repo").getString("key"));
    final String nonSslProtocol = "http";

    OAuth2TokenImpl user = (OAuth2TokenImpl) context.user();
    String token = user.opaqueAccessToken();

    JsonObject repoChange =
      new JsonObject().put("type", repo.getJsonObject("repo").getString("type"))
        .put("key", repo.getJsonObject("repo").getString("key")).put("name", repo.getJsonObject("repo").getString("name"))
        .put("url", repo.getJsonObject("repo").getString("url")).put("token", token);

    remoteRepositoryService.changeRemoteRepositoryProtocol(repoChange, nonSslProtocol , res -> {
      if(res.failed()) {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("operation", "failed").put("result", res.cause().getMessage()).encodePrettily());
      } else {
        JsonObject opResult = res.result();
        if(opResult.getInteger("statuscode") == 200) {
          // TODO Change DB repo with new protocol

          vertx.eventBus().<JsonObject>request(Topics.REPO_UPDATE, opResult.getJsonObject("result"), rspn -> {
            if(rspn.failed()) {
              logger.info(rspn.cause().getLocalizedMessage());
              context.response()
                .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
                .end(new JsonObject().put("operation", "failure").put("result", opResult).encodePrettily());
            } else {
              Message<JsonObject> replyMsg = rspn.result();
              JsonObject reply = replyMsg.body();
              if(reply.getString("operation").equalsIgnoreCase("success")) {
                context.response()
                  .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
                  .end(new JsonObject().put("operation", "success").put("result", opResult).encodePrettily());
              } else {
                context.response()
                  .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
                  .end(new JsonObject().put("operation", "failure").put("result", opResult).encodePrettily());
              }
            }
          });
        } else {
          context.response()
            .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
            .end(new JsonObject().put("operation", "failure").put("result", opResult).encodePrettily());
        }
      }
    });
  }

  private void handleRemoteRepoChange(RoutingContext context) {
    // handle remote repository change protocol logic...
    @Nullable JsonObject repo = context.getBodyAsJson();

    if(Objects.isNull(repo) || !repo.containsKey("repo")) {
      context.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end();
    }

//    logger.info(repo.getString("eventType") + " USER: " + context.user().principal().encodePrettily());
    logger.info("RECIEVED CHANGE PROTOCOL REQUEST FOR: " + repo.getJsonObject("repo").getString("key"));
    final String sslProtocol = "https";

    OAuth2TokenImpl user = (OAuth2TokenImpl) context.user();
    String token = user.opaqueAccessToken();

    JsonObject repoChange =
      new JsonObject().put("type", repo.getJsonObject("repo").getString("type"))
        .put("key", repo.getJsonObject("repo").getString("key")).put("name", repo.getJsonObject("repo").getString("name"))
        .put("url", repo.getJsonObject("repo").getString("url")).put("token", token);

    remoteRepositoryService.changeRemoteRepositoryProtocol(repoChange, sslProtocol , res -> {
      if(res.failed()) {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("operation", "failed").put("result", res.cause().getMessage()).encodePrettily());
      } else {

        JsonObject opResult = res.result();
        if(opResult.getInteger("statuscode") == 200) {
          // TODO Change DB repo with new protocol

          vertx.eventBus().<JsonObject>request(Topics.REPO_UPDATE, opResult.getJsonObject("result"), rspn -> {
            if(rspn.failed()) {
              logger.info(rspn.cause().getLocalizedMessage());
              context.response()
                .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
                .end(new JsonObject().put("operation", "failure").put("result", opResult).encodePrettily());
            } else {
              Message<JsonObject> replyMsg = rspn.result();
              JsonObject reply = replyMsg.body();
              if(reply.getString("operation").equalsIgnoreCase("success")) {
                context.response()
                  .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
                  .end(new JsonObject().put("operation", "success").put("result", opResult).encodePrettily());
              } else {
                context.response()
                  .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
                  .end(new JsonObject().put("operation", "failure").put("result", opResult).encodePrettily());
              }
            }
          });
        } else {
          context.response()
            .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
            .end(new JsonObject().put("operation", "failure").put("result", opResult).encodePrettily());
        }
      }
    });
  }

  private void handleSharedImportsCount(RoutingContext context) {
    sharedImportsService.getSharedImportCount(res -> {
      if(res.failed()) {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("failed", res.cause().getMessage()).encodePrettily());
      } else {
        JsonArray sharedImports = res.result();
        List<Object> importList =
          sharedImports.stream()
            .distinct()
            .collect(Collectors.toList());
        JsonObject count = new JsonObject().put("count", importList.size());
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("result", count).encodePrettily());
      }
    });
  }

  private void handleRemoteRepoCount(RoutingContext context) {
    remoteRepositoryService.getRemoteRepositoryCount(res -> {
      if(res.failed()) {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("failed", res.cause().getMessage()).encodePrettily());
      } else {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("result", res.result()).encodePrettily());
      }
    });
  }

  private void handleSharedImportContentCount(RoutingContext context) {
    sharedImportsService.getSharedImportContentCount(res -> {
      if(res.failed()) {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("failed", res.cause().getMessage()).encodePrettily());
      } else {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("result", res.result()).encodePrettily());
      }
    });
  }

  private void handleGetRemoteRepoContentCount(RoutingContext context) {
    contentProcessingService.getRemoteRepositoryContentsCount(res -> {
      if(res.failed()) {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("failed", res.cause().getMessage()).encodePrettily());
      } else {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("result", res.result()).encodePrettily());
      }
    });
  }

  private void handleNotValidatedRemoteRepositories(RoutingContext context) {
    remoteRepositoryService.getAllNotComparedRemoteRepositories(res -> {
      if(res.failed()) {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("failed", res.cause().getMessage()).encodePrettily());
      } else {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("result", res.result()).encodePrettily());
      }
    });
  }

  private void handleNotValidatedSharedImports(RoutingContext context) {
    sharedImportsService.getNotComparedSharedImportsFromDB(res -> {
      if(res.failed()) {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("failed", res.cause().getMessage()).encodePrettily());
      } else {
        context.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("result", res.result()).encodePrettily());
      }
    });
  }

  private void handleStopProcess(RoutingContext context) {
    @Nullable String process = context.request().getParam("process");

    // not permited for now...
    context.response()
      .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
      .end(new JsonObject().put("start", process).put("sucess", false).put("msg","operation not permited!").encodePrettily());
  }

  private void handleStartProcess(RoutingContext context) {
    @Nullable String process = context.request().getParam("process");
    if(Objects.nonNull(process) && process.equalsIgnoreCase("repos")) {
      vertx.eventBus().send(Topics.REPO_FETCH, new JsonObject().put("cmd", "start").put("validate", "remote-repos").put("packageType", "maven"));

      startRepoTimer = vertx.setPeriodic(TimeUnit.SECONDS.toMillis(10), timer -> {
        remoteRepositoryService.getAllNotComparedRemoteRepositories(res -> {
          if(res.failed()) {
            logger.info(res.cause().getLocalizedMessage());
          } else {
            JsonObject result = res.result();
            result.put("repos", true);
            vertx.eventBus().publish(Topics.VERTX_METRICS, result);
            if(result.getInteger("count")==0) {
              vertx.cancelTimer(startRepoTimer);
            }
          }
        });
      });

      context.response()
        .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
        .end(new JsonObject().put("start", process).put("sucess", true).encodePrettily());

    } else if(Objects.nonNull(process) && process.equalsIgnoreCase("imports")) {
      vertx.eventBus().send(Topics.SHARED_FETCH, new JsonObject().put("cmd", "start").put("validate", "shared-imports").put("type", "sealed"));

      startImportTimer = vertx.setPeriodic(TimeUnit.SECONDS.toMillis(10), timer -> {
        sharedImportsService.getNotComparedSharedImportsFromDB(res -> {
          if(res.failed()) {
            logger.info(res.cause().getLocalizedMessage());
          } else {
            JsonObject result = res.result();
            result.put("imports", true);
            vertx.eventBus().publish(Topics.VERTX_METRICS, result);
            if(result.getInteger("count")==0) {
              vertx.cancelTimer(startImportTimer);
            }
          }
        });
      });

      context.response()
        .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
        .end(new JsonObject().put("start", process).put("sucess", true).encodePrettily());

    } else {
      context.response()
        .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
        .end(new JsonObject().put("start", process).put("sucess", false).put("msg","operation not permited!").encodePrettily());
    }
  }

  private void handleLogoutSession(RoutingContext rc) {
    OAuth2TokenImpl user = (OAuth2TokenImpl) rc.user();
    user.logout(logout -> {
      if(logout.failed()) {
        rc.response().end(new JsonObject().put("failed", true).put("cause", logout.cause()).encodePrettily());
      } else {
        rc.clearUser();
        rc.session().destroy();
        rc.response().setStatusCode(204).end();
      }
    });
  }

  private void handleGetAllSharedImports(RoutingContext rc) {
    sharedImportsService.getAllSharedImportsFromDb(res -> {
      if(res.failed()) {
        rc.response().end(new JsonObject().put("results",new JsonArray()).put("cause",res.cause().getMessage()).encodePrettily());
      } else {
        JsonArray sharedImportArr = res.result();
        CompositeFuture.join(
          sharedImportArr.stream()
            .map(sharedImport -> new JsonObject(sharedImport.toString()))
            .map(sharedImport -> sharedImport.getString("id"))
            .distinct()
            .map(this::fetchSharedImportDownloads)
            .collect(Collectors.toList())
        )
        .onComplete(complete -> {
          if(complete.failed()) {
            logger.info(complete.cause().getLocalizedMessage());
            rc.response()
              .putHeader(CONTENT_TYPE,APPLICATION_JSON_CHARSET_UTF_8)
              .end(new JsonObject().put("results",new JsonArray()).encodePrettily());
          } else {
            List<JsonObject> buildDownloads =
              complete.result().list()
                  .stream()
                  .map(el -> new JsonObject(el.toString()))
                  .distinct()
                  .collect(Collectors.toList());

            JsonArray buildIdDownloads = new JsonArray();
            for(JsonObject buildDownload : buildDownloads) {
              buildIdDownloads.add(buildDownload);
            }

            rc.response()
              .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
              .end(new JsonObject().put("results", buildIdDownloads).encodePrettily());
          }
        });

      }
    });
  }

  private Future<JsonObject> fetchSharedImportDownloads(String buildId) {
    Promise<JsonObject> promise = Promise.promise();
    sharedImportsService.getSharedImportDownloads(buildId , res -> {
      if(res.failed()) {
        logger.info(res.cause().getLocalizedMessage());
        JsonObject buildSumary =
          new JsonObject()
            .put("id", buildId)
            .put("checksums", false)
            .put("pathmatch", false)
            .put("count", 0)
//            .put("downloads", new JsonArray())
          ;
        promise.complete(buildSumary);
      } else {
        JsonObject result = res.result();
        JsonObject buildSumary =
          new JsonObject()
            .put("id", buildId)
            .put("checksums", result.getBoolean("checksums"))
            .put("pathmatch", result.getBoolean("pathmatch"))
            .put("count", result.getInteger("count"))
//            .put("downloads", result.getJsonArray("downloads"))
          ;
        promise.complete(buildSumary);
      }
    });
    return promise.future();
  }

  private void handleReroutingIndex(RoutingContext rc) {
    rc.response()
      .putHeader(LOCATION,"/reptoro/")
      .setStatusCode(301)
      .end();
  }

  private void handleHttpError(RoutingContext rc) {
    logger.info("===<HTTP FAILURE - CODE: " + rc.response().getStatusCode() + ">===");
    Throwable failure = rc.failure();
    if (failure != null) {
      failure.printStackTrace();
    }
  }

  public void currentuser(RoutingContext context) {
    String accessToken = KeycloakHelper.rawAccessToken(context.user().principal());
    JsonObject token = KeycloakHelper.parseToken(accessToken);

    OAuth2TokenImpl user = (OAuth2TokenImpl) context.user();
    context.setUser(user);


    context.response()
      .putHeader(CONTENT_TYPE,APPLICATION_JSON_CHARSET_UTF_8)
      .end(new JsonObject()
        .put("id",token.getString("jti"))
        .put("token",token)
        .put("username",token.getString("preferred_username"))
        .encodePrettily());
  }

  public void handleGetAllRepositories(RoutingContext rc) {
    remoteRepositoryService.getAllRemoteRepositoriesFromDb(res -> {
      if(res.failed()) {
        rc.response().end(new JsonObject().put("results",new JsonArray()).put("cause",res.cause().getMessage()).encodePrettily());
      } else {
        JsonArray repoArray = res.result();


//        List<Future> repoListFutures =
//          repoArray.stream()
//            .map(repo -> new JsonObject(repo.toString()))
//            .map(this::fetchValidationCount)
//            .collect(Collectors.toList());

        CompositeFuture.join(
          repoArray.stream()
            .map(repo -> new JsonObject(repo.toString()))
            .map(this::fetchValidationCount)
            .collect(Collectors.toList())
        )
        .onComplete(complete -> {
          if(complete.failed()) {
            logger.info(complete.cause().getLocalizedMessage());
            rc.response()
              .putHeader(CONTENT_TYPE,APPLICATION_JSON_CHARSET_UTF_8)
              .end(new JsonObject().put("results",new JsonArray()).encodePrettily());
          } else {
            List<Object> list = complete.result().list();
            List<JsonObject> repoResultList =
              list.stream().map(repo -> new JsonObject(repo.toString())).collect(Collectors.toList());
            JsonArray repos = new JsonArray();
            for(JsonObject repoJson : repoResultList) {
              repos.add(repoJson);
            }
            rc.response()
              .putHeader(CONTENT_TYPE,APPLICATION_JSON_CHARSET_UTF_8)
              .end(new JsonObject().put("results",repos).encodePrettily());
          }
        });


      }
    });
  }

  private Future<JsonObject> fetchValidationCount(JsonObject repo) {
    Promise<JsonObject> promise = Promise.promise();
    contentProcessingService.getRemoteRepositoryValidationCount(repo.getString("key"), res -> {
      if(res.failed()) {
        logger.info(res.cause().getLocalizedMessage());
        repo
          .put("contentcount", 0)
          .put("matched", 0)
          .put("notmatched", 0)
        .put("compared",0);
        promise.complete(repo);
      } else {
        JsonObject result = res.result();
        repo
          .put("contentcount", result.getInteger("count"))
          .put("matched", result.getInteger("matched"))
          .put("notmatched", result.getInteger("notmatched"))
          .put("compared", result.getInteger("compared"));
        promise.complete(repo);
      }
    });
    return promise.future();
  }

  private String buildHostURI() {
    return "http://reptoro-newcastle-stage.cloud.paas.psi.redhat.com/reptoro/index.html";
  }

  private void authCallback(OAuth2Auth oauth2, String hostURL, RoutingContext context) {
    final String code = context.request().getParam("code");
    // code is a require value
    if (code == null) {
      context.fail(400);
      return;
    }
    final String redirectTo = context.request().getParam("redirect_uri");
    final String redirectURI = hostURL + context.currentRoute().getPath() + "?redirect_uri=" + redirectTo;
    oauth2.getToken(new JsonObject().put("code", code).put("redirect_uri", redirectURI), ar -> {
      if (ar.failed()) {
        logger.info("Auth fail");
        context.fail(ar.cause());
      } else {
        logger.info("Auth success");
        context.setUser(ar.result());
        context.response()
          .putHeader("Location", redirectTo)
          .setStatusCode(302)
          .end();
      }
    });
  }

  private void handleTest(RoutingContext rc) {
    rc.response()
      .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
      .end(new JsonObject().put("status","OK").encodePrettily());
  }

  /**
   * Enable CORS support.
   *
   * @param router router instance
   */
  protected void enableCorsSupport(Router router) {
    Set<String> allowHeaders = new HashSet<>();
    allowHeaders.add("x-requested-with");
    allowHeaders.add("Access-Control-Allow-Origin");
    allowHeaders.add("origin");
    allowHeaders.add("Content-Type");
    allowHeaders.add("accept");
    Set<HttpMethod> allowMethods = new HashSet<>();
    allowMethods.add(HttpMethod.GET);
    allowMethods.add(HttpMethod.PUT);
    allowMethods.add(HttpMethod.OPTIONS);
    allowMethods.add(HttpMethod.POST);
    allowMethods.add(HttpMethod.DELETE);
    allowMethods.add(HttpMethod.PATCH);

    router.route().handler(CorsHandler.create("*")
      .allowedHeaders(allowHeaders)
      .allowedMethods(allowMethods));
  }

  private static enum HttpStatus {
    SC_INTERNAL_SERVER_ERROR(500),
    SC_UNAUTHORIZED(401);

    private final int value;

    HttpStatus(int statusCode) {
      this.value = statusCode;
    }

    public int getValue() {
      return value;
    }
  }

}
