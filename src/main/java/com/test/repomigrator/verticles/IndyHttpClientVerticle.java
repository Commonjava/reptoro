/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.test.repomigrator.verticles;

import com.test.repomigrator.services.IndyHttpClientService;
import com.test.repomigrator.services.impl.IndyHttpClientServiceImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.serviceproxy.ServiceBinder;

/**
 *
 * @author gorgigeorgievski
 */
public class IndyHttpClientVerticle extends AbstractVerticle {
  
  
  @Override
  public void start() throws Exception {
    
    IndyHttpClientService service = new IndyHttpClientServiceImpl(WebClient.create(vertx,new WebClientOptions().setKeepAlive(true)));
    
    new ServiceBinder(vertx)
      .setAddress("indy.http.client.service")
      .register(IndyHttpClientService.class, service);
  }
  
  
	
}
