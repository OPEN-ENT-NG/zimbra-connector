package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.service.data.SqlAddressBookService;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

public class AddressBookService {

    SqlAddressBookService sqlAddressBookService;
    private static Logger log = LoggerFactory.getLogger(AddressBookService.class);

    public AddressBookService(SqlAddressBookService sqlAddressBookService) {
        this.sqlAddressBookService = sqlAddressBookService;
    }

    public void isUserReadyToSync(UserInfos user, Handler<Boolean> handler) {
        try {
            sqlAddressBookService.getUserSyncInfo(user.getUserId(), result -> {
                if(result != null) {
                    handler.handle(result.mustBeSynced());
                } else {
                    // In case of error, or user not in database, default to user abook sync
                    handler.handle(true);
                }
            });
        } catch (Exception e) {
            log.error("Error in AddressBookService.isUserReadyToSync", e);
            // In case of error, default to user abook sync
            handler.handle(true);
        }
    }

    public void markUserAsSynced(UserInfos user) {
        try {
            sqlAddressBookService.markUserAsSynced(user.getUserId());
        } catch (Exception e) {
            log.error("Error in AddressBookService.markUserAsSynced", e);
        }
    }
}
