package fr.openent.zimbra.model.message;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.RecallTask;
import io.vertx.core.json.JsonObject;

public class RecallMail {
    private int recallId;
    private Message message;
    private String comment;
    private String senderEmail;
    private String senderUserName;
    private Action<RecallTask> action;

    public RecallMail(int recallId, Message message) {
        this.recallId = recallId;
        this.message = message;
    }

    public RecallMail(int recallId, Message message, String senderEmail) {
        this(recallId, message);
        this.senderEmail = senderEmail;
    }

    public RecallMail(int recallId, Message message, Action<RecallTask> action, String comment, String senderUserName) {
        this(recallId, message);
        this.action = action;
        this.comment = comment;
        this.senderUserName = senderUserName;
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

    public void setSenderUserName(String senderUserName) {
        this.senderUserName = senderUserName;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public JsonObject generateDataForFront() {
        JsonObject messageJson = message != null ? message.toJson() : null;
        JsonObject actionJson = new JsonObject();
        JsonObject tasks = new JsonObject();
        if (action != null) {

            RecallTask dateMax = action.getTasks().stream()
                                            .filter(task -> task.getLastUpdated() != null)
                                            .max((task1, task2) -> task1.getLastUpdated().compareTo(task2.lastUpdated))
                                            .orElse(null);
            
            tasks
                    .put(TaskStatus.FINISHED.method(), action.getTasks().stream().filter(task -> task.getStatus() == TaskStatus.FINISHED).count())
                    .put(TaskStatus.ERROR.method(), action.getTasks().stream().filter(task -> task.getStatus() == TaskStatus.ERROR).count())
                    .put(Field.TOTAL, action.getTasks().size())
                    .put(Field.CAMEL_LAST_UPDATE, dateMax != null ? dateMax.getLastUpdated().getTime() : -1);
            actionJson
                    .put(Field.APPROVED, action.getApproved())
                    .put(Field.TASKS,tasks)
                    .put(Field.USERID, action.getUserId().toString())
                    .put(Field.DATE, action.getCreatedAt().getTime());
        }
        return new JsonObject()
                .put(Field.RECALLMAILID, recallId)
                .put(Field.CAMEL_USERNAME, senderUserName)
                .put(Field.COMMENT, comment)
                .put(Field.MESSAGE, messageJson)
                .put(Field.ACTION, actionJson);
    }
}
