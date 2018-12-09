package fr.openent.zimbra.data;

import fr.openent.zimbra.helper.AsyncHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.impl.SoapZimbraService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import static fr.openent.zimbra.helper.SoapConstants.*;

public class SoapRequest {

    private String name;
    private String namespace;
    private boolean isAdmin;
    private String userId;
    private JsonObject content = null;

    private SoapRequest(String name, String namespace, boolean isAdmin) {
        this.name = name;
        this.namespace = namespace;
        this.isAdmin = isAdmin;
    }

    private SoapRequest(String name, String namespace, boolean isAdmin, String userId) {
        this.name = name;
        this.namespace = namespace;
        this.isAdmin = isAdmin;
        this.userId = userId;
    }

    public static SoapRequest AccountSoapRequest(String name, String userId) {
        return new SoapRequest(name, NAMESPACE_ACCOUNT, false, userId);
    }

    public static SoapRequest AdminSoapRequest(String name) {
        return new SoapRequest(name, NAMESPACE_ADMIN, true);
    }

    public void setContent(JsonObject content) {
        if(content == null) {
            content = new JsonObject();
        }
        content.put(REQ_NAMESPACE, namespace);
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
                .put(REQ_NAME, name)
                .put(REQ_CONTENT, content);

        if(isAdmin) {
            soapService.callAdminSoapAPI(reqParams, AsyncHelper.getJsonObjectEitherHandler(handler));
        } else {
            if(userId.isEmpty()) {
                handler.handle(Future.failedFuture("Can't launch user request without userid"));
            } else {
                soapService.callUserSoapAPI(reqParams, userId, handler);
            }
        }

    }
}
