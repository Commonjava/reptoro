package com.commonjava.reptoro.common;

import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.concurrent.TimeUnit;

public class ReptoroConfig {

    Vertx vertx;

    public ReptoroConfig(Vertx vertx) {
        this.vertx = vertx;
    }

    public ConfigRetrieverOptions defaultConfigOptions() {
        ConfigStoreOptions defaultConfigFileStore = new ConfigStoreOptions().setType("file")
                .setConfig(new JsonObject().put("path","conf/config.json"));
        return new ConfigRetrieverOptions().addStore(defaultConfigFileStore).setScanPeriod(TimeUnit.SECONDS.toMillis(2));
    }
}
