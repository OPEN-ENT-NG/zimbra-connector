package fr.openent.zimbra.tasks.service.impl.data;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.helper.PromiseHelper;
import fr.openent.zimbra.model.message.RecallMail;
import fr.openent.zimbra.tasks.service.DbRecallMail;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;

import java.util.Date;

public class SqlRecallMailService extends DbRecallMail {
    private final String recallMailTable;
    private static Logger log = LoggerFactory.getLogger(SqlRecallMailService.class);

    public SqlRecallMailService(String schema) {
        super(schema);
        recallMailTable = schema + "." + "recall_mails";
    }

    private Future<JsonObject> insertRecallMailDb(RecallMail recallMail, UserInfos user) {
        Promise<JsonObject> promise = Promise.promise();

        String query = "INSERT INTO " +
                this.recallMailTable +
                " (" + Field.ACTION_ID + "," + Field.USER_NAME + "," + Field.MESSAGE_ID + "," + Field.STRUCTURES + "," + Field.OBJECT + "," + Field.COMMENT + "," + Field.MAIL_DATE +  ") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING " + Field.ID + ";";

        JsonArray values = new JsonArray();
        values
                .add(recallMail.getAction().getId())
                .add(user.getUsername())
                .add(recallMail.getMessage().getMailId())
                .add(new JsonArray(user.getStructures()))
                .add(recallMail.getMessage().getSubject())
                .add(recallMail.getComment())
                .add(new Date(recallMail.getMessage().getDate()).toString());

        Sql.getInstance().prepared(query, values, SqlResult.validUniqueResultHandler(PromiseHelper.handlerJsonObject(promise)));

        return promise.future();
    }

    @Override
    public Future<RecallMail> createRecallMail(RecallMail recallMail, UserInfos user) {
        Promise<RecallMail> promise = Promise.promise();

        insertRecallMailDb(recallMail, user)
                .onSuccess(recallData -> {
                    try {
                        recallMail.setId(recallData.getInteger(Field.ID));
                        promise.complete(recallMail);
                    } catch (Exception e){
                        String errMessage = String.format("[Zimbra@%s::createRecallMail]:  " +
                                        "fail create recall mail: %s",
                                this.getClass().getSimpleName(), e.getMessage());
                        log.error(errMessage);
                        promise.fail(ErrorEnum.ERROR_CREATING_RECALL_MAIL.method());
                    }
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::createRecallMail]:  " +
                                    "fail : %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ERROR_CREATING_RECALL_MAIL.method());
                });

        return promise.future();
    }
}
