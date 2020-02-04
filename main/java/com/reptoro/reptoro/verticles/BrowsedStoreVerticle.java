package com.reptoro.reptoro.verticles;

import com.reptoro.reptoro.services.CassandraClient;
import com.reptoro.reptoro.services.HttpClientService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.Instant;
import java.util.logging.Logger;

/**
 *
 * @author gorgigeorgievski
 */
public class BrowsedStoreVerticle extends AbstractVerticle {



	Logger logger = Logger.getLogger(this.getClass().getName());

	HttpClientService proxy;
	CassandraClient cassandra;
	io.vertx.cassandra.CassandraClient cassandraClient;

	@Override
	public void init(Vertx vertx, Context context) {
		super.init(vertx, context);
//		CassandraClientOptions options = new CassandraClientOptions();
//		options.setKeyspace("indystorage");
//		options
//		  .dataStaxClusterBuilder()
//		  .withCredentials("cassandra", "cassandra")
//		  .withPort(9042)
//		  .addContactPoint("cassandra-cluster.nos-automation.svc")
//		;
//		cassandraClient = io.vertx.cassandra.CassandraClient.create(vertx, options);
		proxy = HttpClientService.createProxy(vertx, "indy.http.client.service");
		cassandra = CassandraClient.createProxy(vertx, "indy.db.cassandra.client.service");
	}


	@Override
	public void start() throws Exception {


		vertx.eventBus().<JsonObject>consumer("browsed.stores", this:: processBrowsedStores );

		vertx.eventBus().<JsonObject>consumer("browsed.store.processing", this::fetchContentForBrowsedStore);


	}


  @Override
  public void stop() throws Exception {
    // Close Cassandra Connection...
  }

  void processBrowsedStores(Message<JsonObject> repo) {
		JsonObject remoteRepo = repo.body();
//		logger.info(remoteRepo.getString("key"));

		proxy.getListingsFromBrowsedStore(remoteRepo.getString("name"), res -> {
			if(res.succeeded()) {
				JsonObject browsedStore = res.result();
				browsedStore.put("timestamp.bs", Instant.now());
				remoteRepo.put("browsedStore", browsedStore);

				JsonArray listingUrlsArray = browsedStore.getJsonArray("listingUrls");

				if(listingUrlsArray != null && listingUrlsArray.isEmpty()) {

					vertx.eventBus().publish("change.protocol", remoteRepo); // TODO Create Change Protocol Verticle Or Show it on UI

				} else {

					vertx.eventBus().publish("browsed.store.processing", remoteRepo);

				}



			} else {
				logger.info("=== No Listings ===");
				logger.info(remoteRepo.getString("key\n\n"));
			}
		});
	}

	void fetchContentForBrowsedStore(Message<JsonObject> repo) {
		JsonArray dataArr = new JsonArray();


//
//		String statement = "select * from indystorage.pathmap where filesystem='" + repo.body().getString("key") + "' and size>0 allow filtering";
//		cassandraClient.executeWithFullFetch(statement, res -> {
//			logger.info("\n\n\n=== " + repo.body().getString("key") + " ===\n\n\n");
//			if (res.succeeded()) {
//				JsonObject repoJsonObject = repo.body();
//
//				List<Row> results = res.result();
//				for (Row row : results) {
//						JsonObject data = new JsonObject();
//						data.put("filesystem", row.getString("filesystem"));
//						data.put("parentpath", row.getString("parentpath"));
//						data.put("filename", row.getString("filename"));
//						data.put("checksum", row.getString("checksum"));
//						data.put("fileid", row.getString("fileid"));
//						data.put("filestorage", row.getString("filestorage"));
//						data.put("size", row.getLong("size"));
//						dataArr.add(data);
//				}
//				logger.info(repoJsonObject.encodePrettily());
//			} else {
//				logger.info("=== No Content ===");
//				logger.info(res.cause().toString());
//				logger.info(repo.body().getString("key\n\n"));
//			}
//		});

		cassandra.getContentForRepository(repo.body().getString("key"), res -> {
			if(res.succeeded()) {
				JsonObject repoJsonObject = repo.body();
				JsonArray contentArray = res.result();
				JsonObject browsedStore = repoJsonObject.getJsonObject("browsedStore");
				browsedStore.put("content", contentArray);

				vertx.eventBus().send("content.processing.headers", repo.body()); // send to just one processing verticle

			} else {
				logger.info("=== No Content ===");
				logger.info(repo.body().getString("key\n\n"));
				logger.info(res.cause()+"\n\n\n");
			}
		});
	}

}
