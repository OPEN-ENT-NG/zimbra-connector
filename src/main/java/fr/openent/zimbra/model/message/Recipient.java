package fr.openent.zimbra.model.message;

public class Recipient {
    private String emailAddress;
    private String userId;

    public Recipient(String emailAddress, String userId) {
        this.emailAddress = emailAddress;
        this.userId = userId;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getUserId() {
        return userId;
    }
}
