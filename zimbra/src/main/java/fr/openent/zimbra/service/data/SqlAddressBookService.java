package fr.openent.zimbra.service.data;

import fr.openent.zimbra.Zimbra;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@SuppressWarnings("FieldCanBeLocal")
public class SqlAddressBookService {


    private final Sql sql;

    private final String ABOOK_USERID = "userid";

    private final String abookSyncTable;
    private final String ABOOKSYNCTABLE_DATESYNCHRO = "date_synchro";

    private final String abookPurgeTable;
    private final String ABOOKPURGETABLE_DATEPURGE = "date_purge";

    private static final Logger log = LoggerFactory.getLogger(SqlAddressBookService.class);

    public class SqlAbookSyncResult {
        public String userId;
        public Instant lastSync;

        public SqlAbookSyncResult(JsonObject bddResult) {
            userId = bddResult.getString(ABOOK_USERID, "");
            String dateStr = bddResult.getString(ABOOKSYNCTABLE_DATESYNCHRO, "");
            try {
                lastSync = LocalDateTime.parse(
                        dateStr,
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME
                ).toInstant(ZoneOffset.UTC);
            } catch (Exception e) {
                log.error("SqlAbookSyncResult Error when parsing date " + dateStr + "for user " + userId, e);
                lastSync = Instant.ofEpochMilli(0L);
            }
        }

        public boolean mustBeSynced() {
            Instant ttlDeadline = lastSync.plus(Zimbra.appConfig.getAddressBookSynchroTtl(), ChronoUnit.MINUTES);
            return Instant.now().isAfter(ttlDeadline);
        }
    }

    public SqlAddressBookService(String schema) {
        this.sql = Sql.getInstance();
        this.abookSyncTable = schema + ".address_book_sync";
        this.abookPurgeTable = schema + ".purge_emailed_contacts";
    }

    public void getUserSyncInfo(String userId, Handler<SqlAbookSyncResult> handler) {
        // SELECT userid, date_synchro FROM zimbra.address_book_sync WHERE userid=?
        String query = "SELECT " + ABOOK_USERID + ", " + ABOOKSYNCTABLE_DATESYNCHRO
                + " FROM " + abookSyncTable
                + " WHERE " + ABOOK_USERID + "=?";

        sql.prepared(query, new JsonArray().add(userId), SqlResult.validUniqueResultHandler(result -> {
            if(result.isLeft()) {
                log.error("Error in getUserSyncInfo for user " + userId + " : " + result.left().getValue());
                handler.handle(null);
            } else {
                JsonObject bddresult = result.right().getValue();
                SqlAbookSyncResult finalResult;
                if(bddresult.isEmpty()) {
                    finalResult = null;
                } else {
                    finalResult = new SqlAbookSyncResult(bddresult);
                }
                handler.handle(finalResult);
            }
        }));
    }

    public void markUserAsSynced(String userId) {
        // INSERT INTO zimbra.address_book_sync(userid,date_synchro) VALUES(?,now()) ON CONFLICT(userid) DO UPDATE SET date_synchro=now()
        String query = "INSERT INTO " + abookSyncTable + "(" + ABOOK_USERID + "," + ABOOKSYNCTABLE_DATESYNCHRO + ")"
                + " VALUES(?,now())" +
                " ON CONFLICT(" + ABOOK_USERID + ")" +
                " DO UPDATE SET " + ABOOKSYNCTABLE_DATESYNCHRO + "=now()";
        sql.prepared(query, new JsonArray().add(userId), SqlResult.validRowsResultHandler( result -> {
            if(result.isLeft()) {
                log.error("Error when update db in markUserAsSynced for user " + userId + " : " + result.left().getValue());
            }
        }));
    }

    public void purgeUserSyncAddressBook(String userId, Handler<Either<String, JsonObject>> handler) {
        String query = "DELETE FROM " + abookSyncTable + " WHERE userid = ?;";
        sql.prepared(query, new JsonArray().add(userId), SqlResult.validUniqueResultHandler(handler));
    }

    public void getUserPurgeEmailedContacts(String userId, Handler<JsonObject> handler) {
        // SELECT userid, date_purge FROM zimbra.purge_emailed_contacts WHERE userid=?
        String query = "SELECT " + ABOOK_USERID + ", " + ABOOKPURGETABLE_DATEPURGE
                + " FROM " + abookPurgeTable
                + " WHERE " + ABOOK_USERID + "=?";

        sql.prepared(query, new JsonArray().add(userId), SqlResult.validUniqueResultHandler(result -> {
            if(result.isLeft()) {
                log.error("Error in getUserPurgeEmailedContacts for user " + userId + " : " + result.left().getValue());
                handler.handle(null);
            } else {
                JsonObject bddresult = result.right().getValue();
                if(bddresult.isEmpty()) {
                    handler.handle(null);
                } else {
                    handler.handle(bddresult);
                }
            }
        }));
    }

    public void markUserAsPurged(String userId) {
        // INSERT INTO zimbra.purge_emailed_contacts(userid,date_purge) VALUES(?,now())
        String query = "INSERT INTO " + abookPurgeTable + "(" + ABOOK_USERID + "," + ABOOKPURGETABLE_DATEPURGE + ")"
                + " VALUES(?,now())";
        sql.prepared(query, new JsonArray().add(userId), SqlResult.validRowsResultHandler( result -> {
            if(result.isLeft()) {
                log.error("Error when update db in markUserAsPurged for user " + userId + " : " + result.left().getValue());
            }
        }));
    }

    public void truncatePurgeTable() {
        // TRUNCATE TABLE zimbra.purge_emailed_contacts
        String query = "TRUNCATE TABLE " + abookPurgeTable;
        sql.prepared(query, new JsonArray(), SqlResult.validRowsResultHandler( result -> {
            if(result.isLeft()) {
                log.error("Error when truncate purge table in truncatePurgeTable : " + result.left().getValue());
            }
        }));
    }
}
