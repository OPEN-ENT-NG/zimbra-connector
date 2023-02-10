package fr.openent.zimbra.model.task;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.IModelHelper;
import fr.openent.zimbra.model.IModel;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.message.RecallMail;
import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class RecallTask extends Task implements IModel<RecallTask> {
    private RecallMail recallMessage;
    private UUID receiverId;
    private int retry;

    public RecallTask(JsonObject jsonObject) {
        super(jsonObject);
        this.receiverId = UUID.fromString(jsonObject.getString(Field.RECEIVER_ID));
        this.retry = jsonObject.getInteger(Field.RETRY);
        // this.recallMessage = recallMessage;
    }

    public RecallTask(long id, TaskStatus status, Action<RecallTask> action, RecallMail recallMessage, UUID receiverId, int retry) {
        super(id, status, action);
        this.recallMessage = recallMessage;
        this.receiverId = receiverId;
        this.retry = retry;
    }

    public RecallTask(JsonObject dbData, Action<RecallTask> action, RecallMail recallMessage) throws Exception {
        super(dbData, action);
        if (!JSONContainsRecallData(dbData)) {
            throw new Exception(String.format("[Zimbra@%s::RecallTask] Json does not match RecallTask model", this.getClass().getSimpleName()));
        }
        try {
            this.receiverId = UUID.fromString(dbData.getString(Field.RECEIVER_ID));
            this.retry = dbData.getInteger(Field.RETRY);
            this.recallMessage = recallMessage;
        } catch (Exception e) {
            throw new Exception(String.format("[Zimbra@%s::RecallTask] Bad field format", this.getClass().getSimpleName()));
        }
    }

    private boolean JSONContainsRecallData (JsonObject data) {
        return  data.containsKey(Field.RECEIVER_ID) && data.containsKey(Field.RETRY);
    }

    public RecallMail getRecallMessage() {
        return recallMessage;
    }

    public UUID getReceiverId() {
        return receiverId;
    }

    public int getRetry() {
        return retry;
    }

    @Override
    public JsonObject toJson() {
        return IModelHelper.toJson(this, false, true);
    }
}
