package fr.openent.zimbra.service.impl;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.service.DbMailService;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.util.List;

public class ReturnedMailService {

    public ReturnedMailService() {
    }

    /**
     * Insert in database request of returned mail
     * @param returnedMail Object which contains all data to insert (user_id, user_name, object, recipient etc..)
     */
    public void insertReturnedMail(JsonObject returnedMail, Handler<Either<String, JsonObject>> handler) {
        String query = "INSERT INTO " + Zimbra.zimbraSchema + ".mail_returned(" +
                "user_id, user_name, structure_id, object, number_message, recipient, statut, comment)" +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) returning id;";
        JsonArray params = new fr.wseduc.webutils.collections.JsonArray()
                .add(returnedMail.getString("userId"))
                .add(returnedMail.getString("userName"))
                .add(returnedMail.getString("structureId"))
                .add(returnedMail.getString("subject"))
                .add(returnedMail.getInteger("nb_messages"))
                .add(returnedMail.getJsonArray("to"))
                .add("WAITING")
                .add(returnedMail.getString("comment"));
        Sql.getInstance().prepared(query, params, SqlResult.validUniqueResultHandler(handler));
    }


    /**
     * Get all returned mail by id structure
     * @param idStructure Id of a structure
     */
    public void getMailReturned(String idStructure, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * " +
                "FROM " + Zimbra.zimbraSchema + ".mail_returned " +
                "WHERE structure_id = ?; ";
        JsonArray params = new fr.wseduc.webutils.collections.JsonArray()
                .add(idStructure);
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    /**
     * Get all returned mail by ids
     * @param ids List of returnedMail ids
     */
    public void getMailReturnedByIds(List<String> ids, Handler<Either<String, JsonArray>> handler) {
        String query = "SELECT * " +
                "FROM " + Zimbra.zimbraSchema + ".mail_returned " +
                "WHERE id IN " + Sql.listPrepared(ids);
        JsonArray params = new fr.wseduc.webutils.collections.JsonArray();
        for (int i = 0; i < ids.size(); i++) {
            params.add(Integer.parseInt(ids.get(i)));
        }
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }

    /**
     * Change statut of returnedMail from WAITING to REMOVED
     * @param ids List of returnedMail ids
     */
    public void updateStatut(List<String> ids, Handler<Either<String, JsonArray>> handler) {
        String query = "UPDATE " + Zimbra.zimbraSchema + ".mail_returned " +
                "SET statut = 'REMOVED' " +
                "WHERE id IN " + Sql.listPrepared(ids);
        JsonArray params = new fr.wseduc.webutils.collections.JsonArray();
        for (int i = 0; i < ids.size(); i++) {
            params.add(Integer.parseInt(ids.get(i)));
        }
        Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(handler));
    }
}
