package com.commonjava.reptoro.common;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PoolingOptions;
import io.vertx.cassandra.CassandraClientOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class CassandraClient {


    private Vertx vertx;
    private JsonObject config;
    private io.vertx.cassandra.CassandraClient cassandraReptoroClient;
    private io.vertx.cassandra.CassandraClient cassandraIndyRClient;
    private CassandraClientOptions cassandraClientOptions;
    private CassandraClientOptions cassandraIndyOptions;


    public CassandraClient(Vertx vertx , JsonObject config) {
        this.vertx = vertx;
        this.config = config;
        this.cassandraClientOptions = new CassandraClientOptions();
        this.cassandraIndyOptions = new CassandraClientOptions();
    }

    public io.vertx.cassandra.CassandraClient getCassandraReptoroClientInstance() {
        JsonObject cassandraConfig = this.config.getJsonObject("cassandra");
        JsonObject reptoroCassandraConfig = cassandraConfig.getJsonObject("reptoro");

        String user = cassandraConfig.getString("user");
        String pass = cassandraConfig.getString("pass");
        Integer port = cassandraConfig.getInteger("port");
        String cassandraHostname = cassandraConfig.getString("hostname");
        String reptoroKeyspace = reptoroCassandraConfig.getString("keyspace");
        String reptoroTablename = reptoroCassandraConfig.getString("tablename");

        PoolingOptions poolingOptions = new PoolingOptions();
        poolingOptions.setPoolTimeoutMillis(5*1000);
        poolingOptions.setMaxQueueSize(10000);

      Cluster.Builder builder =
        this.cassandraClientOptions
        .setKeyspace(reptoroKeyspace)
        .dataStaxClusterBuilder();

      builder
                .withoutMetrics()
                .withoutJMXReporting()
                .withPort(port)
                .withCredentials(user, pass)
                .withPoolingOptions(poolingOptions)
//                .withRetryPolicy(new LoggingRetryPolicy(DefaultRetryPolicy.INSTANCE))
//                .withReconnectionPolicy(Policies.)
                .addContactPoint(cassandraHostname);

//      Cluster cluster = builder.build();
//
//      JmxReporter reporter =
//        JmxReporter.forRegistry(cluster.getMetrics().getRegistry())
//          .inDomain(cluster.getClusterName() + "-metrics")
//          .build();
//
//      reporter.start();

        this.cassandraReptoroClient = io.vertx.cassandra.CassandraClient.create(vertx,cassandraClientOptions);
        return this.cassandraReptoroClient;
    }

    public io.vertx.cassandra.CassandraClient getCassandraIndyClientInstance() {
        JsonObject cassandraConfig = this.config.getJsonObject("cassandra");

        String user = cassandraConfig.getString("user");
        String pass = cassandraConfig.getString("pass");
        Integer port = cassandraConfig.getInteger("port");
        String cassandraHostname = cassandraConfig.getString("hostname");
        String cassandraKeyspace = cassandraConfig.getString("keyspace");

        this.cassandraIndyOptions
//                .setKeyspace(cassandraKeyspace)
                .dataStaxClusterBuilder()
                .withoutMetrics()
                .withoutJMXReporting()
                .withPort(port)
                .withCredentials(user, pass)
                .addContactPoint(cassandraHostname);
        this.cassandraIndyRClient = io.vertx.cassandra.CassandraClient.create(vertx,cassandraIndyOptions);
        return this.cassandraIndyRClient;

    }
}
