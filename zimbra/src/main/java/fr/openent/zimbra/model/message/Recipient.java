package fr.openent.zimbra.model.message;

import fr.openent.zimbra.core.enums.RecipientType;

public class Recipient {
    private String emailAddress;
    private String userId;
    private RecipientType recipientType;

    public Recipient(String emailAddress, String userId, RecipientType recipientType) {
        this.emailAddress = emailAddress;
        this.userId = userId;
        this.recipientType = recipientType;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getUserId() {
        return userId;
    }

    public RecipientType getRecipientType() {
        return this.recipientType;
    }

    public void setRecipientType(RecipientType recipientType) {
        this.recipientType = recipientType;
    }

}
