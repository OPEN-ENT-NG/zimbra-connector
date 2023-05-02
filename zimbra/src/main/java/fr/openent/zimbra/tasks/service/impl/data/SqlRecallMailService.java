package fr.openent.zimbra.tasks.service.impl.data;

import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.core.enums.ActionStatus;
import fr.openent.zimbra.core.enums.ErrorEnum;
import fr.openent.zimbra.core.enums.TaskStatus;
import fr.openent.zimbra.helper.IModelHelper;
import fr.openent.zimbra.helper.PromiseHelper;
import fr.openent.zimbra.model.action.Action;
import fr.openent.zimbra.model.message.Message;
import fr.openent.zimbra.model.message.RecallMail;
import fr.openent.zimbra.model.message.ZimbraEmail;
import fr.openent.zimbra.model.task.RecallTask;
import fr.openent.zimbra.tasks.service.DbRecallMail;
import fr.openent.zimbra.utils.DateUtils;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class SqlRecallMailService extends DbRecallMail {
    private final String recallMailTable;
    private final String actionTable;
    private final String recallTaskTable;
    private static final Logger log = LoggerFactory.getLogger(SqlRecallMailService.class);
    public static final String ADDR_TYPE_FROM = "f";

    public SqlRecallMailService(String schema) {
        super(schema);
        recallMailTable = schema + "." + "recall_mails";
        this.actionTable = schema + "." + "actions";
        this.recallTaskTable = schema + "." + "recall_recipient_tasks";
    }

    protected Future<JsonObject> insertRecallMailDb(RecallMail recallMail, UserInfos user) {
        Promise<JsonObject> promise = Promise.promise();

        String query = "INSERT INTO " +
                this.recallMailTable +
                " (" + Field.ACTION_ID + "," + Field.USER_NAME + "," + Field.USER_MAIL +"," + Field.LOCAL_MAIL_ID + "," + Field.MESSAGE_ID + "," + Field.STRUCTURES + "," + Field.OBJECT + "," + Field.COMMENT + "," + Field.MAIL_DATE +  ") " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING " + Field.ID + ";";

        String senderAddress = recallMail.getMessage().getEmailAddresses().stream().filter(addr -> addr.getAddrType().equals(ADDR_TYPE_FROM)).findFirst().map(ZimbraEmail::getAddress).orElse("");

        JsonArray values = new JsonArray();
        values
                .add(recallMail.getAction().getId())
                .add(user.getUsername())
                .add(senderAddress)
                .add(recallMail.getMessage().getId())
                .add(recallMail.getMessage().getMailId())
                .add(new JsonArray(user.getStructures().stream().map(id -> new JsonObject().put(Field.ID, id)).collect(Collectors.toList())))
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

    private Future<JsonArray> retrieveRecallByStruct(String structureId) {
        Promise<JsonArray> promise = Promise.promise();

        String query = "SELECT rm.*, json_agg(rct.*) as tasks, to_json(act.*) as action FROM " +
                this.recallMailTable + " AS rm " +
                "INNER JOIN " + this.recallTaskTable + " AS rct ON rct.action_id = rm.action_id " +
                "INNER JOIN " + this.actionTable + " AS act ON act.id = rm.action_id " +
                "WHERE ? IN (SELECT (json_array_elements(rm.structures)->>'id') " +
                "FROM " + this.recallMailTable + ") group by rm.id, act.id;";

        JsonArray values = new JsonArray().add(structureId);

        Sql.getInstance().prepared(query, values, SqlResult.validResultHandler(PromiseHelper.handlerJsonArray(promise)));

        return promise.future();
    }

    private List<RecallMail> createRecallMailInstancesForStruct(JsonArray mailList) throws Exception {

        return IModelHelper.toList(mailList, mailData -> {
            long date;
            Action<RecallTask> action = null;
            try {
                date = new SimpleDateFormat(DateUtils.DATE_FORMAT_SQL).parse(mailData.getString(Field.MAIL_DATE)).getTime();
                action = new Action<>(new JsonObject(mailData.getString(Field.ACTION)));
                action.addTasks(IModelHelper.toList(new JsonArray(mailData.getString(Field.TASKS)), taskData ->
                                new RecallTask(taskData.getInteger(Field.ID),
                                        TaskStatus.fromString(taskData.getString(Field.STATUS)),
                                        null,
                                        null,
                                        null,
                                        taskData.getString(Field.RECIPIENT_ADDRESS),
                                        taskData.getInteger(Field.RETRY))
                        )
                );
            } catch (Exception e) {
                date = -1;
            }

            return new RecallMail(
                    mailData.getInteger(Field.ID),
                    new Message(
                            null,
                            mailData.getString(Field.OBJECT),
                            mailData.getString(Field.MESSAGE_ID),
                            date
                    ),
                    action,
                    mailData.getString(Field.COMMENT),
                    mailData.getString(Field.USER_NAME)
            );
        });
    }

    @Override
    public Future<List<RecallMail>> getRecallMailByStruct(String structureId) {
        Promise<List<RecallMail>> promise = Promise.promise();

        retrieveRecallByStruct(structureId)
                .onSuccess(mailList -> {
                    try {
                        promise.complete(createRecallMailInstancesForStruct(mailList));
                    } catch (Exception e) {
                        String errMessage = String.format("[Zimbra@%s::getRecallMailByStruct]:  " +
                                        "fail to fetch recall mail: %s",
                                this.getClass().getSimpleName(), e.getMessage());
                        log.error(errMessage);
                        promise.fail(ErrorEnum.ERROR_FETCHING_MODEL.method());
                    }
                })
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::getRecallMailByStruct]:  " +
                                    "fail to retrieve recall mails data: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ERROR_RETRIEVING_MAIL.method());
                });
        return promise.future();
    }

    @Override
    public Future<Void> acceptRecall(int recallId) {
        Promise<Void> promise = Promise.promise();

        String query = "UPDATE " + this.actionTable +
                " SET approved = true " +
                "WHERE zimbra.actions.id = (select action_id from " + this.recallMailTable + " where id = ?);";

        JsonArray values = new JsonArray().add(recallId);

        Sql.getInstance().prepared(query, values, SqlResult.validUniqueResultHandler(res -> {
            if (res.isRight()) {
                promise.complete();
            } else {
                String errMessage = String.format("[Zimbra@%s::acceptRecall]:  " +
                                "error while updating recall acceptation in db: %s",
                        this.getClass().getSimpleName(), res.left().getValue());
                log.error(errMessage);
                promise.fail(ErrorEnum.ERROR_ACTION_UPDATE.method());
            }
        }));

        return promise.future();
    }

    public Future<Void> acceptMultipleRecall(List<Integer> recallIds) {
        Promise<Void> promise = Promise.promise();

        String query = "UPDATE " + this.actionTable +
                " SET approved = true " +
                "WHERE zimbra.actions.id IN (select action_id from " + this.recallMailTable + " where id IN " + Sql.listPrepared(recallIds) + ");";

        JsonArray values = new JsonArray(recallIds);

        Sql.getInstance().prepared(query, values, SqlResult.validUniqueResultHandler(res -> {
            if (res.isRight()) {
                promise.complete();
            } else {
                String errMessage = String.format("[Zimbra@%s::acceptMultipleRecall]:  " +
                                "error while updating recalls acceptation in db: %s",
                        this.getClass().getSimpleName(), res.left().getValue());
                log.error(errMessage);
                promise.fail(ErrorEnum.ERROR_ACTION_UPDATE.method());
            }
        }));

        return promise.future();
    }

    @Override
    public Future<JsonArray> checkRecalledInMailList(String userId, JsonArray messageList) {
        Promise<JsonArray> promise = Promise.promise();

        List<String> messageIdList = messageList  .stream()
                .filter(JsonObject.class::isInstance)
                .map(mail -> (JsonObject) mail)
                .map(mail -> mail.getString(Field.ID)).collect(Collectors.toList());

        String query = "SELECT rm.*, act.approved," +
                "count(rt.status) filter ( WHERE status = '" + TaskStatus.FINISHED.method() + "') AS finished," +
                "count(rt.status) filter ( WHERE status = '" + TaskStatus.PENDING.method() + "') AS pending," +
                "count(rt) AS total FROM " + this.recallMailTable + " AS rm " +
                "INNER JOIN " + this.actionTable + " AS act on act.id = rm.action_id " +
                "INNER JOIN " + this.recallTaskTable + " AS rt on rt.action_id = act.id " +
                "WHERE rm.local_mail_id in " + Sql.listPrepared(messageIdList) + " GROUP BY rm.id, act.approved;";

        JsonArray values = new JsonArray(messageIdList);

        Sql.getInstance().prepared(query, values, SqlResult.validResultHandler(PromiseHelper.handlerJsonArray(promise)));

        return promise.future();
    }

    private Future<List<String>> getRecallStructures(Integer recallId) {
        Promise<List<String>> promise = Promise.promise();

        String query = "SELECT rm.structures FROM " + recallMailTable + " AS rm WHERE rm.id = ? LIMIT 1;";

        JsonArray values = new JsonArray().add(recallId);

        Sql.getInstance().prepared(query, values, SqlResult.validUniqueResultHandler(list -> {
            if (list.isRight()) {
                List<String> structs;
                try {
                    structs = new JsonArray(list.right().getValue().getString(Field.STRUCTURES))
                            .stream()
                            .filter(JsonObject.class::isInstance)
                            .map(JsonObject.class::cast)
                            .map(struct -> struct.getString(Field.ID, "")).collect(Collectors.toList());

                } catch (Exception e) {
                    structs = new ArrayList<>();
                    String errMessage = String.format("[Zimbra@%s::getRecallStructures]:  " +
                                    "error while retrieving recall structures: %s",
                            this.getClass().getSimpleName(), e.getMessage());
                    log.error(errMessage);
                }
                promise.complete(structs);
            } else {
                String errMessage = String.format("[Zimbra@%s::getRecallStructures]:  " +
                                "error while retrieving recall structures: %s",
                        this.getClass().getSimpleName(), list.left().getValue());
                log.error(errMessage);
                promise.fail(ErrorEnum.FAIL_LIST_STRUCTURES.method());
            }
        }));

        return promise.future();
    }

    public Future<Boolean> hasADMLDeleteRight(Integer recallId, UserInfos user) {
        Promise<Boolean> promise = Promise.promise();

        getRecallStructures(recallId)
                .onSuccess(list -> promise.complete(!Collections.disjoint(list, user.getStructures())))
                .onFailure(err -> {
                    String errMessage = String.format("[Zimbra@%s::hasADMLDeleteRight]:  " +
                                    "error while retrieving recall structures: %s",
                            this.getClass().getSimpleName(), err.getMessage());
                    log.error(errMessage);
                    promise.fail(ErrorEnum.ADML_NO_RIGHT_STRUCTURES.method());
                });

        return promise.future();
    }

    public Future<Void> deleteRecall(Integer recallId) {
        Promise<Void> promise = Promise.promise();

        String query = "DELETE FROM " + actionTable + " AS act WHERE act.id = (SELECT rm.action_id FROM " + recallMailTable + " AS rm WHERE rm.id = ?)";

        JsonArray values = new JsonArray().add(recallId);

        Sql.getInstance().prepared(query, values, SqlResult.validResultHandler(res -> {
            if (res.isRight()) {
                promise.complete();
            } else {
                String errMessage = String.format("[Zimbra@%s::hasADMLDeleteRight]:  " +
                                "error while deleting recall: %s",
                        this.getClass().getSimpleName(), res.left().getValue());
                log.error(errMessage);
                promise.fail(ErrorEnum.FAIL_DELETE_RECALL.method());
            }
        }));

        return promise.future();
    }

}
