package com.commonjava.reptoro.remoterepos;


import com.commonjava.reptoro.common.RepoStage;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import io.vertx.core.json.JsonObject;

@Table(keyspace = "reptoro" , name = "repos")
public class RemoteRepository {

    @Column(name = "type")
    private String type;

    @PartitionKey
    @Column(name = "key")
    private String key;


    @Column(name = "host")
    private String host;

    @Column(name = "packagetype")
    private String packageType;

    @Column(name = "name")
    private String name;

    @Column(name = "url")
    private String url;

    @Column(name = "compared")
    private Boolean compared;

    @Column(name = "stage")
    private String stage;


    public RemoteRepository(JsonObject repo) {
        this.type = repo.getString("type");
        this.key = repo.getString("key");
        this.host = repo.getString("host");
        this.packageType = repo.getString("packageType");
        this.name = repo.getString("name");
        this.url = repo.getString("url");
        this.compared = repo.containsKey("compared") ? repo.getBoolean("compared") : false;
        this.stage = repo.containsKey("stage") ? repo.getString("compared") : RepoStage.START;
    }

    public static JsonObject toJson(RemoteRepository repo) {
        return new JsonObject()
                .put("type", repo.getType())
                .put("key", repo.getKey())
                .put("host", repo.getHost())
                .put("packagetype", repo.getPackageType())
                .put("name", repo.getName())
                .put("url", repo.getUrl())
                .put("compared", repo.getCompared())
                .put("stage", repo.getStage());
    }

    public RemoteRepository() {
    }

    public RemoteRepository(String type, String key, String host, String packageType, String name, String url, Boolean compared, String stage) {
        this.type = type;
        this.key = key;
        this.host = host;
        this.packageType = packageType;
        this.name = name;
        this.url = url;
        this.compared = compared;
        this.stage = stage;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean getCompared() {
        return compared;
    }

    public void setCompared(Boolean compared) {
        this.compared = compared;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }
}
