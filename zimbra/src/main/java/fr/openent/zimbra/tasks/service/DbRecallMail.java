package fr.openent.zimbra.tasks.service;

import fr.openent.zimbra.core.enums.ActionStatus;
import fr.openent.zimbra.model.message.RecallMail;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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
    public abstract Future<JsonObject> acceptRecall(int recallId);

    public abstract Future<JsonArray> checkRecalledInMailList(String userId, JsonArray messageList);
}
