package com.commonjava.reptoro.common;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.KeycloakHelper;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.OAuth2AuthHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import io.vertx.reactivex.ext.auth.jwt.JWTAuth;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import java.util.logging.Logger;

public class ApiController extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());

  private OAuth2Auth oAuth2Auth;


  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
  }

  @Override
  public void stop() throws Exception {
    super.stop();
  }

  // API Gateway Controller Verticle for Reptoro [ handle SSO , Web Routing , SSE , Client Messaging ... ]
  @Override
  public void start() throws Exception {



    HttpServer server = vertx.createHttpServer();
    Router router = Router.router(vertx);
    SockJSHandler sockJSHandler = SockJSHandler.create(vertx);

    router.errorHandler(500, rc -> {
      System.err.println("Handling failure");
      Throwable failure = rc.failure();
      if (failure != null) {
        failure.printStackTrace();
      }
    });

    router.errorHandler(401, rc -> {
      System.err.println("Handling failure");
      Throwable failure = rc.failure();
      if (failure != null) {
        failure.printStackTrace();
      }
    });

    sockJSHandler
      .bridge(
        new BridgeOptions()
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.REPO_GET_ONE))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.REPO_GET_ONE))
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.REPO_FETCH))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.REPO_FETCH))
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.REPO_START))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.REPO_START))
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.CONTENT_PROCESSING))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.CONTENT_PROCESSING))
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.CONTENT_HEADERS))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.CONTENT_HEADERS))
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.COMPARE_HEADERS))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.COMPARE_HEADERS))
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.SHARED_START))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.SHARED_START))
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.PROCESS_SHAREDIMPORT_REPORT))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.PROCESS_SHAREDIMPORT_REPORT))
      );

//    {
//      "realm": "pncredhat",
//      "auth-server-url": "https://secure-sso-newcastle-stage.psi.redhat.com/auth",
//      "ssl-required": "none",
//      "resource": "reptoro",
//      "verify-token-audience": true,
//      "credentials": {
//          "secret": ""
//      },
//      "use-resource-role-mappings": true,
//      "confidential-port": 0
//    }

    JsonObject keycloakJson = new JsonObject()
      .put("realm", "pncredhat")
      .put("auth-server-url","https://secure-sso-newcastle-stage.psi.redhat.com/auth" )
//      .put("auth-server-url","http://localhost:9080/auth") // local test
      .put("ssl-required", "none")
      .put("resource", "reptoro")
      .put("verify-token-audience",true)
//      .put("allow-any-hostname",true)
//      .put("disable-trust-manager",true)
//      .put("bearer-only",true)
      .put("use-resource-role-mappings",true)
//      .put("public-client", true)
      .put("confidential-port", 0)
      .put("credentials", new JsonObject().put("secret", ""))
    ;



    oAuth2Auth = KeycloakAuth.create(vertx, OAuth2FlowType.AUTH_CODE, keycloakJson);

//    oAuth2Auth = OAuth2Auth.createKeycloak(vertx, OAuth2FlowType.AUTH_CODE, keycloakJson);
    OAuth2AuthHandler oAuth2AuthHandler1 =
      OAuth2AuthHandler.create(
        oAuth2Auth
      ,"http://reptoro-newcastle-stage.cloud.paas.psi.redhat.com/callback"
      );
//    oAuth2AuthHandler1.setupCallback(router.get("/callback"));

//    OAuth2AuthHandler oAuth2AuthHandler = OAuth2AuthHandler.create(oAuth2Auth);
//    oAuth2AuthHandler.setupCallback(router.get("/callback"));

//    String hostURI = buildHostURI();
//    router.route("/callback").handler(context -> authCallback(oAuth2Auth, hostURI, context));

    router.route("/*").handler(StaticHandler.create());
    router.route("/eventbus/*").handler(sockJSHandler);

    router.route("/reptoro/*").handler(oAuth2AuthHandler1);

    router.route("/reptoro/user").handler(this::currentuser);
    router.route("/reptoro/test").handler(this::handleTest);

    server.requestHandler(router).listen(8080);


  }

  private void handleTest(RoutingContext rc) {
    rc.response()
      .putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
      .end(new JsonObject().put("status","OK").encodePrettily());
  }


  public void currentuser(RoutingContext context) {
    String accessToken = KeycloakHelper.rawAccessToken(context.user().principal());
    JsonObject token = KeycloakHelper.parseToken(accessToken);
    context.response().end("User: " + token.getString("preferred_username"));
  }

  private String buildHostURI() {
    return "http://reptoro-newcastle-stage.cloud.paas.psi.redhat.com";
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

}
