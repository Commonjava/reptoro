package com.commonjava.reptoro.contents;

import com.commonjava.reptoro.common.Topics;
import io.vertx.cassandra.Mapper;
import io.vertx.cassandra.MappingManager;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class SaveContentVerticle extends AbstractVerticle {

    Logger logger = Logger.getLogger(this.getClass().getName());

    private JsonObject config;
    private io.vertx.cassandra.CassandraClient cassandraClient;
    private Mapper<Content> contentMapper;

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        this.config = vertx.getOrCreateContext().config();
        this.cassandraClient = new com.commonjava.reptoro.common.CassandraClient(vertx,config()).getCassandraReptoroClientInstance();
        MappingManager mappingManagerContents = MappingManager.create(this.cassandraClient);
        this.contentMapper = mappingManagerContents.mapper(Content.class);
    }

    @Override
    public void start() throws Exception {

        vertx.eventBus().consumer(Topics.SAVE_CONTENT , this::handleSavingContent);

    }

    private void handleSavingContent(Message<JsonObject> tMessage) {
        JsonObject content = tMessage.body();

        contentMapper.save(new Content(content), res -> {
            if (res.succeeded()) {
//                logger.info("CONTENT SAVED: " + content.getString("filename"));
            } else {
                logger.info("CONTENT NOT SAVED: " + res.cause());
            }
        });
    }
}
