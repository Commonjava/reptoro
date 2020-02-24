package com.reptoro.reptoro.verticles;

import com.reptoro.reptoro.common.ReptoroTopics;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

import java.util.logging.Logger;

public class ReptoroHttpVerticle extends AbstractVerticle {

  Logger logger = Logger.getLogger(this.getClass().getName());

  @Override
  public void start() throws Exception {
    // Create Http Server and serve SSE and EventBus to UI
    HttpServer server = vertx.createHttpServer();
    Router router = Router.router(vertx);
    SockJSHandler sockJSHandler = SockJSHandler.create(vertx);

    sockJSHandler
      .bridge(
        new BridgeOptions()
          .addOutboundPermitted(new PermittedOptions().setAddress(ReptoroTopics.REMOTE_REPO))
          .addInboundPermitted(new PermittedOptions().setAddress(ReptoroTopics.REMOTE_REPO))
          .addOutboundPermitted(new PermittedOptions().setAddress(ReptoroTopics.REMOTE_REPO_START))
          .addInboundPermitted(new PermittedOptions().setAddress(ReptoroTopics.REMOTE_REPO_START))
          .addOutboundPermitted(new PermittedOptions().setAddress(ReptoroTopics.REMOTE_REPOS_FILTERED))
          .addInboundPermitted(new PermittedOptions().setAddress(ReptoroTopics.REMOTE_REPOS_FILTERED))
          .addOutboundPermitted(new PermittedOptions().setAddress(ReptoroTopics.BROWSED_STORES))
          .addInboundPermitted(new PermittedOptions().setAddress(ReptoroTopics.BROWSED_STORES))
      );

    router.route("/eventbus/*").handler(sockJSHandler);
    router.route("/*").handler(StaticHandler.create());


    server.requestHandler(router).listen(8080);

  }

  private void handleHttpRequests(HttpServerRequest req) {
    req.response().end("Thanks");
  }

}
