package com.commonjava.reptoro.common;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
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

import java.util.logging.Logger;

public class ApiController extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());


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
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.SHARED_IMPORTS_START))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.SHARED_IMPORTS_START))
          .addOutboundPermitted(new PermittedOptions().setAddress(Topics.PROCESS_SHAREDIMPORT_REPORT))
          .addInboundPermitted(new PermittedOptions().setAddress(Topics.PROCESS_SHAREDIMPORT_REPORT))
      );

    JsonObject keycloakJson = new JsonObject()
      .put("realm", "pncredhat")
      .put("auth-server-url","https://secure-sso-newcastle-stage.psi.redhat.com/auth")
      .put("ssl-required", "none")
      .put("resource", "reptoro")
      .put("public-client", true)
      .put("confidential-port", 0)
      .put("credentials", new JsonObject().put("secret", "f599ca7d-9dfb-460e-9731-dc9b250fbf5d"))
      ;

    OAuth2Auth oAuth2Auth = KeycloakAuth.create(vertx, OAuth2FlowType.AUTH_CODE, keycloakJson);
    OAuth2AuthHandler oAuth2AuthHandler = OAuth2AuthHandler.create(oAuth2Auth);
    oAuth2AuthHandler.setupCallback(router.get("/callback"));

    // Configure the AuthHandler to process JWT's
//    router.route("/greeting").handler(JWTAuthHandler.create(
//      JWTAuth.create(vertx, new JWTAuthOptions()
//        .addPubSecKey(new PubSecKeyOptions()
//          .setAlgorithm("RS256")
//          .setPublicKey(System.getenv("REALM_PUBLIC_KEY")))
//        // since we're consuming keycloak JWTs we need to locate the permission claims in the token
//        .setPermissionsClaimKey("realm_access/roles"))));


    router.route("/*").handler(StaticHandler.create());
    router.route("/eventbus/*").handler(sockJSHandler);
    router.route("/reptoro/*").handler(oAuth2AuthHandler);


    router.route("/reptoro/user").handler(this::currentuser);

    server.requestHandler(router).listen(8080);


  }


  public void currentuser(RoutingContext context) {
    String accessToken = KeycloakHelper.rawAccessToken(context.user().principal());
    JsonObject token = KeycloakHelper.parseToken(accessToken);
    context.response().end("User: " + token.getString("preferred_username"));
  }

}
