package fr.openent.zimbra.data;


import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.helper.SoapConstants;
import fr.openent.zimbra.helper.SynchroConstants;
import fr.openent.zimbra.helper.ZimbraConstants;
import fr.openent.zimbra.service.impl.Neo4jZimbraService;
import fr.openent.zimbra.service.impl.SqlZimbraService;
import fr.openent.zimbra.service.impl.UserService;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;

import static fr.openent.zimbra.helper.SoapConstants.*;
import static fr.openent.zimbra.helper.AsyncHelper.*;

public class ZimbraUser {

    UserService userService;
    SqlZimbraService sqlService;
    Neo4jZimbraService neo4jService;

    private String zimbraID = "";
    private String zimbraName = "";
    private String zimbraStatus = "";
    private List<String> aliases = new ArrayList<>();

    private String totalQuota = "";
    private List<String> entGroups = new ArrayList<>();

    private String entLogin = "";

    private static Logger log = LoggerFactory.getLogger(ZimbraUser.class);

    public ZimbraUser() {
        ServiceManager sm = ServiceManager.getServiceManager();
        this.userService = sm.getUserService();
        this.sqlService = sm.getSqlService();
        this.neo4jService = sm.getNeoService();
    }

    public String getName() {
        return zimbraName;
    }
    public List<String> getAliases() {
        return aliases;
    }
    public String getLogin() {
        return entLogin;
    }



    public void fetchEntLoginFromEmail(String email, Handler<Either<String, JsonObject>> handler) {
        Future<JsonObject> startFuture = getJsonObjectFinalFuture(handler);

        Future<JsonObject> fetchedInfos = Future.future();
        fetchAccountInfoFromEmail(email, fetchedInfos.completer());
        fetchedInfos.compose(v -> {
            fetchLoginFromAliases(startFuture.completer());
        }, startFuture);
    }

    public void fetchAccountInfoFromEmail(String email, Handler<AsyncResult<JsonObject>> handler) {
        JsonObject acct = new JsonObject()
                .put(SoapConstants.ID_BY, ZimbraConstants.ACCT_NAME)
                .put(SoapConstants.ATTR_VALUE, email);

        SoapRequest getAccountRequest = SoapRequest.AdminSoapRequest(SoapConstants.GET_ACCOUNT_REQUEST);
        getAccountRequest.setContent(new JsonObject().put(SoapConstants.ACCOUNT, acct));
        getAccountRequest.start(response -> {
            if(response.succeeded()) {
                try {
                    processGetAccountInfo(response.result());
                } catch (InvalidPropertiesFormatException e) {
                    handler.handle(Future.failedFuture(e));
                }
            }
            handler.handle(response);
        });
    }

    private void fetchLoginFromAliases(Handler<AsyncResult<JsonObject>> handler) {
        JsonArray aliasList = new JsonArray();
        for(String alias : aliases) {
            try {
                MailAddress addr = MailAddress.createFromRawAddress(alias);
                aliasList.add(addr.getLocalPart());
            } catch (IllegalArgumentException e) {
                log.error("Malformed alias : " + alias);
            }
        }
        if(aliasList.isEmpty()) {
            handler.handle(Future.failedFuture("No login for this user"));
        } else {
            neo4jService.getLoginFromIds(aliasList, neoResponse -> {
                if(neoResponse.isRight()) {
                    this.entLogin = neoResponse.right().getValue().getString("login");
                    handler.handle(Future.succeededFuture(neoResponse.right().getValue()));
                } else {
                    handler.handle(Future.failedFuture(neoResponse.left().getValue()));
                }
            });
        }
    }

    private void processGetAccountInfo(JsonObject zimbraData) throws InvalidPropertiesFormatException{
        JsonObject getInfoResp = zimbraData.getJsonObject(BODY)
                .getJsonObject(GET_ACCOUNT_RESPONSE);

        try {
            this.processAccountInfo(getInfoResp);
        } catch (Exception e) {
            String message ="Error when parsing userInfos " ;
            log.error(message + zimbraData.toString());
            throw new InvalidPropertiesFormatException(message);
        }

        sqlService.updateUserAsync(this);
    }

    // Can throw Exception when parsing getAccountResponse
    private void processAccountInfo(JsonObject getAccountResponse) {

        JsonObject account = getAccountResponse.getJsonArray(ACCOUNT).getJsonObject(0);
        this.zimbraID = account.getString(ZimbraConstants.ACCT_ID, this.zimbraID);
        this.zimbraName = account.getString(ZimbraConstants.ACCT_NAME, this.zimbraName);

        JsonArray attributes = account.getJsonArray(ZimbraConstants.ACCT_ATTRIBUTES, new JsonArray());
        for(Object o : attributes) {
            if(!(o instanceof JsonObject)) continue;
            JsonObject attr = (JsonObject)o;
            String key = attr.getString(ZimbraConstants.ACCT_ATTRIBUTES_NAME, "");
            String value = attr.getString(ZimbraConstants.ACCT_ATTRIBUTES_CONTENT, "");

            switch (key) {
                case ZimbraConstants.ATTR_MAIL_QUOTA:
                    this.totalQuota = value;
                    break;
                case SynchroConstants.ALIAS:
                    this.aliases.add(value);
                    break;
                case SynchroConstants.ACCOUNT_STATUS:
                    this.zimbraStatus = value;
                    break;
                case SynchroConstants.GROUPID:
                    this.entGroups.add(value);
                    break;
            }
        }
    }
}
