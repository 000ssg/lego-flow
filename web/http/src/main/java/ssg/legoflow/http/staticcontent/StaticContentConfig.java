package ssg.legoflow.http.staticcontent;

import java.util.List;

public class StaticContentConfig {

    private String urlPrefix = "/static";
    private List<String> indexFiles = List.of("index.html", "index.htm");
    private boolean directoryListing = false;
    private int cacheMaxAge = 3600;

    public String getUrlPrefix() { return urlPrefix; }
    public void setUrlPrefix(String urlPrefix) { this.urlPrefix = urlPrefix; }
    public List<String> getIndexFiles() { return indexFiles; }
    public void setIndexFiles(List<String> indexFiles) { this.indexFiles = indexFiles; }
    public boolean isDirectoryListing() { return directoryListing; }
    public void setDirectoryListing(boolean directoryListing) { this.directoryListing = directoryListing; }
    public int getCacheMaxAge() { return cacheMaxAge; }
    public void setCacheMaxAge(int seconds) { this.cacheMaxAge = seconds; }
}
