package fr.openent.zimbra.data;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.helper.SoapConstants;
import fr.openent.zimbra.service.impl.Neo4jZimbraService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;

import java.util.ArrayList;
import java.util.List;

import static fr.openent.zimbra.helper.SynchroConstants.*;

public class EntUser {

    private Neo4jZimbraService neoService;

    private String userId;
    private String externalId = "";
    private String lastName = "";
    private String firstName = "";
    private String displayName = "";
    private String login = "";
    private String email = "";
    private String emailAcademy = "";
    private List<String> groupsId = new ArrayList<>();
    private List<String> profiles = new ArrayList<>();
    private MailAddress userZimbraAddress;


    public EntUser(UserInfos userInfos) throws IllegalArgumentException {
        initUser(userInfos.getUserId());
    }

    public EntUser(String userId) throws IllegalArgumentException {
        initUser(userId);
    }

    private void initUser(String userId) throws IllegalArgumentException {
        this.userId = userId;
        userZimbraAddress = MailAddress.createFromLocalpartAndDomain(userId, Zimbra.domain);
        ServiceManager sm = ServiceManager.getServiceManager();
        this.neoService = sm.getNeoService();
    }

    public String getUserId() {
        return userId;
    }

    public String getUserAddress() {
        return userZimbraAddress.toString();
    }

    public String getLogin() {
        return login;
    }

    public void fetchDataFromNeo(Handler<AsyncResult<Void>> handler) {
        neoService.getUserFromNeo4j(userId, neoResult -> {
            if(neoResult.isLeft()) {
                handler.handle(Future.failedFuture(neoResult.left().getValue()));
            } else {
                JsonObject jsonUser = neoResult.right().getValue();
                try {
                    applyJson(jsonUser);
                } catch (Exception e) {
                    handler.handle(Future.failedFuture(e));
                }
                handler.handle(Future.succeededFuture());
            }
        });
    }

    private void applyJson(JsonObject neoUser) throws IllegalArgumentException {
        externalId = neoUser.getString("externalId", "");
        lastName = neoUser.getString("lastName", "");
        firstName = neoUser.getString("firstName", "");
        displayName = neoUser.getString("displayName", "");
        login = neoUser.getString("login", "");
        emailAcademy = neoUser.getString("emailAcademy", "");
        email = neoUser.getString("email", "");
        profiles = neoUser.getJsonArray("profiles", new JsonArray()).getList();
        groupsId = neoUser.getJsonArray("groups", new JsonArray()).getList();

        if(login.isEmpty()) {
            throw new IllegalArgumentException("Login is mandatory for user : " + userId);
        }
    }

    public JsonArray getSoapData() {

        JsonArray attributes = new JsonArray()
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, FIRSTNAME)
                        .put(SoapConstants.ATTR_VALUE, firstName))
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, LASTNAME)
                        .put(SoapConstants.ATTR_VALUE, lastName))
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, DISPLAYNAME)
                        .put(SoapConstants.ATTR_VALUE, displayName))
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, LOGIN)
                        .put(SoapConstants.ATTR_VALUE, login));

        for(String groupId : groupsId) {
            attributes
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, GROUPID)
                        .put(SoapConstants.ATTR_VALUE, groupId));
        }

        return attributes;
    }
}
