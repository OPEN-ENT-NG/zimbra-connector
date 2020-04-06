package fr.openent.zimbra.model;

public class SlackConfiguration {
    private String apiUri;
    private String channel;
    private String botUsername;
    private String apiToken;
    private String host;

    public SlackConfiguration(String apiUri, String apiToken, String channel, String botUsername, String host) {
        this.apiUri = apiUri;
        this.apiToken = apiToken;
        this.channel = channel;
        this.botUsername = botUsername;
        this.host = host;
    }

    public String uri() {
        return this.apiUri;
    }

    public String channel() {
        return this.channel;
    }

    public String apiToken () {
        return this.apiToken;
    }

    public String botUsername() {
        return this.botUsername;
    }

    public String host() {
        return this.host;
    }
}
