package fr.openent.zimbra.model;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.JsonHelper;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.service.data.Neo4jZimbraService;
import fr.openent.zimbra.service.data.SqlZimbraService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.UserInfos;
import io.vertx.core.Handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static fr.openent.zimbra.model.constant.SynchroConstants.*;

@SuppressWarnings("FieldCanBeLocal")
public class EntUser {

    @SuppressWarnings("WeakerAccess")
    protected Neo4jZimbraService neoService;
    protected SqlZimbraService sqlZimbraService;

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
        this.sqlZimbraService = sm.getSqlService();
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

    public String getUserStrAddress() {
        return userZimbraAddress.toString();
    }

    public MailAddress getUserMailAddress() {
        return userZimbraAddress;
    }

    protected List<Group> getGroups() {
        return groups;
    }

    @SuppressWarnings("WeakerAccess")
    public String getLogin() {
        return login;
    }

    public void fetchDataFromNeo(Handler<AsyncResult<Void>> handler) {
        neoService.getUserFromNeo4j(userId, neoResult -> {
            if(neoResult.failed()) {
                handler.handle(Future.failedFuture(neoResult.cause()));
            } else {
                JsonObject jsonUser = neoResult.result();
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
        groups = Group.processJsonGroups(neoUser.getJsonArray("groups", new JsonArray()));
        structuresUai = JsonHelper.getStringList(neoUser.getJsonArray("structures", new JsonArray()));

        if(login.isEmpty()) {
            throw new IllegalArgumentException("Login is mandatory for user : " + userId);
        }
    }


    @SuppressWarnings("WeakerAccess")
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
