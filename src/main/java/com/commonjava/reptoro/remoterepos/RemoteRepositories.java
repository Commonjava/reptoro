package com.commonjava.reptoro.remoterepos;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RemoteRepositories {

    Logger logger = Logger.getLogger(this.getClass().getName());

    private JsonObject config;
    private Vertx vertx;

    public RemoteRepositories() {}

    public RemoteRepositories(JsonObject config, Vertx vertx) {
        this.config = config;
        this.vertx = vertx;
    }

    public List<JsonObject> filterMavenNonSslRemoteRepositories(JsonObject repos) {
        List<JsonObject> items = repos.getJsonArray("items").getList();
        return items
                .stream()
                .map(entry -> new JsonObject(entry.toString()))
                .filter(this::filterRemoteRepos)
                .filter(this::filterDisabledRepos)
                .filter(this::filterProtocol)
                .filter(this::filterPromotedRepos)
                .filter(this::filterKojiRepos)
                .filter(this::filterExceptedRepos)
                .filter(this::filterImplicitRepos)
                .collect(Collectors.toList())
                ;
    }

    public Boolean filterRemoteRepos(JsonObject repo) {
        return repo.getString("type").equalsIgnoreCase("remote");
    }

    public Boolean filterProtocol(JsonObject repo) {
        String protocol = repo.getString("url").split("//")[0];
        return !protocol.equalsIgnoreCase("https:");
    }

    public Boolean filterPromotedRepos(JsonObject repo) {
        String name = repo.getString("name");
        return !name.startsWith("Promote_");
    }

    public Boolean filterKojiRepos(JsonObject repo) {
        String name = repo.getString("name");
        return !name.startsWith("koji-");
    }

    public Boolean filterDisabledRepos(JsonObject repo) {
        return !repo.getBoolean("disabled");
    }

    public Boolean filterExceptedRepos(JsonObject repo) {
        JsonObject reptoroConfig = config.getJsonObject("reptoro");

        List<String> exceptedRepos = reptoroConfig.getJsonArray("except.remote.repos").getList();

        String key = repo.getString("key");
        for(String repoKey : exceptedRepos) {
            if(repoKey.equalsIgnoreCase(key)) {
                logger.info("[[FILTER.EXCLUDE]] " + key);
                return false;
            }
        }
        return true;
    }

    public Boolean filterImplicitRepos(JsonObject repo) {
        String key = repo.getString("key");
        JsonObject metadata = repo.getJsonObject("metadata");
        if(metadata.containsKey("disabled") || metadata.containsKey("HTTP_HEAD_STATUS") || metadata.containsKey("HTTP_GET_STATUS")) {
            logger.info("[[FILTER.EXCLUDE.IMPLICIT]] " + key);
            return false;
        }
        return true;
    }
}
