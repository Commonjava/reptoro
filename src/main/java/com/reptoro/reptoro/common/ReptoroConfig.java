/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.reptoro.reptoro.common;

import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.json.JsonObject;
import java.net.InetAddress;
import org.slf4j.MDC;

/**
 *
 * @author gorgigeorgievski
 */
public class ReptoroConfig {


	public static ConfigRetrieverOptions getConfiguration() {

		ConfigStoreOptions defaultFileConfigStore = new ConfigStoreOptions()
		  .setType("file")
		  .setConfig(new JsonObject().put("path", "conf/config.json"));

		return new ConfigRetrieverOptions().addStore(defaultFileConfigStore);
	}

	public static void configureLogging() {
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

}
