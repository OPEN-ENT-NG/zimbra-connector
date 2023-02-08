package fr.openent.zimbra.helper;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.model.task.RecallTaskInfo;
import io.vertx.core.json.JsonObject;

import java.util.UUID;
import java.util.stream.Collectors;

public class TaskHelper {
    private static boolean JSONContainsTaskInfos(JsonObject recallTaskInfos) {
        return  recallTaskInfos.containsKey(Field.ACTION_ID) &&
                recallTaskInfos.containsKey(Field.STATUS);
    }

    private static boolean JSONContainsRecallInfos(JsonObject recallTaskInfos) {
        return  recallTaskInfos.containsKey(Field.RECEIVER_ID) &&
                recallTaskInfos.containsKey(Field.RECALL_MAIL_ID) &&
                recallTaskInfos.containsKey(Field.RETRY);
    }

    public static RecallTaskInfo recallTaskInfoFromJSON(JsonObject recallTaskInfos) {
        if (JSONContainsTaskInfos(recallTaskInfos) && JSONContainsRecallInfos(recallTaskInfos)) {
            return null;
        }
        try {
            return new RecallTaskInfo(
                    recallTaskInfos.getInteger(Field.ACTION_ID),
                    TaskStatus.fromString(recallTaskInfos.getString(Field.STATUS)),
                    recallTaskInfos.getInteger(Field.RECALL_MAIL_ID),
                    recallTaskInfos.getJsonArray(Field.RECEIVERS_ID).stream().map(e -> UUID.fromString(e.toString())).collect(Collectors.toList()),
                    recallTaskInfos.getInteger(Field.RETRY));
        } catch (Exception e) {
            return null;
        }
    }
}
