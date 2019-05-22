package fr.openent.zimbra.model.synchro.addressbook;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.ArrayList;
import java.util.List;



public abstract class AddressBookSynchro {

    protected String uai;
    protected List<AddressBookFolder> folders = new ArrayList<>();

    AddressBookSynchro(String uai) throws NullPointerException {
        if(uai == null || uai.isEmpty()) {
            throw new NullPointerException("AddrBook : Empty UAI");
        }
        this.uai = uai;
    }

    public abstract void load(Handler<AsyncResult<AddressBookSynchro>> handler);
}
