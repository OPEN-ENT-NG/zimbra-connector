package fr.openent.zimbra.model;

import io.vertx.core.json.JsonObject;

public class DocumentConfiguration {
    private String cidPattern;
    private String documentDownloadEndpoint;

    public DocumentConfiguration(JsonObject documentConfig) {
        this.cidPattern = documentConfig.getString("cid-patern", "cid:(.*?)\"");
        this.documentDownloadEndpoint = documentConfig.getString("document-download-endpoint", "/service/home/~/?auth=co&loc=fr_FR&id=");
    }

    // Getter

    public String getCidPattern() {
        return cidPattern;
    }

    public String getDocumentDownloadEndpoint() {
        return documentDownloadEndpoint;
    }

    // Setter

    public DocumentConfiguration setCidPattern(String cidPattern) {
        this.cidPattern = cidPattern;
        return this;
    }

    public DocumentConfiguration setDocumentDownloadEndpoint(String documentDownloadEndpoint) {
        this.documentDownloadEndpoint = documentDownloadEndpoint;
        return this;
    }
}
