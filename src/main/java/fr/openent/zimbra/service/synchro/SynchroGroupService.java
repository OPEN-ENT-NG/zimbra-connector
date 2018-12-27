package fr.openent.zimbra.service.synchro;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.model.constant.SoapConstants;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.user.UserUtils;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;
import static org.entcore.common.neo4j.Neo4jResult.validUniqueResultHandler;

public class SynchroGroupService {


    private Neo4j neo;
    private SoapZimbraService soapService;
    private static final String memberUrlTpl = "ldap:///??sub?(&(objectClass=zimbraAccount)(|(ou=###)(ou=allgroupsaccount)))";
    private static Logger log = LoggerFactory.getLogger(SynchroGroupService.class);
    private SynchroUserService synchroUser;

    public SynchroGroupService(SoapZimbraService soapZimbraService, SynchroUserService synchroUserService){
        this.neo = Neo4j.getInstance();
        this.soapService = soapZimbraService;
        this.synchroUser = synchroUserService;
    }

    /**
     * Export a group to Zimbra
     * If it is a manual group, update members in Zimbra (create them if necessary)
     * Get data from Neo4j, then create group in Zimbra
     * @param groupId Group Id
     * @param handler result handler
     */
    public void exportGroup(String groupId, Handler<Either<String, JsonObject>> handler) {

        getGroupFromNeo4j(groupId, neoResponse -> {
            if(neoResponse.isLeft()) {
                log.error("Zimbra : exportGroup, " + groupId +" not found in Neo. "
                        + neoResponse.left().getValue());
                handler.handle(neoResponse);
            } else {
                createGroup(groupId, neoResponse.right().getValue(), resultCreateGroup -> {
                    if(resultCreateGroup.isLeft()) {
                        handler.handle(resultCreateGroup);
                    } else {
                        updateManualGroupMembers(groupId, handler);
                    }
                });
            }
        });

    }

    /**
     * Update a group in Zimbra.
     * If it's not a manual group, do nothing.
     * Else, update members of the group
     * @param groupId id of the group to update
     * @param handler result handler
     */
    private void updateManualGroupMembers(String groupId, Handler<Either<String, JsonObject>> handler) {
        getManualGroupMembersNeo(groupId, result -> {
            if(result.isLeft()) {
                log.error("Zimbra : updateGroup, " + groupId +" not found in Neo. "
                        + result.left().getValue());
                handler.handle(new Either.Left<>(result.left().getValue()));
            } else if (result.right().getValue().isEmpty()) {
                log.warn("Created empty group");
                handler.handle(new Either.Right<>(new JsonObject()));
            } else {
                JsonArray groupMembers = result.right().getValue();
                synchroUser.addGroupToMembers(groupId, groupMembers, handler);
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
     * Get all users ids from a Manual Group in Neo4j
     * @param groupId Group Id
     * @param handler result handler
     */
    private void getManualGroupMembersNeo(String groupId,
                                          Handler<Either<String, JsonArray>> handler) {

        String query = "MATCH (u:User)-[IN]->(g:ManualGroup) "
                + "WHERE g.id = {id} "
                + "RETURN u.id as id";
        JsonObject params = new JsonObject().put("id", groupId);

        neo.execute(query, params, validResultHandler(handler));
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
                        .put("_jsns", SoapConstants.NAMESPACE_ADMIN));

        soapService.callAdminSoapAPI(createDLRequest, handler);
    }
}
