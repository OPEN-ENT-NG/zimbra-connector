package fr.openent.zimbra.model.synchro.addressbook;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

// todo documentation
public abstract class AddressBookSynchro {

    protected String uai;
    protected Map<String,AddressBookFolder> folders = new HashMap<>();

    AddressBookSynchro(String uai) throws NullPointerException {
        if(uai == null || uai.isEmpty()) {
            throw new NullPointerException("AddrBook : Empty UAI");
        }
        this.uai = uai;
    }

    public abstract void load(Handler<AsyncResult<AddressBookSynchro>> handler);

    public abstract void sync(String userId, Handler<AsyncResult<JsonObject>> handler);

    AddressBookFolder getFolder(String folderName) {
        AddressBookFolder folder = folders.get(folderName);
        if(folder == null) {
            folder = new AddressBookFolder();
            folders.put(folderName, folder);
        }
        return folder;
    }
}
