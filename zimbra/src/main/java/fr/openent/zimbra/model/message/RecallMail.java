package fr.openent.zimbra.model.message;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.RecallTask;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RecallMail {
    private int recallId;
    private Message message;
    private String comment;
    private String senderEmail;
    private Action<RecallTask> action;

    public RecallMail(int recallId, Message message) {
        this.recallId = recallId;
        this.message = message;
    }

    public RecallMail(int recallId, Message message, String senderEmail) {
        this(recallId, message);
        this.senderEmail = senderEmail;
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

    public String getSenderEmail() {
        return senderEmail;
    }

    public JsonObject generateDataForFront() {
        JsonObject messageJson = message != null ? message.toJson() : null;
        JsonObject tasks = new JsonObject();
        if (action != null) {
            tasks
                    .put(TaskStatus.FINISHED.method(), action.getTasks().stream().filter(task -> task.getStatus() == TaskStatus.FINISHED).count())
                    .put(TaskStatus.ERROR.method(), action.getTasks().stream().filter(task -> task.getStatus() == TaskStatus.ERROR).count())
                    .put(Field.TOTAL, action.getTasks().size());
        }
        return new JsonObject()
                .put(Field.RECALL_MAIL_ID, recallId)
                .put(Field.COMMENT, comment)
                .put(Field.MESSAGE, messageJson)
                .put(Field.ACTION, tasks);
    }
}
