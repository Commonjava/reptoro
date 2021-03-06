package com.commonjava.reptoro.contents;

import com.commonjava.reptoro.common.Topics;
import io.vertx.cassandra.Mapper;
import io.vertx.cassandra.MappingManager;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

import java.util.logging.Logger;

public class SaveHeadersVerticle extends AbstractVerticle {

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

        vertx.eventBus().consumer(Topics.SAVE_HEADERS , this::handleSavingContent);

    }

    private void handleSavingContent(Message<JsonObject> tMessage) {
        JsonObject contentWithLocalAndSourceHeaders = tMessage.body();

        contentMapper.save(new Content(contentWithLocalAndSourceHeaders), update -> {
            if(update.succeeded()) {
//                logger.info("CONTENT HEADERS UPDATE SAVED!");
//              System.out.print(".");
            } else {
                logger.info("CONTENT HEADERS UPDATE FAILED: " + update.cause());
            }
        });
    }
}
