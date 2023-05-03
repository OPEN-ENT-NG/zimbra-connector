package fr.openent.zimbra.tasks.service;

import fr.openent.zimbra.model.message.RecallMail;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import org.entcore.common.user.UserInfos;

import java.util.List;

public abstract class DbRecallMail {
    private final String schema;

    protected DbRecallMail(String schema) {
        this.schema = schema;
    }

    /**
     * Create recall mail in DB
     * @param recallMail    Model of recall mail
     * @param user          User infos
     * @return              Recall mail
     */
    public abstract Future<RecallMail> createRecallMail(RecallMail recallMail, UserInfos user);
    public abstract Future<List<RecallMail>> getRecallMailByStruct(String structureId);
    public abstract Future<Void> acceptRecall(int recallId);
    public abstract Future<Void> acceptMultipleRecall(List<Integer> recallIds);
    public abstract Future<JsonArray> checkRecalledInMailList(String userId, JsonArray messageList);
    public abstract Future<Void> resetFailedTasks(List<Integer> recallIds);

    public abstract Future<Boolean> hasADMLDeleteRight(Integer recallId, UserInfos user);
    public abstract Future<Void> deleteRecall(Integer recallId);
}
