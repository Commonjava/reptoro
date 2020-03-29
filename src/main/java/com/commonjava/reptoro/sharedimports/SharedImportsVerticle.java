package com.commonjava.reptoro.sharedimports;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

import java.util.logging.Logger;

public class SharedImportsVerticle extends AbstractVerticle {

    Logger logger = Logger.getLogger(this.getClass().getName());


    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
    }

    @Override
    public void start() throws Exception {

    }
}
