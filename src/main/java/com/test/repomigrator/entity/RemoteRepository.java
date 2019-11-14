package com.test.repomigrator.entity;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.vertx.codegen.annotations.DataObject;

import java.io.Serializable;
import javax.json.bind.annotation.JsonbProperty;


@DataObject
@JsonAutoDetect
//@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteRepository implements Serializable {
  
  @JsonbProperty("type")
  public String type;
  @JsonbProperty("key")
  public String key;
  @JsonbProperty("metadata")
  public Metadata metadata;
  @JsonbProperty("disabled")
  public Boolean disabled;
  @JsonbProperty("host")
  public String host;
  @JsonbProperty("port")
  public Integer port;
  @JsonbProperty("packageType")
  public String packageType;
  @JsonbProperty("name")
  public String name;
  @JsonbProperty("disable_timeout")
  public String disableTimout;
  @JsonbProperty("path_style")
  public String pathStyle;
  @JsonbProperty("authoritative_index")
  public Boolean authoritativeIndex;
  @JsonbProperty("allow_snapshots")
  public Boolean allowSnapshots;
  @JsonbProperty("allow_releases")
  public Boolean allowReleases;
  public String url;
  @JsonbProperty("timeout_seconds")
  public Integer timeoutSeconds;
  @JsonbProperty("max_connections")
  public Integer maxConnections;
  @JsonbProperty("ignore_hostname_verification")
  public Boolean ignoreHostnameVerification;
  @JsonbProperty("nfc_timeout_seconds")
  public Integer nfcTimeoutSeconds;
  @JsonbProperty("is_passthrough")
  public Boolean isPassthrough;
  @JsonbProperty("cache_timeout_seconds")
  public Integer cacheTimeoutSeconds;
  @JsonbProperty("metadata_timeout_seconds")
  public Integer metadataTimeoutSeconds;
  @JsonbProperty("server_certificate_pem")
  public String serverCertificatePem;
  @JsonbProperty("proxy_port")
  public Integer proxyPort;
  @JsonbProperty("prefetch_priority")
  public Integer prefetchPriority;
  @JsonbProperty("prefetch_rescan")
  public Boolean prefetchRescan;
  @JsonbProperty("prefetch_listing_type")
  public String prefetchListingType;
  
  
  
  public String getDisableTimout() {
    return disableTimout;
  }
  
  public void setDisableTimout(String disableTimout) {
    this.disableTimout = disableTimout;
  }
  
  public String getPathStyle() {
    return pathStyle;
  }
  
  public void setPathStyle(String pathStyle) {
    this.pathStyle = pathStyle;
  }
  
  public Boolean getAuthoritativeIndex() {
    return authoritativeIndex;
  }
  
  public void setAuthoritativeIndex(Boolean authoritativeIndex) {
    this.authoritativeIndex = authoritativeIndex;
  }
  
  public Boolean getAllowSnapshots() {
    return allowSnapshots;
  }
  
  public void setAllowSnapshots(Boolean allowSnapshots) {
    this.allowSnapshots = allowSnapshots;
  }
  
  public Boolean getAllowReleases() {
    return allowReleases;
  }
  
  public void setAllowReleases(Boolean allowReleases) {
    this.allowReleases = allowReleases;
  }
  
  public Integer getTimeoutSeconds() {
    return timeoutSeconds;
  }
  
  public void setTimeoutSeconds(Integer timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }
  
  public Integer getMaxConnections() {
    return maxConnections;
  }
  
  public void setMaxConnections(Integer maxConnections) {
    this.maxConnections = maxConnections;
  }
  
  public Boolean getIgnoreHostnameVerification() {
    return ignoreHostnameVerification;
  }
  
  public void setIgnoreHostnameVerification(Boolean ignoreHostnameVerification) {
    this.ignoreHostnameVerification = ignoreHostnameVerification;
  }
  
  public Integer getNfcTimeoutSeconds() {
    return nfcTimeoutSeconds;
  }
  
  public void setNfcTimeoutSeconds(Integer nfcTimeoutSeconds) {
    this.nfcTimeoutSeconds = nfcTimeoutSeconds;
  }
  
  public Boolean getIsPassthrough() {
    return isPassthrough;
  }
  
  public void setIsPassthrough(Boolean isPassthrough) {
    this.isPassthrough = isPassthrough;
  }
  
  public Integer getCacheTimeoutSeconds() {
    return cacheTimeoutSeconds;
  }
  
  public void setCacheTimeoutSeconds(Integer cacheTimeoutSeconds) {
    this.cacheTimeoutSeconds = cacheTimeoutSeconds;
  }
  
  public Integer getMetadataTimeoutSeconds() {
    return metadataTimeoutSeconds;
  }
  
  public void setMetadataTimeoutSeconds(Integer metadataTimeoutSeconds) {
    this.metadataTimeoutSeconds = metadataTimeoutSeconds;
  }
  
  public String getServerCertificatePem() {
    return serverCertificatePem;
  }
  
  public void setServerCertificatePem(String serverCertificatePem) {
    this.serverCertificatePem = serverCertificatePem;
  }
  
  public Integer getProxyPort() {
    return proxyPort;
  }
  
  public void setProxyPort(Integer proxyPort) {
    this.proxyPort = proxyPort;
  }
  
  public Integer getPrefetchPriority() {
    return prefetchPriority;
  }
  
  public void setPrefetchPriority(Integer prefetchPriority) {
    this.prefetchPriority = prefetchPriority;
  }
  
  public Boolean getPrefetchRescan() {
    return prefetchRescan;
  }
  
  public void setPrefetchRescan(Boolean prefetchRescan) {
    this.prefetchRescan = prefetchRescan;
  }
  
  public String getPrefetchListingType() {
    return prefetchListingType;
  }
  
  public void setPrefetchListingType(String prefetchListingType) {
    this.prefetchListingType = prefetchListingType;
  }
  
  
  
  
  
  public Metadata getMetadata() {
    return metadata;
  }
  
  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
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
  
  public Boolean getDisabled() {
    return disabled;
  }
  
  public void setDisabled(Boolean disabled) {
    this.disabled = disabled;
  }
  
  public String getHost() {
    return host;
  }
  
  public void setHost(String host) {
    this.host = host;
  }
  
  public Integer getPort() {
    return port;
  }
  
  public void setPort(Integer port) {
    this.port = port;
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
  
  @Override
  public String toString() {
    return "{" + "type:" + type + ", key:" + key + ", metadata:" + metadata + ", disabled:" + disabled + ", host:" + host + ", port:" + port + ", packageType:" + packageType + ", name:" + name + ", disableTimout:" + disableTimout + ", pathStyle:" + pathStyle + ", authoritativeIndex:" + authoritativeIndex + ", allowSnapshots:" + allowSnapshots + ", allowReleases:" + allowReleases + ", url:" + url + ", timeoutSeconds:" + timeoutSeconds + ", maxConnections:" + maxConnections + ", ignoreHostnameVerification:" + ignoreHostnameVerification + ", nfcTimeoutSeconds:" + nfcTimeoutSeconds + ", isPassthrough:" + isPassthrough + ", cacheTimeoutSeconds:" + cacheTimeoutSeconds + ", metadataTimeoutSeconds:" + metadataTimeoutSeconds + ", serverCertificatePem:" + serverCertificatePem + ", proxyPort:" + proxyPort + ", prefetchPriority:" + prefetchPriority + ", prefetchRescan:" + prefetchRescan + ", prefetchListingType:" + prefetchListingType + "}";
  }
  
  
  
}
