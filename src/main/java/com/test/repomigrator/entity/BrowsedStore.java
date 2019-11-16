package com.test.repomigrator.entity;


import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import javax.json.bind.annotation.JsonbProperty;
import java.io.Serializable;
import java.util.List;

/**
 *
 * @author gorgigeorgievski
 */


@DataObject(generateConverter = true)
public class BrowsedStore implements Serializable {



    String storeKey;
    String path;
    String storeBrowseUrl;
    String storeContentUrl;
    String baseBrowseUrl;
    String baseContentUrl;
    String parentUrl;
    String parentPath;
  

    List<String> sources;
    List<ListingUrls> listingUrls;
    
    
    public BrowsedStore() {
    }
    
    public BrowsedStore(JsonObject jsonObject) {
    //  BrowsedStoreConverter.fromJson(json, obj);
    }
    
    public BrowsedStore(String storeKey, String path, String storeBrowseUrl, String storeContentUrl, String baseBrowseUrl, List<String> sources, List<ListingUrls> listingUrls) {
        this.storeKey=storeKey;
        this.path = path;
        this.storeBrowseUrl = storeBrowseUrl;
        this.storeContentUrl  = storeContentUrl;
        this.baseBrowseUrl = baseBrowseUrl;
        this.sources = sources;
        this.listingUrls = listingUrls;
    }
    
    public JsonObject toJson() {
      JsonObject jsonObject = new JsonObject();
    //   BrowsedStoreConverter.toJson(obj, json);
      return jsonObject;
    }
    
    public String getStoreKey() {
        return storeKey;
    }
    
    public void setStoreKey(String storeKey) {
        this.storeKey = storeKey;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    public String getStoreBrowseUrl() {
        return storeBrowseUrl;
    }
    
    public void setStoreBrowseUrl(String storeBrowseUrl) {
        this.storeBrowseUrl = storeBrowseUrl;
    }
    
    public String getStoreContentUrl() {
        return storeContentUrl;
    }
    
    public void setStoreContentUrl(String storeContentUrl) {
        this.storeContentUrl = storeContentUrl;
    }
    
    public String getBaseBrowseUrl() {
        return baseBrowseUrl;
    }
    
    public void setBaseBrowseUrl(String baseBrowseUrl) {
        this.baseBrowseUrl = baseBrowseUrl;
    }
    
    public String getBaseContentUrl() {
        return baseContentUrl;
    }
    
    public void setBaseContentUrl(String baseContentUrl) {
        this.baseContentUrl = baseContentUrl;
    }
    
    public List<String> getSources() {
        return sources;
    }
    
    public void setSources(List<String> sources) {
        this.sources = sources;
    }
    
    public List<ListingUrls> getListingUrls() {
        return listingUrls;
    }
    
    public void setListingUrls(List<ListingUrls> listingUrls) {
        this.listingUrls = listingUrls;
    }
    
    public String getParentUrl() {
        return parentUrl;
    }
    
    public void setParentUrl(String parentUrl) {
        this.parentUrl = parentUrl;
    }
    
    public String getParentPath() {
        return parentPath;
    }
    
    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
    }
    
    @Override
    public String toString() {
        return "{" + "storeKey:" + storeKey + ", path:" + path + ", storeBrowseUrl:" + storeBrowseUrl + ", storeContentUrl:" + storeContentUrl + ", baseBrowseUrl:" + baseBrowseUrl + ", baseContentUrl:" + baseContentUrl + ", sources:" + sources + ", listingUrls:" + listingUrls + "}";
    }
    
    
    
}
