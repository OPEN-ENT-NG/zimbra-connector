package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.model.RecallMail;
import fr.openent.zimbra.service.DbMailService;
import fr.openent.zimbra.service.RecallMailService;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.NotImplementedException;

import java.util.List;
import java.util.UUID;

public class RecallMailServiceImpl implements RecallMailService {
    public static ConfigManager appConfig;

    private final DbMailService dbMailService;
    private final EventBus eb;

    private static Logger log = LoggerFactory.getLogger(RecallMailServiceImpl.class);

    public RecallMailServiceImpl(DbMailService dbMailService, EventBus eb) {
        this.dbMailService = dbMailService;
        this.eb = eb;
    }

    public Future<List<RecallMail>> getRecallMails () {
        throw new NotImplementedException();
    }

    public Future<RecallMail> createRecallMail (String messageId, String comment) {
        throw new NotImplementedException();
    }

    public Future<RecallMail> updateRecallMail (String recallMailId, String comment, String status) {
        throw new NotImplementedException();
    }

    public Future<List<UUID>> getUsers (String recallMailId) {
        // todo: get recall_mail from db
        // todo: call zimbra to retrieve user ids
        // todo: return user ids
        throw new NotImplementedException();
    }

    public Future<Void> deleteMessages (String recallMailId) {
        throw new NotImplementedException();
    }
}
