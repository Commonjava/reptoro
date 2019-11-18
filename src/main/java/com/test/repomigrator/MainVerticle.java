package com.test.repomigrator;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Slf4jReporter;
import io.vertx.circuitbreaker.HystrixMetricHandler;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.*;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.providers.KeycloakAuth;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.UserSessionHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.net.InetAddress;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class MainVerticle extends AbstractVerticle {

	Logger logger = Logger.getLogger(this.getClass().getName());

//  private OAuth2Auth oauth2;
 
 
	@Override
	public void start() throws Exception {
    
    vertx.exceptionHandler(t -> {
      vertx.eventBus().publish("error.processing",
        new JsonObject()
          .put("time", Instant.now())
          .put("msg", t.getMessage())
          .put("cause", t.getCause())
          .put("stacktrace", t.getStackTrace())
          .put("suppresed", t.getSuppressed())
      );
    });
    
		System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory");

		configureLogging();

//	String hostURI = buildHostURI();
		// Initialize metric registry
		String registryName = "registry";
		MetricRegistry registry = SharedMetricRegistries.getOrCreate(registryName);
		SharedMetricRegistries.setDefault(registryName);

		Slf4jReporter reporter = Slf4jReporter.forRegistry(registry)
		  .outputTo(LoggerFactory.getLogger(MainVerticle.class))
		  .convertRatesTo(TimeUnit.SECONDS)
		  .convertDurationsTo(TimeUnit.MILLISECONDS)
		  .build();
		reporter.start(240, TimeUnit.MINUTES);

		// Initialize vertx with the metric registry
		DropwizardMetricsOptions metricsOptions = new DropwizardMetricsOptions()
		  .setEnabled(true)
		  .setMetricRegistry(registry);
		VertxOptions vertxOptions = new VertxOptions().setMetricsOptions(metricsOptions);
		// Vertx vertx = Vertx.vertx(vertxOptions);

//		ConfigRetrieverOptions configurationOptions = getConfigurationOptions();
//		ConfigRetriever configRetriever = ConfigRetriever.create(vertx, configurationOptions);
		

		// create OAuth 2 instance for Keycloak
//	oauth2 = KeycloakAuth.create(vertx, OAuth2FlowType.AUTH_CODE, configRetriever.getCachedConfig() );


//		configRetriever.getConfig(conf -> {
		  // deploy services first...
		  vertx.deployVerticle("com.test.repomigrator.verticles.IndyHttpClientVerticle", ar -> {
        logger.info("IndyHttpClientVerticle Deployed, id: " + ar.result());
        
        vertx.deployVerticle("com.test.repomigrator.RemoteRepositoryProcessing");
        vertx.deployVerticle("com.test.repomigrator.BrowsedProcessing");
        vertx.deployVerticle("com.test.repomigrator.ListingUrlProcessing");
        vertx.deployVerticle("com.test.repomigrator.verticles.ListingProcessingVerticle");
        vertx.deployVerticle("com.test.repomigrator.ContentProcessing");
        vertx.deployVerticle("com.test.repomigrator.RepoValidationProcessing");
        vertx.deployVerticle("com.test.repomigrator.ErrorProcessing");
        vertx.deployVerticle("com.test.repomigrator.DBProcessingVerticle");
      });
//		});

		HttpServer server = vertx.createHttpServer();
		Router router = Router.router(vertx);

//	router.route().handler(UserSessionHandler.create(oauth2));
		// set auth callback handler
//	router.route("/callback").handler(context -> authCallback(oauth2, hostURI, context));
//	router.get("/uaa").handler(this::authUaaHandler);
//	router.get("/login").handler(this::loginEntryHandler);
//	router.post("/logout").handler(this::logoutHandler);


		SockJSHandler sockJSHandler = SockJSHandler.create(vertx);

		sockJSHandler
		  .bridge(
			new BridgeOptions()
			  .addOutboundPermitted(new PermittedOptions().setAddress("listings.urls"))
			  .addInboundPermitted(new PermittedOptions().setAddress("listings.urls"))
			  .addOutboundPermitted(new PermittedOptions().setAddress("browsed.stores"))
			  .addInboundPermitted(new PermittedOptions().setAddress("browsed.stores"))
			  .addOutboundPermitted(new PermittedOptions().setAddress("error.processing"))
			  .addInboundPermitted(new PermittedOptions().setAddress("error.processing"))
			  .addOutboundPermitted(new PermittedOptions().setAddress("vertx.circuit-breaker"))
		  //          .addOutboundPermitted(new PermittedOptions().setAddress("open.circuit.browsed.stores"))
		  //          .addInboundPermitted(new PermittedOptions().setAddress("open.circuit.browsed.stores"))
		  );

		router.route("/eventbus/*").handler(sockJSHandler);
		router.route("/*").handler(StaticHandler.create());
		router.get("/hystrix-metrics").handler(HystrixMetricHandler.create(vertx));

		server.requestHandler(router).listen(8080);
	}

	private ConfigRetrieverOptions getConfigurationOptions() {
		JsonObject path = new JsonObject().put("path", "config/config.json");
		return new ConfigRetrieverOptions().addStore(new ConfigStoreOptions().setType("file").setConfig(path));
	}

	private static void configureLogging() {
		// It's OK to use MDC with static values
		MDC.put("application", "repomigrator");
		MDC.put("version", "1.0.0");
		MDC.put("release", "canary");
		try {
			MDC.put("hostname", InetAddress.getLocalHost().getHostName());
		} catch (Exception e) {
			// Silent error, we can live without it
		}
	}

//	private String buildHostURI() {
//		int port = config().getInteger("repomigrator.http.port", 8080);
//		final String host = config().getString("repomigrator.http.address.external", "localhost");
//		return String.format("https://%s:%d", host, port);
//	}
//
//	private void authCallback(OAuth2Auth oauth2, String hostURL, RoutingContext context) {
//		final String code = context.request().getParam("code");
//		// code is a require value
//		if (code == null) {
//			context.fail(400);
//			return;
//		}
//		final String redirectTo = context.request().getParam("redirect_uri");
//		final String redirectURI = hostURL + context.currentRoute().getPath() + "?redirect_uri=" + redirectTo;
//		oauth2.getToken(new JsonObject().put("code", code).put("redirect_uri", redirectURI), ar -> {
//			if (ar.failed()) {
//				logger.info("Auth fail");
//				context.fail(ar.cause());
//			} else {
//				logger.info("Auth success");
//				context.setUser(ar.result());
//				context.response()
//				  .putHeader("Location", redirectTo)
//				  .setStatusCode(302)
//				  .end();
//			}
//		});
//	}
//
//	private void authUaaHandler(RoutingContext context) {
//		if (context.user() != null) {
//			JsonObject principal = context.user().principal();
//			String username = null;
//			// String username = KeycloakHelper.preferredUsername(principal);
//			if (username == null) {
//
//			} else {
//				context.response().end();
//			}
//		} else {
//			context.fail(401);
//		}
//	}
//
//	private void loginEntryHandler(RoutingContext context) {
//		context.response()
//		  .putHeader("Location", generateAuthRedirectURI(buildHostURI()))
//		  .setStatusCode(302)
//		  .end();
//	}
//
//	private void logoutHandler(RoutingContext context) {
//		context.clearUser();
//		context.session().destroy();
//		context.response().setStatusCode(204).end();
//	}
//
//	private String generateAuthRedirectURI(String from) {
//		return oauth2.authorizeURL(new JsonObject()
//		  .put("redirect_uri", from + "/callback?redirect_uri=" + from)
//		  .put("scope", "")
//		  .put("state", ""));
//	}
}
