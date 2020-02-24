package com.reptoro.reptoro.common;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.api.querybuilder.schema.CreateKeyspace;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTable;
import io.vertx.core.Vertx;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;
import java.util.UUID;

public class CassandraWriteClient {


  Vertx vertx;
  String keySpace;

  private CqlSession session;


  public CassandraWriteClient(Vertx vertx) {
    this.vertx = vertx;
    this.keySpace = vertx.getOrCreateContext().config().getString("indy.db.cassandra.write.keyspace");

  }

  public CassandraWriteClient connect(String node, Integer port,String user, String pass , String dataCenter) {
    this.session =
            CqlSession
              .builder()
              .withAuthCredentials(user,pass)
              .addContactPoint(new InetSocketAddress(node, port))
//              .withLocalDatacenter(dataCenter)
              .build()
            ;
    return this;
  }

  public CassandraWriteClient createKeySpace(@Nonnull String keyspaceName , int numberOfReplicas) {
    CreateKeyspace createKeyspace =
      SchemaBuilder.createKeyspace(keyspaceName).ifNotExists().withSimpleStrategy(numberOfReplicas);
    getSession().execute(createKeyspace.build());
    return this;
  }

  public CassandraWriteClient useKeyspace(@Nonnull String keyspace) {
    getSession().execute("USE " + CqlIdentifier.fromCql(keyspace));
    return this;
  }

  // columns: remoterepo , browsedstore , content , timestamp
  public CassandraWriteClient createReptoroTable(@Nonnull String tableName,@Nonnull String ...columnNames) {
    CreateTable reptoroTable =
      SchemaBuilder.createTable(tableName)
        .ifNotExists()
        .withPartitionKey("reptoro_id", DataTypes.UUID);

    for(String column : columnNames) {
      reptoroTable.withColumn(column,DataTypes.TEXT);
    }

    return this;
  }

  public CassandraWriteClient insertReptoroRecord(@Nonnull String tableName , String ...values) {
    UUID reptoroId = UUID.randomUUID();
    String query =
      new StringBuilder("INSERT INTO ").append(tableName).append("(reptoro_id, , , ) ").append("VALUES (")
        .append(reptoroId.toString()).append(", '").append("remoterepo").append("', '").append("browsedstore").append("', '")
        .append("content").append("');").append("timestamp").append("');").toString();
    getSession().execute(query);
    return this;
  }

  public CqlSession getSession() {
    return this.session;
  }

  public CassandraWriteClient close() {
    session.close();
    return this;
  }
}
