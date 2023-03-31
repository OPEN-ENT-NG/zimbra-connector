package fr.openent.zimbra.model.message;

import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.RecallTask;

public class RecallMail {
    private int recallId;
    private Message message;
    private  String comment;
    private Action<RecallTask> action;

    public RecallMail(int recallId, Message message) {
        this.recallId = recallId;
        this.message = message;
    }

    public RecallMail(int recallId, Message message, Action<RecallTask> action, String comment) {
        this(recallId, message);
        this.action = action;
        this.comment = comment;
    }

    public int getRecallId() {
        return recallId;
    }

    public Message getMessage() {
        return message;
    }

    public Action<RecallTask> getAction() {
        return this.action;
    }

    public String getComment() {
        return comment;
    }

    public void setId(int recallId) {
        this.recallId = recallId;
    }
}
