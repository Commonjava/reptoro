package com.reptoro.reptoro.verticles;

import com.reptoro.reptoro.services.HttpClientService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;


// Worker Verticle
public class RemoteRepositoryVerticle extends AbstractVerticle {

	Logger logger = Logger.getLogger(this.getClass().getName());

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		vertx.eventBus().<JsonObject>consumer("remote.repo", this::handleRemoteRepositoryMsg);
	}


	void handleRemoteRepositoryMsg(Message<JsonObject> msg) {



			  // Get ,Filter and Transform all Provided Remote Repositories
			  processAllRemoteRepos(msg)
				// Attach to Remote Repository Browsed store json object
//				.thenApply(this::fetchBrowsedStores)
//
//
//				.thenCompose(this::publishProcessedReposFuture)

		  ;


	}

	void publishProcessedRepos(List<JsonObject> repos) {
		vertx.eventBus().<JsonArray>publish("browsed.stores", new JsonArray(repos));
	}

	CompletableFuture<List<JsonObject>> publishProcessedReposFuture(CompletableFuture<List<JsonObject>> repos) {
		return CompletableFuture.supplyAsync(new Supplier<List<JsonObject>>() {
			@Override
			public List<JsonObject> get() {
				try {
					vertx.eventBus().publish("browsed.stores", new JsonArray(repos.get()));
					return null;
				} catch (InterruptedException ex) {
					Logger.getLogger(RemoteRepositoryVerticle.class.getName()).log(Level.SEVERE, null, ex);
				} catch (ExecutionException ex) {
					Logger.getLogger(RemoteRepositoryVerticle.class.getName()).log(Level.SEVERE, null, ex);
				}
				return null;
			}

		});
	}

	List<JsonObject> publishRepos(List<JsonObject> repos) {
		vertx.eventBus().<JsonArray>publish("browsed.stores", new JsonArray(repos));
		return repos;
	}

	static Boolean compareProtocol(JsonObject repo) {
		String protocol = repo.getString("url").split("//")[0];
		return !protocol.equalsIgnoreCase("https:");
	}

	static Boolean filterPromotedRepos(JsonObject repo) {
		String name = repo.getString("name");
		return !name.contains("Promote");
	}

  static Boolean filterKojiRepos(JsonObject repo) {
    String name = repo.getString("name");
    return !name.contains("koji-");
  }

	static Boolean filterDisabledRepos(JsonObject repo) {
		return !repo.getBoolean("disabled");
	}

	static Boolean filterRemoteRepos(JsonObject repo) {
		return repo.getString("type").equalsIgnoreCase("remote");
	}

	CompletableFuture<List<JsonObject>> processAllRemoteRepos(Message<JsonObject> repos) {
		return CompletableFuture.supplyAsync(new Supplier<List<JsonObject>>() {
			@Override
			public List<JsonObject> get() {
				return repos
				  .body()
				  .getJsonArray("items")
				  .stream()
				  .map(entry -> new JsonObject(entry.toString()))
				  .filter(RemoteRepositoryVerticle::filterRemoteRepos)
				  .filter(RemoteRepositoryVerticle::filterDisabledRepos)
				  .filter(RemoteRepositoryVerticle::compareProtocol)
				  .filter(RemoteRepositoryVerticle::filterPromotedRepos)
          .filter(RemoteRepositoryVerticle::filterKojiRepos)
				  .map(repo -> {
					  repo.put("timestamp.rr", Instant.now());
					  vertx.eventBus().publish("browsed.stores", repo);
					  return repo;
				  })
				  .collect(Collectors.toList());
			}

			private Stream<JsonObject> publishRepos(Stream<JsonObject> repo) {
				// vertx.eventBus().publish("browsed.stores", new JsonObject(repo));
				return repo;
			}
		});
	}

	CompletableFuture<List<JsonObject>> fetchBrowsedStores(List<JsonObject> remoteRepos) {
		return CompletableFuture.supplyAsync(new Supplier<List<JsonObject>>() {
			@Override
			public List<JsonObject> get() {
				HttpClientService proxy = HttpClientService.createProxy(vertx, "indy.http.client.service");

				List<JsonObject> bsRemoteRepos = new ArrayList<>();
				for (JsonObject jo : remoteRepos) {
					proxy.getListingsFromBrowsedStore(jo.getString("key"), res -> {
						if (res.succeeded()) {
//							logger.info("-- Fetched --- " + jo.getString("key") );
							jo.put("browsedStore", res.result());
							bsRemoteRepos.add(jo);
						} else {
							logger.info("BAD RESULT: \n" + res.cause());
						}
					});
				}
				logger.info(bsRemoteRepos.size()+"");
				return bsRemoteRepos;
			}
		});
	}

//	JsonObject getBrowsedStore(JsonObject repo,HttpClientService proxy) {
//		JsonObject result = new JsonObject();
//		proxy.getListingsFromBrowsedStore(repo.getString("key"), res -> {
//			if(res.succeeded()) {
//				result = res.result();
//			}
//		});
//		return result;
//	}
}
