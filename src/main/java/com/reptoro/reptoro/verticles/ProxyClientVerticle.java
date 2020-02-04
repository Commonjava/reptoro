package com.reptoro.reptoro.verticles;


import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.reptoro.reptoro.services.CassandraClient;
import com.reptoro.reptoro.services.HttpClientService;
import com.reptoro.reptoro.services.impl.CassandraClientImpl;
import com.reptoro.reptoro.services.impl.HttpClientServiceImpl;

import io.vertx.cassandra.CassandraClientOptions;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.serviceproxy.ServiceBinder;

public class ProxyClientVerticle extends AbstractVerticle {
  @Override
  public void start() throws Exception {
    
    // Indy Endpoint Deployment of Proxy Client Service...
    WebClientOptions webOptions =
      new WebClientOptions()
        .setKeepAlive(true)
        .setTrustAll(true)
        .setConnectTimeout(60000)
//        .setSslEngineOptions(new OpenSSLEngineOptions().setSessionCacheEnabled(true))
      ;

    HttpClientService service =
      new HttpClientServiceImpl(WebClient.create(vertx,webOptions),config());

    new ServiceBinder(vertx.getDelegate())
      .setAddress("indy.http.client.service")
      .register(HttpClientService.class, service);
  
  
    CassandraClientOptions options = new CassandraClientOptions();
    options.setKeyspace("pathmap");
    options.setPort(9042);
  
  
    Cluster.Builder builder = options.dataStaxClusterBuilder();
    if(builder.getContactPoints().isEmpty()) {
      builder.addContactPoint("cassandra-cluster.newcastle-devel.svc");
    }
  
    builder
      .withCredentials("cassandra", "cassandra")
      .addContactPoint("cassandra-cluster.newcastle-devel.svc")
      .withPort(9042)
	  
    ;
    Cluster cluster = builder.build();
	
    Session indystorage = cluster.connect("indystorage");
	
//	io.vertx.cassandra.CassandraClient cassClient = io.vertx.cassandra.CassandraClient.create(vertx.getDelegate(), options);
//	
//	
//	
//	  CassandraClient cassandraClient = new CassandraClientImpl(cassClient,config());
//	  new ServiceBinder(vertx.getDelegate())
//		.setAddress("indy.db.cassandra.client.service")
//		.register(CassandraClient.class, cassandraClient);
  
    CassandraClient cassandraClient = new CassandraClientImpl(indystorage, config());
  
    new ServiceBinder(vertx.getDelegate())
      .setAddress("indy.db.cassandra.client.service")
      .register(CassandraClient.class, cassandraClient);
  
  }
}
