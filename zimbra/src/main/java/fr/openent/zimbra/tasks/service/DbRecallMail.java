package fr.openent.zimbra.tasks.service;

import fr.openent.zimbra.model.message.RecallMail;
import io.vertx.core.Future;
import org.entcore.common.user.UserInfos;

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
}
