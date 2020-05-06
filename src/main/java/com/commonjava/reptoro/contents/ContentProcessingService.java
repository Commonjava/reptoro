package com.commonjava.reptoro.contents;


import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface ContentProcessingService {

    void getLocalHeadersSync(JsonObject content, Handler<AsyncResult<JsonObject>> handler);

    void getSourceHeadersSync(String sourceUrl, JsonObject content, Handler<AsyncResult<JsonObject>> handler);

    void getContentsForRemoteRepository(JsonObject repo,Handler<AsyncResult<JsonObject>> handler );

    void getContentsForRemoteRepositoryAsync(JsonObject repo,Handler<AsyncResult<JsonObject>> handler );

    void getContentsFromDb(JsonObject repo,Handler<AsyncResult<JsonObject>> handler );

    void getRemoteRepositoryContentsCount(Handler<AsyncResult<JsonObject>> handler);

    void getRemoteRepositoryValidationCount(String key,Handler<AsyncResult<JsonObject>> handler);

    @GenIgnore
    static ContentProcessingService createProxy(Vertx vertx, String address) {
        return new ContentProcessingServiceVertxEBProxy(vertx,address);
    }

    @GenIgnore
    static ContentProcessingService createProxyWithOptions(Vertx vertx, String address, DeliveryOptions options) {
        return new ContentProcessingServiceVertxEBProxy(vertx,address,options);
    }
}
