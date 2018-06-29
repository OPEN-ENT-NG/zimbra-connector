package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.service.impl.SoapZimbraService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.user.UserUtils;

import static fr.openent.zimbra.helper.ZimbraConstants.*;
import static org.entcore.common.neo4j.Neo4jResult.validUniqueResultHandler;

public class SynchroGroupService {


    private Neo4j neo;
    private SoapZimbraService soapService;
    private static final String memberUrlTpl = "ldap:///??sub?(&(objectClass=zimbraAccount)(ou=###))";

    public SynchroGroupService(SoapZimbraService soapZimbraService){
        this.neo = Neo4j.getInstance();
        this.soapService = soapZimbraService;
    }

    /**
     * Export a group to Zimbra
     * Get data from Neo4j, then create group in Zimbra
     * @param groupId Group Id
     * @param handler result handler
     */
    public void exportGroup(String groupId, Handler<Either<String, JsonObject>> handler) {

        getGroupFromNeo4j(groupId, neoResponse -> {
            if(neoResponse.isLeft()) {
                handler.handle(neoResponse);
            } else {
                createGroup(groupId, neoResponse.right().getValue(), handler);
            }
        });
    }

    /**
     * Get every info from Neo4j that needs to be synchronized with Zimbra
     * @param id Group Id
     * @param handler result handler
     */
    private void getGroupFromNeo4j(String id, Handler<Either<String, JsonObject>> handler) {

        String query = "MATCH (g:Group) "
                + "WHERE g.id = {id} "
                + "RETURN g.id as id, "
                + "g.groupDisplayName as groupDisplayName, "
                + "g.name as groupName";
        JsonObject params = new JsonObject().put("id", id);

        neo.execute(query, params, validUniqueResultHandler(handler));
    }

    /**
     * Create group in Zimbra
     *      id@domain
     * @param groupId Group id
     * @param neoData Group Neo4j data
     * @param handler result handler
     */
    private void createGroup(String groupId, JsonObject neoData,
                            Handler<Either<String, JsonObject>> handler) {

        String accountName = groupId + "@" + Zimbra.domain;

        String displayName = UserUtils.groupDisplayName(
                neoData.getString("groupName", ""),
                neoData.getString("groupDisplayName"),
                Zimbra.synchroLang);

        String memberUrl = memberUrlTpl.replace("###", groupId);

        JsonArray attributes = new JsonArray()
                .add(new JsonObject()
                        .put("n", "displayName")
                        .put("_content", displayName))
                .add(new JsonObject()
                        .put("n", "memberURL")
                        .put("_content", memberUrl))
                .add(new JsonObject()
                        .put("n", "zimbraIsACLGroup")
                        .put("_content", "FALSE"));


        JsonObject createDLRequest = new JsonObject()
                .put("name", "CreateDistributionListRequest")
                .put("content", new JsonObject()
                        .put("name", accountName)
                        .put("dynamic", 1)
                        .put("a", attributes)
                        .put("_jsns", NAMESPACE_ADMIN));

        soapService.callAdminSoapAPI(createDLRequest, handler);
    }
}
