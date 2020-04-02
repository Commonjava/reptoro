package com.commonjava.reptoro.sharedimports;


import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import io.vertx.core.json.JsonObject;

@Table(keyspace = "reptoro" , name = "sharedimports")
public class SharedImport {

  @PartitionKey(0)
  @Column(name = "id")
  String id;

  @PartitionKey(1)
  @Column(name = "storekey")
  String storeKey;

  @Column(name = "accesschannel")
  String accessChannel;

  @PartitionKey(2)
  @Column(name = "path")
  String path;

  @Column(name = "originurl")
  String originUrl;

  @Column(name = "localurl")
  String localUrl;

  @Column(name = "md5")
  String md5;

  @Column(name = "sha256")
  String sha256;

  @Column(name = "sha1")
  String sha1;

  @Column(name = "compared")
  Boolean compared;

  public SharedImport() {
  }

  public SharedImport(String id, String storeKey, String accessChannel, String path, String originUrl, String localUrl, String md5, String sha256, String sha1,Boolean compared) {
    this.id = id;
    this.storeKey = storeKey;
    this.accessChannel = accessChannel;
    this.path = path;
    this.originUrl = originUrl;
    this.localUrl = localUrl;
    this.md5 = md5;
    this.sha256 = sha256;
    this.sha1 = sha1;
    this.compared = compared;
  }

  public SharedImport(JsonObject sharedImportReport) {
    this.id = sharedImportReport.getString("id");
    this.storeKey = sharedImportReport.getString("storeKey");
    this.accessChannel = sharedImportReport.getString("accessChannel");
    this.path = sharedImportReport.getString("path");
    this.originUrl = sharedImportReport.getString("originUrl");
    this.localUrl = sharedImportReport.getString("localUrl");
    this.md5 = sharedImportReport.getString("md5");
    this.sha256 = sharedImportReport.getString("sha256");
    this.sha1 = sharedImportReport.getString("sha1");
  }

  public JsonObject toJson(SharedImport sharedImport) {
    return new JsonObject()
      .put("id" , sharedImport.getId())
      .put("storeKey" , sharedImport.getStoreKey())
      .put("accessChannel" , sharedImport.getAccessChannel())
      .put("path" , sharedImport.getPath())
      .put("originUrl" , sharedImport.getOriginUrl())
      .put("localUrl" , sharedImport.getLocalUrl())
      .put("md5" , sharedImport.getMd5())
      .put("sha256" , sharedImport.getSha256())
      .put("sha1" , sharedImport.getSha1())
      ;
  }

  public Boolean getCompared() {
    return compared;
  }

  public void setCompared(Boolean compared) {
    this.compared = compared;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getStoreKey() {
    return storeKey;
  }

  public void setStoreKey(String storeKey) {
    this.storeKey = storeKey;
  }

  public String getAccessChannel() {
    return accessChannel;
  }

  public void setAccessChannel(String accessChannel) {
    this.accessChannel = accessChannel;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getOriginUrl() {
    return originUrl;
  }

  public void setOriginUrl(String originUrl) {
    this.originUrl = originUrl;
  }

  public String getLocalUrl() {
    return localUrl;
  }

  public void setLocalUrl(String localUrl) {
    this.localUrl = localUrl;
  }

  public String getMd5() {
    return md5;
  }

  public void setMd5(String md5) {
    this.md5 = md5;
  }

  public String getSha256() {
    return sha256;
  }

  public void setSha256(String sha256) {
    this.sha256 = sha256;
  }

  public String getSha1() {
    return sha1;
  }

  public void setSha1(String sha1) {
    this.sha1 = sha1;
  }
}
