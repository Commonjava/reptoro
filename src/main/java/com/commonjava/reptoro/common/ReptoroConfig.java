package com.commonjava.reptoro.common;

import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import org.slf4j.MDC;

public class ReptoroConfig {

    Vertx vertx;

    public ReptoroConfig(Vertx vertx) {
        this.vertx = vertx;
    }

    public ConfigRetrieverOptions defaultConfigOptions() {
        ConfigStoreOptions defaultConfigFileStore = new ConfigStoreOptions().setType("file")
                .setConfig(new JsonObject().put("path","conf/config.json"));

        return new ConfigRetrieverOptions()
            .addStore(defaultConfigFileStore)
            .setScanPeriod(TimeUnit.SECONDS.toMillis(5));
    }

    public static void configureLogging() {

        MDC.put("application", "reptoro");
        MDC.put("version", "1.0.0");
        MDC.put("release", "canary");
        try {
            MDC.put("hostname", InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            // Silent error, we can live without it
        }
    }
}
