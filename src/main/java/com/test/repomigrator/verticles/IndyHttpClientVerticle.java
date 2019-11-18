/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.test.repomigrator.verticles;

import com.test.repomigrator.services.IndyHttpClientService;
import com.test.repomigrator.services.impl.IndyHttpClientServiceImpl;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.serviceproxy.ServiceBinder;

/**
 *
 * @author gorgigeorgievski
 */
public class IndyHttpClientVerticle extends AbstractVerticle {
  
  
  @Override
  public void start() throws Exception {
    WebClientOptions webOptions =
      new WebClientOptions()
        .setKeepAlive(true)
        .setTrustAll(true)
//        .setSslEngineOptions(new OpenSSLEngineOptions().setSessionCacheEnabled(true))
      ;
    
    IndyHttpClientService service = new IndyHttpClientServiceImpl(WebClient.create(vertx,webOptions));
    
    
    new ServiceBinder(vertx.getDelegate())
      .setAddress("indy.http.client.service")
      .register(IndyHttpClientService.class, service);
  }
  
  
	
}
