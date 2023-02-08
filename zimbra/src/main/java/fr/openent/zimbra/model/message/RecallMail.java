package fr.openent.zimbra.model.message;

public class RecallMail {
    private int recallId;
    private String mailId;

    public RecallMail(int recallId, String mailId) {
        this.recallId = recallId;
        this.mailId = mailId;
    }

    public int getRecallId() {
        return recallId;
    }

    public String getMailId() {
        return mailId;
    }
}
