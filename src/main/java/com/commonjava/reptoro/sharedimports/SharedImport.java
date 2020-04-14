package com.commonjava.reptoro.sharedimports;


import com.datastax.driver.core.Row;
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

  @Column(name = "pathmatch")
  Boolean pathmatch;

  @Column(name = "sourceheaders")
  String sourceheaders;

  @Column(name = "checksum")
  Boolean checksum;

  public SharedImport() {
  }

  public SharedImport(String id, String storeKey, String accessChannel, String path, String originUrl, String localUrl, String md5, String sha256, String sha1,Boolean compared,Boolean pathmatch,String sourceheaders,Boolean checksum) {
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
    this.pathmatch = pathmatch;
    this.sourceheaders = sourceheaders;
    this.checksum = checksum;
  }

  public SharedImport(JsonObject sharedImportReport) {
    this.id = sharedImportReport.getString("id");
    this.storeKey = sharedImportReport.containsKey("storeKey") ? sharedImportReport.getString("storeKey") : "";
    this.accessChannel = sharedImportReport.containsKey("accessChannel") ? sharedImportReport.getString("accessChannel") : "";
    this.path = sharedImportReport.containsKey("path") ? sharedImportReport.getString("path") : "";
    this.originUrl = sharedImportReport.containsKey("originUrl") ? sharedImportReport.getString("originUrl") : "";
    this.localUrl = sharedImportReport.containsKey("localUrl") ? sharedImportReport.getString("localUrl") : "";
    this.md5 = sharedImportReport.containsKey("md5") ? sharedImportReport.getString("md5") : "";
    this.sha256 = sharedImportReport.containsKey("sha256") ? sharedImportReport.getString("sha256") : "";
    this.sha1 = sharedImportReport.containsKey("sha1") ? sharedImportReport.getString("sha1") : "";
    this.compared = sharedImportReport.containsKey("compared") ? sharedImportReport.getBoolean("compared") : false;

    this.pathmatch = sharedImportReport.containsKey("pathmatch") ? sharedImportReport.getBoolean("pathmatch") : false;
    this.checksum = sharedImportReport.containsKey("checksum") ? sharedImportReport.getBoolean("checksum") : false;
    if(sharedImportReport.containsKey("sourceheaders") && sharedImportReport.getValue("sourceheaders") instanceof JsonObject) {
      this.sourceheaders = sharedImportReport.getJsonObject("sourceheaders").encode();
    } else {
      this.sourceheaders = sharedImportReport.getString("sourceheaders");
    }
  }

  public static JsonObject toSiJson(Row siRow) {
    return new JsonObject()
      .put("id",siRow.getString("id")).put("storekey",siRow.getString("storekey"))
      .put("accesschannel",siRow.getString("accesschannel")).put("path",siRow.getString("path"))
      .put("originurl",siRow.getString("originurl")).put("localurl",siRow.getString("localurl"))
      .put("md5",siRow.getString("md5")).put("sha1",siRow.getString("sha1")).put("sha256",siRow.getString("sha256"))
      .put("compared",siRow.getBool("compared")).put("checksum",siRow.getBool("checksum")).put("pathmatch",siRow.getBool("pathmatch"))
      .put("sourceheaders",siRow.getString("sourceheaders"));
  }

  public static JsonObject toJson(SharedImport sharedImport) {
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
      .put("compared",sharedImport.getCompared())
      .put("pathmatch",sharedImport.getPathmatch())
      .put("checksum",sharedImport.getChecksum())
      .put("sourceheaders",sharedImport.getSourceheaders())
      ;
  }

  public String getSourceheaders() {
    return sourceheaders;
  }

  public void setSourceheaders(String sourceheaders) {
    this.sourceheaders = sourceheaders;
  }

  public Boolean getChecksum() {
    return checksum;
  }

  public void setChecksum(Boolean checksum) {
    this.checksum = checksum;
  }

  public Boolean getPathmatch() {
    return pathmatch;
  }

  public void setPathmatch(Boolean pathmatch) {
    this.pathmatch = pathmatch;
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
