/*
 * Copyright (c) Région Ile-de-France, Région Nouvelle-Aquitaine, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.zimbra.service.data;


import fr.openent.zimbra.Zimbra;
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
    private static final String AB_SYNC_DATE = "ab_sync_date";

    public static final String SYNCHRO_ID = "id";
    public static final String SYNCHRO_MAILLINGLIST = "maillinglist";
    private static final String SYNCHRO_STATUS = "status";
    public static final String SYNCHRO_AGG_LOGS = "aggregated_logs";
    public static final String SYNCHRO_DATE = "date_synchro";

    public static final String USER_IDROW = "id";
    public static final String USER_IDUSER = "id_user";
    private static final String USER_SYNCID = "id_synchro";
    private static final String USER_SYNCDATE = "synchro_date";
    private static final String USER_SYNCTYPE = "synchro_type";
    public static final String USER_SYNCACTION = "synchro_action";
    private static final String USER_STATUS = "status";
    private static final String USER_LOGID = "id_logs";

    private static final String LOGS_ID = "id";
    private static final String LOGS_CONTENT = "content";

    private final int INSERT_PAGINATION;


    private final Sql sql;

    private final String deployedStructuresTable;
    private final String synchroTable;
    private final String userSynchroTable;
    private final String synchroLogsTable;

    private static Logger log = LoggerFactory.getLogger(SqlSynchroService.class);

    public SqlSynchroService(String schema) {
        this.sql = Sql.getInstance();
        this.deployedStructuresTable = schema + ".deployed_structures";
        this.synchroTable = schema + ".synchro";
        this.userSynchroTable = schema + ".synchro_user";
        this.synchroLogsTable = schema + ".synchro_logs";
        INSERT_PAGINATION = Zimbra.appConfig.getSqlInsertPaginationSize();
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
                    log.error("Invalid data in database : " + res.right().getValue().toString());
                    handler.handle(Future.failedFuture("Invalid data in database"));
                }
            }
        }));
    }

    //todo delete old structures
    public void updateDeployedStructures(List<String> newStructures,
                                         @SuppressWarnings("unused") List<String> toDeleteStructures,
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


    public void updateStructureForAbSync(String structureUAI, Handler<AsyncResult<JsonObject>> handler) {
        String query = "UPDATE " + deployedStructuresTable;
        query += " SET " + AB_SYNC_DATE + " = now() ";
        query += "WHERE " + UAI + " = ? ";
        query += "AND " + AB_SYNC_DATE + " IS NULL ";
        query += "OR " + AB_SYNC_DATE + " < (now() - '1 day'::interval) ";
        query += "RETURNING " + deployedStructuresTable + "." + UAI;

        sql.prepared(query, new JsonArray().add(structureUAI),
                SqlResult.validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
    }


    /**
     * Initialize a synchronisation
     * @param maillingList mailling to use for reporting
     * @param handler result
     */
    public void initializeSynchro(String maillingList, Handler<AsyncResult<JsonObject>> handler) {
        String query = "INSERT INTO " + synchroTable
                + String.format("(%s,%s) ", SYNCHRO_MAILLINGLIST, SYNCHRO_STATUS)
                + "VALUES (?, ?) "
                + "RETURNING id as " + SYNCHRO_ID;

        JsonArray params = new JsonArray().add(maillingList).add(SynchroConstants.STATUS_TODO);

        sql.prepared(query, params,
                SqlResult.validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
    }


    /**
     * Add a list of users to synchronize, for a synchro
     * @param idSynchro id of current synchro
     * @param users list of users ids
     * @param modification modification type (CREATE, MODIFY or DELETE)
     * @param handler result
     */
    public void addUsersToSynchronize(int idSynchro, List<String> users, String modification,
                                       Handler<AsyncResult<JsonObject>> handler) {
        addUsersToSynchronize(idSynchro, users, modification, 0, handler);
    }

    private void addUsersToSynchronize(int idSynchro, List<String> users, String modification,
                                      int start, Handler<AsyncResult<JsonObject>> handler) {
        if(users.isEmpty() || start >= users.size()) {
            handler.handle(Future.succeededFuture(new JsonObject()));
            return;
        }

        String statusCondition = String.format("%s='%s'", USER_STATUS, SynchroConstants.STATUS_TODO);

        StringBuilder query = new StringBuilder("WITH new_values (new_user_id) AS ( ");

        String delimiter = "VALUES ";
        int i;
        for( i = 0 ; (start + i) < users.size() && i < INSERT_PAGINATION ; i++) {
            query.append(String.format("%s(?)",delimiter));
            delimiter = ",";
        }
        query.append(") INSERT INTO ")
                .append(userSynchroTable)
                .append(String.format("(%s,%s,%s,%s,%s) ",
                        USER_SYNCID, USER_IDUSER, USER_SYNCTYPE, USER_SYNCACTION, USER_STATUS))
                .append(String.format("SELECT %d,new_user_id,'%s','%s','%s'",
                        idSynchro, SynchroConstants.SYNC_DAILY, modification, SynchroConstants.STATUS_TODO))
                .append("FROM new_values ")
                .append("WHERE NOT EXISTS ( SELECT 1 FROM ")
                .append(userSynchroTable)
                .append(String.format(" WHERE %s=new_user_id", USER_IDUSER))
                .append(String.format(" AND %s", statusCondition))
                .append(")");
        List<String> extract = users.subList(start, start + i);
        sql.prepared(query.toString(), new JsonArray(extract),
                SqlResult.validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(
                        res -> {
                            if(res.failed()) {
                                handler.handle(res);
                            } else {
                                addUsersToSynchronize(idSynchro, users, modification,
                                        start + INSERT_PAGINATION, handler);
                            }
                        }
                )));
    }


    // Get an user in TO-DO state from database in order to synchronize it
    public void fetchUserToSynchronize(Handler<AsyncResult<JsonObject>> handler) {
        // Explanation for : FOR UPDATE SKIP LOCKED :
        // https://dba.stackexchange.com/questions/69471/postgres-update-limit-1
        String query = "UPDATE " + userSynchroTable
                + " SET " + USER_STATUS + "='" + SynchroConstants.STATUS_INPROGRESS + "'"
                + ", " + USER_SYNCDATE+ "=now()"
                + " WHERE " + USER_IDROW + "= ("
                    + "SELECT " + USER_IDROW
                    + " FROM " + userSynchroTable
                    + " WHERE " + USER_STATUS + "='" + SynchroConstants.STATUS_TODO + "'"
                    + " LIMIT 1"
                    + " FOR UPDATE SKIP LOCKED"
                + " ) "
                + " RETURNING " + USER_IDUSER + "," + USER_SYNCACTION + "," + USER_IDROW;
        sql.prepared(query, new JsonArray(),
                SqlResult.validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
    }


    /**
     * Update a user to synchronize
     * @param idRow row to update
     * @param state new state
     * @param logs create an entry in synchro_logs table if not empty
     * @param handler result
     */
    public void updateSynchroUser(int idRow, String state, String logs, Handler<AsyncResult<JsonObject>> handler) {
        JsonArray params = new JsonArray();
        String logIdQuery = "";
        String logCondition = "";
        if(!logs.isEmpty()) {
            logIdQuery = "WITH tmp_log AS ( "
                            + "INSERT INTO " + synchroLogsTable + String.format("(%s) ", LOGS_CONTENT)
                            + "VALUES (?) "
                            + "RETURNING id as logid"
                        + ") ";
            logCondition = String.format(", %s=tmp_log.logid FROM tmp_log ", USER_LOGID);
            params.add(logs);
        }
        String query = logIdQuery
                + "UPDATE " + userSynchroTable
                + String.format(" SET %s=? ", USER_STATUS)
                + logCondition
                + String.format("WHERE %s=? ", USER_IDROW);
        params.add(state).add(idRow);
        sql.prepared(query, params,
                SqlResult.validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
    }


    /**
     * Update all Synchros status
     * @param oldStatus status to change
     * @param newStatus new status
     * @param handler return updated synchros ids
     */
    public void updateSynchros(String oldStatus, String newStatus, Handler<AsyncResult<JsonArray>> handler) {
        JsonArray params = new JsonArray();
        String query = "UPDATE " + synchroTable
                + String.format(" SET %s=? ", SYNCHRO_STATUS)
                + String.format("WHERE %s=?", SYNCHRO_STATUS)
                + " returning " + SYNCHRO_ID;
        params.add(newStatus).add(oldStatus);
        sql.prepared(query, params,
                SqlResult.validResultHandler(AsyncHelper.getJsonArrayEitherHandler(handler)));
    }


    /**
     * Get information from synchro  : id, mailling list, aggregation of all logs
     * @param synchroId synchro id
     * @param handler result (one line per synchro)
     */
    public void getSynchroInfos(String synchroId, Handler<AsyncResult<JsonObject>> handler) {
        JsonArray params = new JsonArray();
        String query = String.format("SELECT %s.%s AS %s, %s.%s AS %s, %s.%s as %s, ",
                                        synchroTable, SYNCHRO_ID, SYNCHRO_ID,
                                        synchroTable, SYNCHRO_MAILLINGLIST, SYNCHRO_MAILLINGLIST,
                                        synchroTable, SYNCHRO_DATE, SYNCHRO_DATE)
                + String.format("string_agg(%s.%s || ' ' || %s.%s, '<br/>\r\n') as %s ",
                                    userSynchroTable, USER_IDUSER,
                                    synchroLogsTable, LOGS_CONTENT,
                                    SYNCHRO_AGG_LOGS)
                + "FROM " + synchroTable
                + " LEFT JOIN " + userSynchroTable
                + String.format(" ON %s.%s=%s.%s", synchroTable, SYNCHRO_ID, userSynchroTable, USER_SYNCID)
                + " LEFT JOIN " + synchroLogsTable
                + String.format(" ON %s.%s=%s.%s", synchroLogsTable, LOGS_ID, userSynchroTable, USER_LOGID)
                + String.format(" WHERE %s.%s=?",
                                synchroTable, SYNCHRO_ID)
                + String.format(" GROUP BY %s.%s", synchroTable, SYNCHRO_ID);
        params.add(synchroId);
        sql.prepared(query, params,
                SqlResult.validUniqueResultHandler(AsyncHelper.getJsonObjectEitherHandler(handler)));
    }
}
