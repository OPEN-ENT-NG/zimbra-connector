package fr.openent.zimbra.model.synchro.addressbook;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.soap.SoapRequest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class AddressBookSynchroZimbra extends AddressBookSynchro{


    public AddressBookSynchroZimbra(String uai) throws NullPointerException {
        super(uai);
    }

    @Override
    public void load(Handler<AsyncResult<AddressBookSynchro>> handler) {
        //todo load from Zimbra
        SoapRequest getContactsRequest = SoapRequest.AccountSoapRequest(
                SoapConstants.GET_FOLDER_REQUEST,
                Zimbra.appConfig.getZimbraAdminAccount());
        getContactsRequest.setContent(new JsonObject());
        handler.handle(Future.succeededFuture(AddressBookSynchroZimbra.this));
    }
}
