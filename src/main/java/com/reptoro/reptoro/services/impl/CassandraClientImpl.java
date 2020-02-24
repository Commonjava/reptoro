package com.reptoro.reptoro.services.impl;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.reptoro.reptoro.services.CassandraClient;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

import java.util.logging.Logger;

public class CassandraClientImpl implements CassandraClient {

  Logger logger = Logger.getLogger(this.getClass().getName());


  String hostname;
  Integer port;
  String keyspace;
  Session sessionIndyStorage;
  io.vertx.cassandra.CassandraClient client;
//    Vertx vertx;

  public CassandraClientImpl(Session indystorage, JsonObject config) {
    this.hostname = config.getString("indy.db.cassandra.hostname");
    this.port = config.getInteger("indy.db.cassandra.port");
    this.keyspace = config.getString("indy.db.cassandra.keyspace");
    this.sessionIndyStorage = indystorage;
//        this.vertx = Vertx.vertx();
  }

  public CassandraClientImpl(io.vertx.cassandra.CassandraClient indystorage, JsonObject config) {
    this.hostname = config.getString("indy.db.cassandra.hostname");
    this.port = config.getInteger("indy.db.cassandra.port");
    this.keyspace = config.getString("indy.db.cassandra.keyspace");
    this.client = indystorage;
//        this.vertx = Vertx.vertx();
  }


  @Override
  public void getContentForRepository(String name, Handler<AsyncResult<JsonArray>> handler) {
    // logger.info("Get all content for repo: " + name);
    String statement = "select * from indystorage.pathmap where filesystem='" + name + "' and size>0 allow filtering";

    ResultSet resultSet = sessionIndyStorage.execute(statement);
    JsonArray dataArr = new JsonArray();

    for (Row row : resultSet.all()) {
//			if(row.getString("filename").endsWith("jar") && row.getString("filename").endsWith("pom") && row.getString("filename").endsWith("tar")) {
      JsonObject data = new JsonObject();
      data.put("filesystem", row.getString("filesystem"));
      data.put("parentpath", row.getString("parentpath"));
      data.put("filename", row.getString("filename"));
      data.put("checksum", row.getString("checksum"));
      data.put("fileid", row.getString("fileid"));
      data.put("filestorage", row.getString("filestorage"));
      data.put("size", row.getLong("size"));
      dataArr.add(data);
//			}
    }
    if (dataArr.size() > 0)
      logger.info("[[REPOSITORY]] " + name + " [[FILES.SIZE]] " + dataArr.size());
    handler.handle(Future.succeededFuture(dataArr));

  }

  @Override
  public void insertRemoteRepositoryData(JsonObject repo, Handler<AsyncResult<JsonObject>> handler) {
    // Store this repository in cassandra db.
  }

}
