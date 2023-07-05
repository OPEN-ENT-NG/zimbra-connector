package fr.openent.zimbra.tasks.service.impl;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.IModelHelper;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.message.Message;
import fr.openent.zimbra.model.message.RecallMail;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.tasks.service.DbActionService;
import fr.openent.zimbra.tasks.service.DbTaskService;
import fr.openent.zimbra.tasks.service.QueueService;
import fr.openent.zimbra.utils.DateUtils;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

public class RecallQueueServiceImpl extends QueueService<RecallTask> {
    public RecallQueueServiceImpl(String schema, DbTaskService<RecallTask> dbTaskService, DbActionService dbActionService) {
        super(schema, dbTaskService, dbActionService);
        this.dbTaskService = dbTaskService;
        this.actionType = ActionType.RECALL;
    }

    @Override
    protected List<RecallTask> createTasksFromData(Action<RecallTask> action, JsonArray taskData) throws Exception {
        return IModelHelper.toList(taskData, data -> new RecallTask(
                data.getInteger(Field.ID),
                TaskStatus.PENDING,
                DateUtils.parseDate(data.getString(Field.LAST_UPDATE, ""), DateUtils.DATE_FORMAT_SQL_WITHOUT_MILLI),
                action,
                null,
                data.getString(Field.RECEIVER_ID),
                data.getString(Field.RECIPIENT_ADDRESS),
                data.getInteger(Field.RETRY)));
    }

    protected RecallTask createTaskFromData(JsonObject taskData, Action<RecallTask> action) throws Exception {
        JsonObject recallData = new JsonObject(taskData.getString(Field.RECALL_MAIL));
        Message message = Message.fromZimbra(new JsonObject().put(Field.MID, recallData.getString(Field.MESSAGE_ID)));
        return new RecallTask(      taskData,
                                    action,
                                    new RecallMail(taskData.getInteger(Field.RECALL_MAIL_ID), message, recallData.getString(Field.USER_MAIL))
        );
    }

}
