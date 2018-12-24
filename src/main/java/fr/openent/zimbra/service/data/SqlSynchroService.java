package fr.openent.zimbra.service.data;

import fr.openent.zimbra.model.Group;
import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.JsonHelper;
import fr.openent.zimbra.model.constant.SynchroConstants;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.util.List;

public class SqlSynchroService {

    private static final String UAI = "uai";
    private static final String IS_DEPLOYED = "is_deployed";
    private static final String DATE_MODIFIED = "date_modified";

    public static final String SYNCHRO_ID = "synchroid";
    private static final String SYNCHRO_MAILLINGLIST = "maillinglist";
    private static final String SYNCHRO_STATUS = "status";

    public static final String USER_IDROW = "id";
    public static final String USER_IDUSER = "id_user";
    private static final String USER_SYNCID = "id_synchro";
    private static final String USER_SYNCDATE = "synchro_date";
    private static final String USER_SYNCTYPE = "synchro_type";
    public static final String USER_SYNCACTION = "action_type";
    private static final String USER_STATUS = "status";


    private final Sql sql;

    private final String deployedStructuresTable;
    private final String synchroTable;
    private final String userSynchroTable;

    private static Logger log = LoggerFactory.getLogger(SqlSynchroService.class);

    public SqlSynchroService(String schema) {
        this.sql = Sql.getInstance();
        this.deployedStructuresTable = schema + ".deployed_structures";
        this.synchroTable = schema + ".synchro";
        this.userSynchroTable = schema + ".synchro_user";
    }

    public void getDeployedStructures(Handler<AsyncResult<List<String>>> handler) {
        String query = "SELECT " + UAI + " FROM "
                + deployedStructuresTable + " WHERE " + IS_DEPLOYED + " = true ";

        sql.prepared(query, new JsonArray(), SqlResult.validResultHandler(res -> {
            if(res.isLeft()) {
                handler.handle(Future.failedFuture(res.left().getValue()));
            } else {
                try {
                    List<String> structuresList = JsonHelper.extractValueFromJsonObjects(res.right().getValue(), UAI);
                    handler.handle(Future.succeededFuture(structuresList));
                } catch (IllegalArgumentException e) {
                    log.error("Invalid model in database : " + res.right().getValue().toString());
                    handler.handle(Future.failedFuture("Invalid model in database"));
                }
            }
        }));
    }

    //todo delete old structures
    public void updateDeployedStructures(List<String> newStructures, List<String> toDeleteStructures,
                                  Handler<AsyncResult<JsonObject>> handler) {
        StringBuilder query = new StringBuilder("INSERT INTO " + deployedStructuresTable)
                .append(String.format("(%s,%s,%s) ", UAI, DATE_MODIFIED, IS_DEPLOYED))
                .append("VALUES ");
        boolean first = true;
        for(int i = 0 ; i < newStructures.size() ; i++) {
            query.append(String.format("%s(?,now(),true) ", first ? "" : ","));
            first = false;
        }
        query.append("ON CONFLICT(" + UAI + ") DO UPDATE SET ")
                .append(UAI + " = excluded."+ UAI + ", ")
                .append(DATE_MODIFIED + " = now(), ")
                .append(IS_DEPLOYED + " = true");

        sql.prepared(query.toString(), new JsonArray(newStructures),
                SqlResult.validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
    }

    public void initializeSynchro(String maillingList, Handler<AsyncResult<JsonObject>> handler) {
        String query = "INSERT INTO " + synchroTable
                + String.format("(%s,%s) ", SYNCHRO_MAILLINGLIST, SYNCHRO_STATUS)
                + "VALUES (?, ?) "
                + "RETURNING id as " + SYNCHRO_ID;

        JsonArray params = new JsonArray().add(maillingList).add(SynchroConstants.STATUS_TODO);

        sql.prepared(query, params,
                SqlResult.validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
    }

    public void addUsersToSynchronize(int idSynchro, List<String> users, String modification,
                               Handler<AsyncResult<JsonObject>> handler) {
        if(users.isEmpty()) {
            handler.handle(Future.succeededFuture(new JsonObject()));
            return;
        }
        StringBuilder query = new StringBuilder("INSERT INTO " + userSynchroTable)
                .append(String.format("(%s,%s,%s,%s,%s) ",
                        USER_SYNCID, USER_IDUSER, USER_SYNCTYPE, USER_SYNCACTION, USER_STATUS));
        String delimiter = "VALUES";
        for(int i = 0 ; i < users.size() ; i++) {
            query.append(String.format("%s(%d,?,'%s','%s','%s')",
                    delimiter, idSynchro, SynchroConstants.SYNC_DAILY, modification, SynchroConstants.STATUS_TODO));
            delimiter = ",";
        }
        sql.prepared(query.toString(), new JsonArray(users),
                SqlResult.validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
    }

    public void fetchUserToSynchronize(Handler<AsyncResult<JsonObject>> handler) {
        // Get an user in TO-DO state from database
        // Explanation for : FOR UPDATE SKIP LOCKED :
        // https://dba.stackexchange.com/questions/69471/postgres-update-limit-1
        String query = "UPDATE " + userSynchroTable
                + " SET " + USER_STATUS + "='" + SynchroConstants.STATUS_INPROGRESS + "'"
                + ", " + USER_SYNCDATE+ "=now()"
                + " WHERE " + USER_IDUSER + "= ("
                    + "SELECT " + USER_IDUSER
                    + " FROM " + userSynchroTable
                    + " WHERE " + USER_STATUS + "='" + SynchroConstants.STATUS_TODO + "'"
                    + " LIMIT 1"
                    + " FOR UPDATE SKIP LOCKED"
                + " ) "
                + " RETURNING " + USER_IDUSER + "," + USER_SYNCACTION + "," + USER_IDROW;
        sql.prepared(query, new JsonArray(),
                SqlResult.validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
    }

    public void updateSynchroUser(int idRow, String state, String logs, Handler<AsyncResult<JsonObject>> handler) {
        // TODO update synchro user
    }
}
