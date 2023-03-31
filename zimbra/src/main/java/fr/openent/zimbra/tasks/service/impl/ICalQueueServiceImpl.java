package fr.openent.zimbra.tasks.service.impl;

import fr.openent.zimbra.core.enums.ActionType;
import fr.openent.zimbra.helper.IModelHelper;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.ICalTask;
import fr.openent.zimbra.tasks.service.DbActionService;
import fr.openent.zimbra.tasks.service.DbTaskService;
import fr.openent.zimbra.tasks.service.QueueService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

public class ICalQueueServiceImpl extends QueueService<ICalTask> {
    private final String icalTable = "ical_request_tasks";

    public ICalQueueServiceImpl(String schema, DbTaskService<ICalTask> dbTaskService, DbActionService dbActionService) {
        super(schema, dbTaskService, dbActionService);
        this.taskTable = this.schema + "." + icalTable;
        this.actionType = ActionType.ICAL;
    }

    @Override
    protected List<ICalTask> createTasksFromData(Action<ICalTask> action, JsonArray taskData) {
        return IModelHelper.toList(taskData, ICalTask.class);
    }

    @Override
    protected ICalTask createTaskFromData(JsonObject taskData, Action<ICalTask> action) throws Exception {
        return new ICalTask(action, taskData);
    }

}
