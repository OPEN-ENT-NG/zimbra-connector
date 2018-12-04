package fr.openent.zimbra.data;

import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.helper.SoapConstants;
import fr.openent.zimbra.service.impl.SoapZimbraService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import static fr.openent.zimbra.helper.ZimbraConstants.NAMESPACE_ACCOUNT;
import static fr.openent.zimbra.helper.ZimbraConstants.NAMESPACE_ADMIN;

public class SoapRequest {

    private String name;
    private String namespace;
    private boolean isAdmin;
    private JsonObject content = null;

    private SoapRequest(String name, String namespace, boolean isAdmin) {
        this.name = name;
        this.namespace = namespace;
        this.isAdmin = isAdmin;
    }

    public static SoapRequest UserSoapRequest(String name) {
        return new SoapRequest(name, NAMESPACE_ACCOUNT, false);
    }

    public static SoapRequest AdminSoapRequest(String name) {
        return new SoapRequest(name, NAMESPACE_ADMIN, true);
    }

    public void setContent(JsonObject content) {
        if(content == null) {
            content = new JsonObject();
        }
        content.put("_jsns", namespace);
        this.content = content;
    }

    public void start(Handler<AsyncResult<JsonObject>> handler) {
        // todo handle user requests
        ServiceManager sm = ServiceManager.getServiceManager();
        SoapZimbraService soapService = sm.getSoapService();

        if(name == null || name.isEmpty() || content == null || content.isEmpty()) {
            handler.handle(Future.failedFuture("Incomplete request"));
            return;
        }

        JsonObject reqParams = new JsonObject()
                .put(SoapConstants.REQ_NAME, name)
                .put(SoapConstants.REQ_CONTENT, content);

        if(!isAdmin) {
            handler.handle(Future.failedFuture("User requests not implemented"));
            return;
        }

        soapService.callAdminSoapAPI(reqParams, AsyncHelper.getJsonObjectEitherHandler(handler));
    }
}
