package com.commonjava.reptoro.contents;


import com.commonjava.reptoro.common.Const;
import com.datastax.driver.mapping.annotations.Column;
import com.datastax.driver.mapping.annotations.PartitionKey;
import com.datastax.driver.mapping.annotations.Table;
import io.vertx.core.json.JsonObject;

import java.math.BigInteger;

@Table(keyspace = "reptoro", name = "contents")
public class Content {

  @Column(name = "localheaders")
  private String localheaders;

  @Column(name = "sourceheaders")
  private String sourceheaders;

  @Column(name = "source")
  private String source;

  @PartitionKey(2)
  @Column(name = "filesystem")
  private String filesystem;

  @PartitionKey(0)
  @Column(name = "parentpath")
  private String parentpath;


  @PartitionKey(1)
  @Column(name = "filename")
  private String filename;

  @Column(name = "checksum")
  private String checksum;

  @Column(name = "fileid")
  private String fileid;

  @Column(name = "filestorage")
  private String filestorage;

  @Column(name = "size")
  private Long size;

  public Content() {
  }

  public Content(String localheaders, String sourceheaders, String source, String filesystem, String parentpath, String filename, String checksum, String fileid, String filestorage, Long size) {
    this.localheaders = localheaders;
    this.sourceheaders = sourceheaders;
    this.source = source;
    this.filesystem = filesystem;
    this.parentpath = parentpath;
    this.filename = filename;
    this.checksum = checksum;
    this.fileid = fileid;
    this.filestorage = filestorage;
    this.size = size;
  }

  public Content(JsonObject content) {
    if (content.containsKey(Const.LOCALHEADERS) && content.getValue(Const.LOCALHEADERS) instanceof JsonObject) {
      this.localheaders = content.getJsonObject(Const.LOCALHEADERS).encode();
    } else {
      this.localheaders = content.getString(Const.LOCALHEADERS);
    }

    if (content.containsKey(Const.SOURCEHEADERS) && content.getValue(Const.SOURCEHEADERS) instanceof JsonObject) {
      this.sourceheaders = content.getJsonObject(Const.SOURCEHEADERS).encode();
    } else {
      this.sourceheaders = content.getString(Const.SOURCEHEADERS);
    }
    this.source = content.getString("source");
    this.filesystem = content.getString("filesystem");
    this.parentpath = content.getString("parentpath");
    this.filename = content.getString("filename");
    if(content.containsKey("checksum") && content.getValue("checksum") instanceof Boolean) {
      this.checksum =  Boolean.toString(content.getBoolean("checksum"));
    } else {
      this.checksum = content.getString("checksum");
    }
    this.fileid = content.getString("fileid");
    this.filestorage = content.getString("filestorage");
    this.size = content.getLong("size");
  }

  public static JsonObject toJson(Content content) {
    return new JsonObject()
      .put("localheaders", content.getLocalheaders())
      .put("sourceheaders", content.getSourceheaders())
      .put("source", content.getSource())
      .put("filesystem", content.getFilesystem())
      .put("parentpath", content.getParentpath())
      .put("filename", content.getFilename())
      .put("checksum", content.getChecksum())
      .put("fileid", content.getFileid())
      .put("filestorage", content.getFilestorage())
      .put("size", content.getSize());
  }

  public static Content fromJson(JsonObject con) {
    Content content = new Content();
    content.setLocalheaders(con.getString("localheaders"));
    content.setSourceheaders(con.getString("sourceheaders"));
    content.setSource(con.getString("source"));
    content.setFilesystem(con.getString("filesystem"));
    content.setParentpath(con.getString("parentpath"));
    content.setFilename(con.getString("filename"));
    content.setChecksum(con.getString("checksum"));
    content.setFileid(con.getString("fileid"));
    content.setFilestorage(con.getString("filestorage"));
    content.setSize(con.getLong("size"));
    return content;
  }

  public String getLocalheaders() {
    return localheaders;
  }

  public void setLocalheaders(String localheaders) {
    this.localheaders = localheaders;
  }

  public String getSourceheaders() {
    return sourceheaders;
  }

  public void setSourceheaders(String sourceheaders) {
    this.sourceheaders = sourceheaders;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getFilesystem() {
    return filesystem;
  }

  public void setFilesystem(String filesystem) {
    this.filesystem = filesystem;
  }

  public String getParentpath() {
    return parentpath;
  }

  public void setParentpath(String parentpath) {
    this.parentpath = parentpath;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getChecksum() {
    return checksum;
  }

  public void setChecksum(String checksum) {
    this.checksum = checksum;
  }

  public String getFileid() {
    return fileid;
  }

  public void setFileid(String fileid) {
    this.fileid = fileid;
  }

  public String getFilestorage() {
    return filestorage;
  }

  public void setFilestorage(String filestorage) {
    this.filestorage = filestorage;
  }

  public Long getSize() {
    return size;
  }

  public void setSize(Long size) {
    this.size = size;
  }
}
