package fr.openent.zimbra.model;


import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.model.constant.SynchroConstants;
import fr.openent.zimbra.model.constant.ZimbraConstants;
import fr.openent.zimbra.model.soap.SoapError;
import fr.openent.zimbra.service.data.Neo4jZimbraService;
import fr.openent.zimbra.service.data.SqlZimbraService;
import fr.openent.zimbra.service.impl.UserService;
import fr.wseduc.webutils.Either;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;

import static fr.openent.zimbra.model.constant.SoapConstants.*;
import static fr.openent.zimbra.helper.AsyncHelper.*;

@SuppressWarnings("FieldCanBeLocal")
public class ZimbraUser {

    private UserService userService;
    private SqlZimbraService sqlService;
    private Neo4jZimbraService neo4jService;

    private boolean accountExists = false;
    private MailAddress address;

    private String zimbraID = "";
    private String zimbraName = "";
    private String zimbraStatus = "";
    private List<String> aliases = new ArrayList<>();

    private String totalQuota = "";
    private List<String> entGroups = new ArrayList<>();

    private String entLogin = "";

    private static Logger log = LoggerFactory.getLogger(ZimbraUser.class);

    public ZimbraUser(MailAddress address) {
        this.address = address;
        init();
    }

    private void init() {
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
    public String getZimbraID() {
        return zimbraID;
    }



    public void checkIfExists(Handler<AsyncResult<ZimbraUser>> handler) {
        JsonObject response = new JsonObject();

        fetchAccountInfoFromEmail(address.toString(), result -> {
            if(result.succeeded()) {
                response.put("login", entLogin);
                handler.handle(Future.succeededFuture(ZimbraUser.this));
            } else {
                String errorStr = result.cause().getMessage();
                try {
                    SoapError error = new SoapError(errorStr);
                    if(ZimbraConstants.ERROR_NOSUCHACCOUNT.equals(error.getCode())) {
                        handler.handle(Future.succeededFuture(ZimbraUser.this));
                    } else {
                        handler.handle(Future.failedFuture(errorStr));
                    }
                } catch (DecodeException e) {
                    log.error("Unknown error when trying to fetch account info : " + errorStr);
                    handler.handle(Future.failedFuture("Unknown Zimbra error"));
                }
            }
        });
    }

    public boolean existsInZimbra() {
        return accountExists;
    }

    private void fetchAccountInfoFromEmail(String email, Handler<AsyncResult<ZimbraUser>> handler) {
        userService.getUserAccount(email, response ->  {
            if(response.succeeded()) {
                try {
                    processGetAccountInfo(response.result());
                    handler.handle(Future.succeededFuture(ZimbraUser.this));
                } catch (InvalidPropertiesFormatException e) {
                    handler.handle(Future.failedFuture(e));
                }
            } else {
                handler.handle(Future.failedFuture(response.cause()));
            }
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
