package fr.openent.zimbra.data;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.JsonHelper;
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
import java.util.Collections;
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
    private List<Group> groups = new ArrayList<>();
    private String profile = "";
    private List<String> structuresUai = new ArrayList<>();
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

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
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
        profile = neoUser.getString("profiles", "");
        processJsonGroups(neoUser.getJsonArray("groups", new JsonArray()));
        structuresUai = JsonHelper.getStringList(neoUser.getJsonArray("structures", new JsonArray()));

        if(login.isEmpty()) {
            throw new IllegalArgumentException("Login is mandatory for user : " + userId);
        }
    }

    private void processJsonGroups(JsonArray groupArray) throws IllegalArgumentException {
        for(Object o : groupArray) {
            if(!(o instanceof JsonObject)) {
                throw new IllegalArgumentException("JsonArray is not a String list");
            }
            JsonObject groupJson = (JsonObject)o;
            groups.add(new Group(groupJson));
        }
    }

    public JsonArray getSoapData() {
        return getSoapData(false);
    }

    private JsonArray getSoapData(boolean delete) {

        Collections.sort(structuresUai);
        StringBuilder structuresStr = new StringBuilder();
        for(String uai : structuresUai) {
            structuresStr.append(uai);
            structuresStr.append(" ");
        }

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
                        .put(SoapConstants.ATTR_VALUE, login))
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, PROFILE)
                        .put(SoapConstants.ATTR_VALUE, profile))
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, STRUCTURES)
                        .put(SoapConstants.ATTR_VALUE, structuresStr.toString()))
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, ACCOUNT_STATUS)
                        .put(SoapConstants.ATTR_VALUE, delete ? ACCT_STATUS_LOCKED : ACCT_STATUS_ACTIVE))
                .add(new JsonObject()
                        .put(SoapConstants.ATTR_NAME, HIDEINSEARCH)
                        .put(SoapConstants.ATTR_VALUE, delete ? SoapConstants.TRUE_VALUE : SoapConstants.FALSE_VALUE));

        if(delete) {
            attributes.add(new JsonObject()
                    .put(SoapConstants.ATTR_NAME, GROUPID)
                    .put(SoapConstants.ATTR_VALUE, SoapConstants.EMPTY_VALUE));
        } else {
            for (Group group : groups) {
                attributes
                        .add(new JsonObject()
                                .put(SoapConstants.ATTR_NAME, GROUPID)
                                .put(SoapConstants.ATTR_VALUE, group.getId()));
            }
        }

        return attributes;
    }

    @SuppressWarnings("WeakerAccess")
    public JsonArray getDeleteSoapData() {
        return getSoapData(true);
    }
}
