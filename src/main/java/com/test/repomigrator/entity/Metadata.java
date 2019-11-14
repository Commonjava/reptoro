package com.test.repomigrator.entity;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.json.bind.annotation.JsonbProperty;
import java.io.Serializable;


@JsonIgnoreProperties(ignoreUnknown = true)
public class Metadata implements Serializable {
  
  @JsonbProperty("changelog")
    String changelog;
    
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
