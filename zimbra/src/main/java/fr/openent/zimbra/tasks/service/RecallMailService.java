package fr.openent.zimbra.tasks.service;


import fr.openent.zimbra.model.message.RecallMail;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import org.entcore.common.user.UserInfos;

import java.util.List;
import java.util.UUID;

public interface RecallMailService {

    /**
     * Get Recall mails
     * @return
     */
    public Future<List<RecallMail>> getRecallMails ();

    public Future<Void> acceptRecall(int recallId);

    /**
     * Get Recall mails for a specific structure
     * @return
     */
    public Future<List<RecallMail>> getRecallMailsForOneStructure (String structureId);

    /**
     *
     * @param messageId
     * @param comment
     * @return
     */
    public Future<RecallMail> createRecallMail (UserInfos user, String messageId, String comment);

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
    public Future<List<UUID>> getUsers (String recallMailId);

    /**
     *
     * @param recallMail
     * @return
     */
    public Future<Void> deleteMessage (RecallMail recallMail, UserInfos user, String receiverEmail, String senderEmail);

    public Future<JsonArray> renderRecallMails(UserInfos user, JsonArray messageList);

    Future<Void> acceptMultipleRecall(List<Integer> recallIds);

    Future<Void> deleteRecallMail(Integer recallId, UserInfos user);

}
