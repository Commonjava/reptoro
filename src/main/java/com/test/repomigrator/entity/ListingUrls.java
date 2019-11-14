package com.test.repomigrator.entity;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.vertx.codegen.annotations.DataObject;

import javax.json.bind.annotation.JsonbProperty;
import java.io.Serializable;
import java.util.List;

/**
 *
 * @author gorgigeorgievski
 */

@DataObject
@JsonAutoDetect
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListingUrls implements Serializable{
  
  @JsonbProperty("path")
    String path;
  @JsonbProperty("listingUrl")
    String listingUrl;
  
    List<String> sources;
  
  @JsonbProperty("contentUrl")
    String contentUrl;
    
    public String getContentUrl() {
        return contentUrl;
    }
    
    public void setContentUrl(String contentUrl) {
        this.contentUrl = contentUrl;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getListingUrl() {
        return listingUrl;
    }
    
    public void setListingUrl(String listingUrl) {
        this.listingUrl = listingUrl;
    }
    
    public List<String> getSources() {
        return sources;
    }
    
    public void setSources(List<String> sources) {
        this.sources = sources;
    }
    
    @Override
    public String toString() {
        return "{" + "path:" + path + ", listingUrl:" + listingUrl + ", sources:" + sources + ",contentUrl:" + contentUrl+ "}";
    }
}

