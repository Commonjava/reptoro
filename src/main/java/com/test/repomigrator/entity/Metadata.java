package com.test.repomigrator.entity;


import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.io.Serializable;


@DataObject(generateConverter = true)
public class Metadata implements Serializable {
  

    String changelog;

    public Metadata(JsonObject json) {
        
    }
    
    public String getChangelog() {
        return changelog;
    }
    
    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }
    
    @Override
    public String toString() {
        return "{" + "changelog:" + changelog + "}";
    }
    
    
}
