package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.service.impl.SoapZimbraService;
import fr.openent.zimbra.service.impl.SqlZimbraService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;

import static fr.openent.zimbra.helper.ZimbraConstants.*;
import static org.entcore.common.neo4j.Neo4jResult.validUniqueResultHandler;

public class SynchroUserService {

    private Neo4j neo;
    private SoapZimbraService soapService;
    private SqlZimbraService sqlService;

    public SynchroUserService(SoapZimbraService soapZimbraService, SqlZimbraService sqlService){
        this.neo = Neo4j.getInstance();
        this.soapService = soapZimbraService;
        this.sqlService = sqlService;
    }

    /**
     * Remove outdated user info from base
     * @param userId User ID (potentially obsolete)
     * @param userMail User Mail (potentially obsolete)
     * @param handler Final handler
     */
    public void removeUserFromBase(String userId, String userMail, Handler<Either<String,JsonObject>> handler) {
        sqlService.removeUserFrombase(userId, userMail, handler);
    }

    /**
     * Get every info from Neo4j that needs to be synchronized with Zimbra
     * @param id User Id
     * @param handler result handler
     */
    private void getUserFromNeo4j(String id, Handler<Either<String, JsonObject>> handler) {

        JsonArray fields = new JsonArray().add("externalId").add("lastName").add("firstName").add("login");
        fields.add("email").add("emailAcademy").add("mobile").add("deleteDate").add("functions").add("displayName");


        StringBuilder query = new StringBuilder();

        query.append("MATCH (s:Structure)<-[:DEPENDS]-(pg:ProfileGroup)")
                .append("-[:HAS_PROFILE]->(p:Profile)")
                .append(", pg<-[:IN]-(u:User) ")
                .append(", (g:Group)<-[:IN]-u ");

        String  filter =  "WHERE u.id = {id} ";
        JsonObject params = new JsonObject().put("id", id);

        query.append(filter);

        query.append("RETURN DISTINCT ");
        for (Object field : fields) {
            query.append(" u.").append(field);
            query.append(" as ").append(field).append(",");
        }
        query.deleteCharAt(query.length() - 1);
        query.append(", p.name as profiles");
        query.append(", s.externalId as structures")
                .append(" , CASE WHEN size(u.classes) > 0  THEN  last(collect(u.classes)) END as classes")
                .append(" , collect({groupName:g.name, groupId:g.id}) + {groupName:pg.name, groupId:pg.id} as groups");
        neo.execute(query.toString(), params, validUniqueResultHandler(handler));
    }

    /**
     * Export a user to Zimbra
     * Get data from Neo4j, then create user in Zimbra
     * @param userId userId
     * @param handler result handler
     */
    public void exportUser(String userId, Handler<Either<String, JsonObject>> handler) {

        getUserFromNeo4j(userId, neoResponse -> {
            if(neoResponse.isLeft()) {
                handler.handle(neoResponse);
            } else {
                createUser(userId, 0, neoResponse.right().getValue(), handler);
            }
        });

    }

    /**
     * Create user in Zimbra
     *      login@domain
     * If user login already exists in Zimbra, use deduplication :
     *      login-<increment>@domain
     * @param userId User id
     * @param increment Increment to use (if > 0)
     * @param neoData User Neo4j data
     * @param handler result handler
     */
    private void createUser(String userId, int increment, JsonObject neoData,
                            Handler<Either<String, JsonObject>> handler) {

        String login = neoData.getString("login", "");
        if(login.isEmpty()) {
            handler.handle(new Either.Left<>("No login from Neo4j, can't create zimbra account"));
            return;
        }

        String accountName = increment > 0
                ? login + "-" + increment + "@" + Zimbra.domain
                : login + "@" + Zimbra.domain;

        String firstName = neoData.getString("firstName", "");
        String lastName = neoData.getString("lastName", "");
        JsonArray attributes = new JsonArray()
                .add(new JsonObject()
                        .put("n", "givenName")
                        .put("_content", firstName))
                .add(new JsonObject()
                        .put("n", "sn")
                        .put("_content", lastName))
                .add(new JsonObject()
                        .put("n", "cn")
                        .put("_content", firstName + " " + lastName));
        for(Object o : neoData.getJsonArray("groups", new JsonArray())) {
            if(!(o instanceof JsonObject)) continue;
            JsonObject group = (JsonObject)o;
            if(group.containsKey("groupId")) {
                attributes.add(new JsonObject()
                    .put("n", "ou")
                    .put("_content", group.getString("groupId")));
            }
        }


        JsonObject createAccountRequest = new JsonObject()
                .put("name", "CreateAccountRequest")
                .put("content", new JsonObject()
                        .put("name", accountName)
                        .put("a", attributes)
                        .put("_jsns", NAMESPACE_ADMIN));

        soapService.callAdminSoapAPI(createAccountRequest, response -> {
            if(response.isLeft()) {
                try {
                    JsonObject callResult = new JsonObject(response.left().getValue());
                    if(callResult.getString(SoapZimbraService.ERROR_CODE,"").equals(ERROR_ACCOUNTEXISTS)) {
                        createUser(userId, increment+1, neoData, handler);
                    } else {
                        handler.handle(response);
                    }
                } catch (Exception e) {
                    handler.handle(response);
                }
            } else {
                try {
                    String accountId = response.right().getValue()
                            .getJsonObject("Body")
                            .getJsonObject("CreateAccountResponse")
                            .getJsonArray("account").getJsonObject(0)
                            .getString("id");
                    addAlias(userId, accountId, handler);
                } catch (Exception e) {
                    handler.handle(new Either.Left<>("Could not get account id to add alias"));
                }
            }
        });
    }

    /**
     * Add user Id as alias to existing Zimbra account
     * @param userId User Id
     * @param accountId Existing Zimbra Account Id
     * @param handler result handler
     */
    private void addAlias(String userId, String accountId, Handler<Either<String, JsonObject>> handler) {
        JsonObject addAliasRequest = new JsonObject()
                .put("name", "AddAccountAliasRequest")
                .put("content", new JsonObject()
                        .put("id", accountId)
                        .put("alias", userId + "@" + Zimbra.domain)
                        .put("_jsns", NAMESPACE_ADMIN));
        soapService.callAdminSoapAPI(addAliasRequest, handler);
    }
}
