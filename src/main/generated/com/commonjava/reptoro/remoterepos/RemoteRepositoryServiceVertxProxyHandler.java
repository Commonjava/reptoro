/*
* Copyright 2014 Red Hat, Inc.
*
* Red Hat licenses this file to you under the Apache License, version 2.0
* (the "License"); you may not use this file except in compliance with the
* License. You may obtain a copy of the License at:
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations
* under the License.
*/

package com.commonjava.reptoro.remoterepos;

import com.commonjava.reptoro.remoterepos.RemoteRepositoryService;
import io.vertx.core.Vertx;
import io.vertx.core.Handler;
import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.serviceproxy.ProxyHandler;
import io.vertx.serviceproxy.ServiceException;
import io.vertx.serviceproxy.ServiceExceptionMessageCodec;
import io.vertx.serviceproxy.HelperUtils;

import java.util.List;
import io.vertx.core.json.JsonObject;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
/*
  Generated Proxy code - DO NOT EDIT
  @author Roger the Robot
*/

@SuppressWarnings({"unchecked", "rawtypes"})
public class RemoteRepositoryServiceVertxProxyHandler extends ProxyHandler {

  public static final long DEFAULT_CONNECTION_TIMEOUT = 5 * 60; // 5 minutes 
  private final Vertx vertx;
  private final RemoteRepositoryService service;
  private final long timerID;
  private long lastAccessed;
  private final long timeoutSeconds;

  public RemoteRepositoryServiceVertxProxyHandler(Vertx vertx, RemoteRepositoryService service){
    this(vertx, service, DEFAULT_CONNECTION_TIMEOUT);
  }

  public RemoteRepositoryServiceVertxProxyHandler(Vertx vertx, RemoteRepositoryService service, long timeoutInSecond){
    this(vertx, service, true, timeoutInSecond);
  }

  public RemoteRepositoryServiceVertxProxyHandler(Vertx vertx, RemoteRepositoryService service, boolean topLevel, long timeoutSeconds) {
      this.vertx = vertx;
      this.service = service;
      this.timeoutSeconds = timeoutSeconds;
      try {
        this.vertx.eventBus().registerDefaultCodec(ServiceException.class,
            new ServiceExceptionMessageCodec());
      } catch (IllegalStateException ex) {}
      if (timeoutSeconds != -1 && !topLevel) {
        long period = timeoutSeconds * 1000 / 2;
        if (period > 10000) {
          period = 10000;
        }
        this.timerID = vertx.setPeriodic(period, this::checkTimedOut);
      } else {
        this.timerID = -1;
      }
      accessed();
    }


  private void checkTimedOut(long id) {
    long now = System.nanoTime();
    if (now - lastAccessed > timeoutSeconds * 1000000000) {
      close();
    }
  }

    @Override
    public void close() {
      if (timerID != -1) {
        vertx.cancelTimer(timerID);
      }
      super.close();
    }

    private void accessed() {
      this.lastAccessed = System.nanoTime();
    }

  public void handle(Message<JsonObject> msg) {
    try{
      JsonObject json = msg.body();
      String action = msg.headers().get("action");
      if (action == null) throw new IllegalStateException("action not specified");
      accessed();
      switch (action) {
        case "fetchRemoteRepositories": {
          service.fetchRemoteRepositories((java.lang.String)json.getValue("packageType"),
                        HelperUtils.createHandler(msg));
          break;
        }
        case "checkCassandraConnection": {
          service.checkCassandraConnection(HelperUtils.createHandler(msg));
          break;
        }
        case "createReptoroRepositoriesKeyspace": {
          service.createReptoroRepositoriesKeyspace(HelperUtils.createHandler(msg));
          break;
        }
        case "creteReptoroRepositoriesTable": {
          service.creteReptoroRepositoriesTable(HelperUtils.createHandler(msg));
          break;
        }
        case "creteReptoroContentsTable": {
          service.creteReptoroContentsTable(HelperUtils.createHandler(msg));
          break;
        }
        case "storeRemoteRepository": {
          service.storeRemoteRepository((io.vertx.core.json.JsonObject)json.getValue("repo"),
                        HelperUtils.createHandler(msg));
          break;
        }
        case "getRemoteRepository": {
          service.getRemoteRepository((io.vertx.core.json.JsonObject)json.getValue("repo"),
                        HelperUtils.createHandler(msg));
          break;
        }
        case "updateRemoteRepository": {
          service.updateRemoteRepository((io.vertx.core.json.JsonObject)json.getValue("repo"),
                        HelperUtils.createHandler(msg));
          break;
        }
        case "getOneRemoteRepository": {
          service.getOneRemoteRepository(HelperUtils.createHandler(msg));
          break;
        }
        case "updateNewRemoteRepositories": {
          service.updateNewRemoteRepositories(HelperUtils.convertList(json.getJsonArray("repos").getList()),
                        HelperUtils.createHandler(msg));
          break;
        }
        default: throw new IllegalStateException("Invalid action: " + action);
      }
    } catch (Throwable t) {
      msg.reply(new ServiceException(500, t.getMessage()));
      throw t;
    }
  }
}