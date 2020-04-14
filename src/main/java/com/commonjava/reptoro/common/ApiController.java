package com.commonjava.reptoro.common;

import com.commonjava.reptoro.remoterepos.RemoteRepository;
import com.commonjava.reptoro.remoterepos.RemoteRepositoryService;
import com.commonjava.reptoro.sharedimports.SharedImportsService;
import com.google.common.net.HttpHeaders;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
import org.omg.CosNaming.NamingContextExtPackage.StringNameHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static io.vertx.core.http.HttpHeaders.LOCATION;

public class ApiController extends AbstractVerticle {


  Logger logger = Logger.getLogger(this.getClass().getName());

  private static final int DEFAULT_PORT = 8080;
  private static final String EVENTBUS = "/eventbus/*";
  private static final String REPTORO_PROTECTED = "/reptoro/*";
  private static final String LOGIN = "/reptoro/login";
  private static final String LOGOUT = "/reptoro/logout";
  private static final String TEST = "/reptoro/test";
  private static final String STATIC_CONTENT = "/*";
  private static final String CALLBACK = "/callback";
  private static final String REPOSITORY_ALL = "/reptoro/repository";
  private static final String REPTORO_INDEX_HTML = "/reptoro/index.html";
  public static final String APPLICATION_JSON_CHARSET_UTF_8 = "application/json; charset=utf-8";
  public static final String SHAREDIMPORTS_ALL = "/reptoro/sharedimport";

  private OAuth2Auth oAuth2Auth;
  private JsonObject config;
  private RemoteRepositoryService remoteRepositoryService;
  private SharedImportsService sharedImportsService;


  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    this.remoteRepositoryService = RemoteRepositoryService.createProxy(vertx,Const.REPO_SERVICE);
    this.sharedImportsService = SharedImportsService.createProxy(vertx,Const.SHARED_IMPORTS_SERVICE);
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
    SockJSHandler sockJSHandler = SockJSHandler.create(vertx);

    enableCorsSupport(router);

    // Store session information on the server side
    SessionStore sessionStore = LocalSessionStore.create(vertx);
    SessionHandler sessionHandler = SessionHandler.create(sessionStore);
    router.route().handler(sessionHandler);//***

    // CSRF handler setup required for logout form
    String csrfSecret = config().getJsonObject("reptoro").getString("session.secret");
    CSRFHandler csrfHandler = CSRFHandler.create(csrfSecret);
    router.route().handler(ctx -> {
        ctx.request().setExpectMultipart(true);
        ctx.request().endHandler(v -> csrfHandler.handle(ctx));
      }
    );//***

//    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

    router.errorHandler(HttpStatus.SC_INTERNAL_SERVER_ERROR.getValue(), this::handleHttpError);
    router.errorHandler(HttpStatus.SC_UNAUTHORIZED.getValue(), this::handleHttpError);


    sockJSHandler
      .bridge(
        new BridgeOptions()
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.CLIENT_TOPIC))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.CLIENT_TOPIC))
      );

    JsonObject keycloakConfig = config.getJsonObject("keycloak");

    logger.info(keycloakConfig.encodePrettily());

    HttpClientOptions httpClientOptions = new HttpClientOptions().setSsl(true).setVerifyHost(false).setTrustAll(true);

    oAuth2Auth = KeycloakAuth.create(vertx, OAuth2FlowType.AUTH_CODE, keycloakConfig,httpClientOptions);

    OAuth2AuthHandler oAuth2AuthHandler1 = OAuth2AuthHandler.create(oAuth2Auth);
    oAuth2AuthHandler1.setupCallback(router.get(CALLBACK));

    // session handler needs access to the authenticated user, otherwise we get an infinite redirect loop
    sessionHandler.setAuthProvider(oAuth2Auth);//***

//    router.route("/callback").handler(ctx -> authCallback(oAuth2Auth,buildHostURI(),ctx));

    router.route(EVENTBUS).handler(sockJSHandler);

    router.route(REPTORO_PROTECTED).handler(oAuth2AuthHandler1);
    router.get(REPOSITORY_ALL).handler(this::handleGetAllRepositories);
    router.get(SHAREDIMPORTS_ALL).handler(this::handleGetAllSharedImports);
    router.route(REPTORO_INDEX_HTML).handler(this::handleReroutingIndex);

//    router.get("/uaa").handler(this::currentuser);
    router.route(LOGIN).handler(this::currentuser);

    router.route(STATIC_CONTENT).handler(StaticHandler.create());


    router.route(TEST).handler(this::handleTest);

    server
      .requestHandler(router)
      .listen(config().getJsonObject("reptoro").getInteger("api.gateway.http.port",DEFAULT_PORT));
  }

  private void handleGetAllSharedImports(RoutingContext rc) {
    sharedImportsService.getAllSharedImportsFromDb(res -> {
      if(res.failed()) {
        rc.response().end(new JsonObject().put("results",new JsonArray()).put("cause",res.cause().getMessage()).encodePrettily());
      } else {
        JsonArray sharedImportArr = res.result();
//        List<String> buildIds =
//          sharedImportArr.stream()
//            .map(sharedImport -> new JsonObject(sharedImport.toString()))
//            .map(sharedImport -> sharedImport.getString("id"))
//            .distinct()
//            .collect(Collectors.toList());
        rc.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("results",sharedImportArr).encodePrettily());
      }
    });
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
        rc.response()
          .putHeader(CONTENT_TYPE,APPLICATION_JSON_CHARSET_UTF_8)
          .end(new JsonObject().put("results",repoArray).encodePrettily());
      }
    });
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
