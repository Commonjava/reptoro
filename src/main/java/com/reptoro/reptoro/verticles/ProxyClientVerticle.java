package com.reptoro.reptoro.verticles;


import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.reptoro.reptoro.services.CassandraClient;
import com.reptoro.reptoro.services.HttpClientService;
import com.reptoro.reptoro.services.impl.CassandraClientImpl;
import com.reptoro.reptoro.services.impl.HttpClientServiceImpl;

import io.vertx.cassandra.CassandraClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.serviceproxy.ServiceBinder;

public class ProxyClientVerticle extends AbstractVerticle {


  @Override
  public void start() throws Exception {

    JsonObject config = config();


    // Indy Endpoint Deployment of Proxy Client Service...
    WebClientOptions webOptions =
      new WebClientOptions()
        .setKeepAlive(true)
        .setTrustAll(true)
        .setConnectTimeout(60000)
//        .setSslEngineOptions(new OpenSSLEngineOptions().setSessionCacheEnabled(true))
      ;
    HttpClientService service =
      new HttpClientServiceImpl(WebClient.create(vertx,webOptions),config);
    new ServiceBinder(vertx.getDelegate())
      .setAddress("indy.http.client.service")
      .register(HttpClientService.class, service);


    // Cassandra Client Deployment of Proxy Service ...
    CassandraClientOptions options = new CassandraClientOptions();
    options.setKeyspace(config.getString("indy.db.cassandra.keyspace"));
    options.setPort(config.getInteger("indy.db.cassandra.port"));

    Cluster.Builder builder = options.dataStaxClusterBuilder();
    if(builder.getContactPoints().isEmpty()) {
      builder.addContactPoint(config.getString("indy.db.cassandra.hostname"));
    }
    builder
      .withCredentials(config.getString("indy.db.cassandra.user"), config.getString("indy.db.cassandra.pass"))
      .addContactPoint(config.getString("indy.db.cassandra.hostname"))
      .withPort(config.getInteger("indy.db.cassandra.port"));
    Cluster cluster = builder.build();
    Session indystorage = cluster.connect(config.getString("indy.db.cassandra.name"));
    CassandraClient cassandraClient = new CassandraClientImpl(indystorage, config());
    new ServiceBinder(vertx.getDelegate())
      .setAddress("indy.db.cassandra.client.service")
      .register(CassandraClient.class, cassandraClient);

  }
}
