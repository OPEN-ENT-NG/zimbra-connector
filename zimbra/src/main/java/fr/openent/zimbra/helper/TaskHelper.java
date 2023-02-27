package fr.openent.zimbra.helper;

import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.task.RecallTask;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class TaskHelper {
    protected static final Logger log = LoggerFactory.getLogger(TaskHelper.class);

    /**
     * Convert JsonArray containing data about tasks to a list of task model
     * @param action    Tasks action
     * @param tasksData JsonArray with the data
     * @return          Null if one of the conversion failed, the list otherwise.
     */
    public static List<RecallTask> convertJsonListToTask(Action<RecallTask> action, JsonArray tasksData) throws Exception {
        List<RecallTask> recallTaskList = new ArrayList<>();
        Iterator<JsonObject> recallTaskIterator = tasksData.stream().filter(taskData -> taskData instanceof JsonObject).map(JsonObject.class::cast).iterator();

        while (recallTaskIterator.hasNext()) {
            try {
                recallTaskList.add(new RecallTask(recallTaskIterator.next(), action, null));
            } catch (Exception e) {
                String errMessage = String.format("[Zimbra@%s::convertJsonListToTask]:  " +
                                "error while fetching model: %s",
                        TaskHelper.class.getSimpleName(), e.getMessage());
                log.error(errMessage);
                return null;
            }
        }
        return recallTaskList;
    }
}
