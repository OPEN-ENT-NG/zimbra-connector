package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.model.soap.model.SoapFolder;
import fr.openent.zimbra.service.data.SqlAddressBookService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.user.UserInfos;

public class AddressBookService {

    SqlAddressBookService sqlAddressBookService;
    private static final Logger log = LoggerFactory.getLogger(AddressBookService.class);

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

    public void purgeEmailedContacts(UserInfos user) {
        isUserNeverPurge(user, isNeverPurge ->  {
            if(isNeverPurge) {
                try {
                    purgeEmailedContacts(user.getUserId(), res -> {
                        if(res.failed()) {
                            log.error("zimbra purge Emailed Contacts  failed for user " + user.getUserId() + " " + res.cause());
                        } else {
                            markUserAsPurged(user);
                            log.info("zimbra purge Emailed Contacts successful for user " + user.getUserId());
                        }
                    });
                } catch (Exception e) {
                    //No Exception may be thrown in the main thread
                    log.error("Error in purgeEmailedContacts : " + e);
                }
            }
        });
    }

    private void isUserNeverPurge(UserInfos user, Handler<Boolean> handler) {
        try {
            sqlAddressBookService.getUserPurgeEmailedContacts(user.getUserId(), result -> {
                if(result != null) {
                    handler.handle(false);
                } else {
                    // In case of error, or user not in database, default to user abook purge emailed contacts
                    handler.handle(true);
                }
            });
        } catch (Exception e) {
            log.error("Error in AddressBookService.isUserNeverPurge", e);
            // In case of error, default to user abook purge emailed contacts
            handler.handle(false);
        }
    }

    private void markUserAsPurged(UserInfos user) {
        try {
            sqlAddressBookService.markUserAsPurged(user.getUserId());
        } catch (Exception e) {
            log.error("Error in AddressBookService.markUserAsPurged", e);
        }
    }

    private void purgeEmailedContacts(String userId, Handler<AsyncResult<JsonObject>> handler) {
        try {
            SoapFolder.getFolderByPath(userId, ZimbraConstants.PATH_EMAILED_CONTACTS, ZimbraConstants.VIEW_CONTACT, -1,
                    emailedContactsFolder -> {
                if (emailedContactsFolder.failed()) {
                    log.error("Error in AddressBookService.purgeEmailedContacts.getFolderByPath", emailedContactsFolder.cause());
                } else {
                    SoapFolder zimbraFolder = emailedContactsFolder.result();
                    zimbraFolder.emptyFolder(userId, handler);
                }
            });
        } catch (Exception e) {
            log.error("Error in AddressBookService.purgeEmailedContacts", e);
        }
    }

    public void truncatePurgeTable() {
        try {
            sqlAddressBookService.truncatePurgeTable();
            log.info("Truncate purge_emailed_contacts table");
        } catch (Exception e) {
            log.error("Error in AddressBookService.truncatePurgeTable", e);
        }
    }
}
