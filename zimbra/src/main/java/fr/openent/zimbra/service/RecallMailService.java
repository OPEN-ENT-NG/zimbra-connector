package fr.openent.zimbra.service;

import fr.openent.zimbra.model.RecallMail;
import io.vertx.core.Future;

import java.util.List;

public interface RecallMailService {

    /**
     * Get Recall mails
     * @return
     */
    public Future<List<RecallMail>> getRecallMails ();

    /**
     *
     * @param messageId
     * @param comment
     * @return
     */
    public Future<RecallMail> createRecallMail (String messageId, String comment);

    /**
     *
     * @param recallMailId
     * @param comment
     * @param status
     * @return
     */
    public Future<RecallMail> updateRecallMail (String recallMailId, String comment, String status);

    /**
     *
     * @param recallMailId
     * @return
     */
    public Future<List<Object>> getMessages (String recallMailId);

    /**
     *
     * @param recallMailId
     * @return
     */
    public Future<Void> deleteMessages (String recallMailId);
}
