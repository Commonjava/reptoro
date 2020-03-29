package com.commonjava.reptoro.remoterepos;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

import java.util.List;


@ProxyGen
public interface RemoteRepositoryService {

    // indy
    void fetchRemoteRepositories(String packageType, Handler<AsyncResult<JsonObject>> resultHandler);


    // cassandra
    void checkCassandraConnection(Handler<AsyncResult<JsonObject>> handler);

    void createReptoroRepositoriesKeyspace(Handler<AsyncResult<JsonObject>> handler);

    void creteReptoroRepositoriesTable(Handler<AsyncResult<JsonObject>> handler);

    void creteReptoroContentsTable(Handler<AsyncResult<JsonObject>> handler);

    void storeRemoteRepository(JsonObject repo, Handler<AsyncResult<JsonObject>> handler);

    void getRemoteRepository(JsonObject repo, Handler<AsyncResult<JsonObject>> handler);

    void updateRemoteRepository(JsonObject repo, Handler<AsyncResult<JsonObject>> handler);

    void getOneRemoteRepository(Handler<AsyncResult<JsonObject>> handler);

    void updateNewRemoteRepositories(List<JsonObject> repos, Handler<AsyncResult<JsonObject>> handler);

    @GenIgnore
    static RemoteRepositoryService createProxy(Vertx vertx, String address) {
        return new RemoteRepositoryServiceVertxEBProxy(vertx,address);
    }

    @GenIgnore
    static RemoteRepositoryService createProxyWithOptions(Vertx vertx, String address, DeliveryOptions options) {
        return new RemoteRepositoryServiceVertxEBProxy(vertx,address,options);
    }

}
