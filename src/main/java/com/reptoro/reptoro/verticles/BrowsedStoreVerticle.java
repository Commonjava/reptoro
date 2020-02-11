package com.reptoro.reptoro.verticles;

import com.reptoro.reptoro.common.ReptoroTopics;
import com.reptoro.reptoro.services.CassandraClient;
import com.reptoro.reptoro.services.HttpClientService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.Objects;
import java.util.logging.Logger;

/**
 *
 * @author gorgigeorgievski
 */

// Worker Verticle - Fetching all BrowsedStore listings from indy side and then fetching all content from Cassandra
// Database Pathmap storage for indy for every BrowsedStore which is having listingsUrls array of content
public class BrowsedStoreVerticle extends AbstractVerticle {

	Logger logger = Logger.getLogger(this.getClass().getName());
	public final static String INDY_HTTP_CLIENT_PROXY_SERVICE = "indy.http.client.service";
	public final static String INDY_DB_CASSANDRA_CLIENT_PROXY_SERVICE = "indy.db.cassandra.client.service";
	HttpClientService proxy;
	CassandraClient cassandra;

	@Override
	public void init(Vertx vertx, Context context) {
		super.init(vertx, context);
		proxy = HttpClientService.createProxy(vertx, INDY_HTTP_CLIENT_PROXY_SERVICE);
		cassandra = CassandraClient.createProxy(vertx, INDY_DB_CASSANDRA_CLIENT_PROXY_SERVICE);
	}


	@Override
	public void start() throws Exception {

//		vertx.eventBus().<JsonObject>consumer("browsed.store", this:: processBrowsedStores );

    vertx.eventBus().<JsonArray>consumer(ReptoroTopics.BROWSED_STORES, this:: handleProcessingBrowsedStores );

		vertx.eventBus().<JsonObject>consumer(ReptoroTopics.BROWSED_STORE_PROCESSING, this::handleFetchContentsFromBrowsedStore);


	}

//  void processBrowsedStores(Message<JsonObject> repo) {
//		JsonObject remoteRepo = repo.body();
//
//		logger.info("=> Processing: " + remoteRepo.getString("key"));
//
//		proxy.getListingsFromBrowsedStore(remoteRepo.getString("name"), res -> {
//			if(res.succeeded()) {
//				JsonObject browsedStore = res.result();
//				browsedStore.put("timestamp.bs", Instant.now());
//				remoteRepo.put("browsedStore", browsedStore);
//
//				JsonArray listingUrlsArray = browsedStore.getJsonArray("listingUrls");
//
//				if(listingUrlsArray != null && listingUrlsArray.isEmpty()) {
//
//					vertx.eventBus().publish("change.protocol", remoteRepo); // TODO Create Change Protocol Verticle Or Show it on UI
//
//				} else {
//
//					vertx.eventBus().publish("browsed.store.process", remoteRepo);
//
//				}
//
//
//
//			} else {
//				logger.info("=== No Listings ===");
//				logger.info(remoteRepo.getString("key\n\n"));
//			}
//		});
//	}

  void handleProcessingBrowsedStores(Message<JsonArray> repos) {
    JsonArray remoteRepos = repos.body();

    for(Object repo : remoteRepos) {

      JsonObject remoteRepo = (JsonObject) repo ;

      logger.info("=> Processing KeyStore: " + remoteRepo.getString("key"));

      String repoName = remoteRepo.getString("name");

      proxy.getListingsFromBrowsedStore(repoName, res -> {
        if (res.succeeded()) {
          JsonObject browsedStore = res.result();
          browsedStore.put("timestamp.bs", Instant.now());
          remoteRepo.put("browsedStore", browsedStore);

          JsonArray listingUrlsArray = browsedStore.getJsonArray("listingUrls");

          if (Objects.isNull(listingUrlsArray) || listingUrlsArray.isEmpty()) {
            vertx.eventBus().publish(ReptoroTopics.CHANGE_PROTOCOL, remoteRepo);
          } else {

            vertx.eventBus().publish(ReptoroTopics.BROWSED_STORE_PROCESSING, remoteRepo);

          }
        } else {
          logger.info("=== Problem Fetching Content From Cassandra ===");
          logger.info(remoteRepo.getString("key\n\n"));
        }
      });
    }
  }

	void handleFetchContentsFromBrowsedStore(Message<JsonObject> repo) {

    JsonObject msgBody = repo.body();

    logger.info("=> Fetching content from Cassanra for repo: " + msgBody.getString("name"));

    String repoKey = msgBody.getString("key");

    cassandra.getContentForRepository(repoKey, res -> {
			if(res.succeeded()) {
				JsonArray contentArray = res.result();
				JsonObject browsedStore = msgBody.getJsonObject("browsedStore");
				browsedStore.put("content", contentArray);

				logger.info(msgBody.encodePrettily());

				vertx.eventBus().send(ReptoroTopics.CONTENT_PROCESSING_HEADERS, msgBody); // send to just one processing verticle

			} else {
				logger.info("=== No Content From Cassandra DB ===");
				logger.info(repo.body().getString("key\n\n"));
				logger.info(res.cause()+"\n\n\n");
			}
		});
	}

}
