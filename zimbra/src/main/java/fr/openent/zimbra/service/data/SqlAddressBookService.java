package fr.openent.zimbra.service.data;

import fr.openent.zimbra.Zimbra;
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

    private final String abookSyncTable;
    private final String ABOOKSYNCTABLE_USERID = "userid";
    private final String ABOOKSYNCTABLE_DATESYNCHRO = "date_synchro";

    private static Logger log = LoggerFactory.getLogger(SqlAddressBookService.class);

    public class SqlAbookSyncResult {
        public String userId;
        public Instant lastSync;

        public SqlAbookSyncResult(JsonObject bddResult) {
            userId = bddResult.getString(ABOOKSYNCTABLE_USERID, "");
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
    }

    public void getUserSyncInfo(String userId, Handler<SqlAbookSyncResult> handler) {
        // SELECT userid, date_synchro FROM zimbra.address_book_sync WHERE userid=?
        String query = "SELECT " + ABOOKSYNCTABLE_USERID + ", " + ABOOKSYNCTABLE_DATESYNCHRO
                + " FROM " + abookSyncTable
                + " WHERE " + ABOOKSYNCTABLE_USERID + "=?";

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
        String query = "INSERT INTO " + abookSyncTable + "(" + ABOOKSYNCTABLE_USERID + "," + ABOOKSYNCTABLE_DATESYNCHRO + ")"
                + " VALUES(?,now())" +
                " ON CONFLICT(" + ABOOKSYNCTABLE_USERID + ")" +
                " DO UPDATE SET " + ABOOKSYNCTABLE_DATESYNCHRO + "=now()";
        sql.prepared(query, new JsonArray().add(userId), SqlResult.validRowsResultHandler( result -> {
            if(result.isLeft()) {
                log.error("Error when update db in markUserAsSynced for user " + userId + " : " + result.left().getValue());
            }
        }));
    }
}
