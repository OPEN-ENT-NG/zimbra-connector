package fr.openent.zimbra.model.synchro.addressbook;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static fr.openent.zimbra.service.data.Neo4jAddrbookService.*;

class AddressBookSubDir {

    private Map<String,AddressBookUser> users = new HashMap<>();

    private static Logger log = LoggerFactory.getLogger(AddressBookSubDir.class);

    AddressBookSubDir(JsonObject json) throws IllegalArgumentException {
        if(json == null
                ||  json.getString(SUBDIR_NAME, "").isEmpty()) {
            throw new IllegalArgumentException("Invalid AddressBookSubDir data");
        }
        log.info("Subdir : " + json.toString());
        JsonArray jsonUsers = json.getJsonArray(USERS, new JsonArray());
        for(Object o : jsonUsers) {
            if(!(o instanceof JsonObject)) continue;
            try {
                AddressBookUser abUser = new AddressBookUser((JsonObject)o);
                users.put(abUser.getId(), abUser);
            } catch (IllegalArgumentException e) {
                log.error("Error when loading abookuser : " + o.toString());
            }
        }
    }
}
