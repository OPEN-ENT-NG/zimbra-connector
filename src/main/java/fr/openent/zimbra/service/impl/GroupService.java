package fr.openent.zimbra.service.impl;


import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.ZimbraConstants;
import fr.openent.zimbra.service.synchro.SynchroGroupService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

class GroupService {

    private SoapZimbraService soapService;
    private SqlZimbraService sqlService;
    private SynchroGroupService synchroGroupService;
    private static Logger log = LoggerFactory.getLogger(GroupService.class);

    GroupService(SoapZimbraService soapService, SqlZimbraService sqlService, SynchroUserService synchroUserService) {
        this.soapService = soapService;
        this.sqlService = sqlService;
        this.synchroGroupService = new SynchroGroupService(soapService, synchroUserService);
    }

    /**
     * Get a group adress
     * First query database
     * If not present, query Zimbra
     * If not existing in Zimbra, try to create it
     * No need to translate group mail, final form is id@domain
     * @param groupId Group Id
     * @param handler result handler
     */
    void getGroupAddress(String groupId, Handler<Either<String,String>> handler) {
        sqlService.getGroupMailFromId(groupId, result -> {
            if(result.isLeft() || result.right().getValue().isEmpty()) {
                log.debug("no group in database for id : " + groupId);
                String groupAddress = groupId + "@" + Zimbra.domain;
                getGroupAccount(groupAddress, response -> {
                    if(response.isLeft()) {
                        JsonObject callResult = new JsonObject(response.left().getValue());
                        if(ZimbraConstants.ERROR_NOSUCHDLIST
                                .equals(callResult.getString(SoapZimbraService.ERROR_CODE, ""))) {
                            synchroGroupService.exportGroup(groupId, resultSync -> {
                                if (resultSync.isLeft()) {
                                    handler.handle(new Either.Left<>(resultSync.left().getValue()));
                                } else {
                                    getGroupAddress(groupId, handler);
                                }
                            });
                        } else {
                            handler.handle(new Either.Left<>(response.left().getValue()));
                        }
                    } else {
                        handler.handle(new Either.Right<>(groupAddress));
                    }
                });
            } else {
                JsonArray results = result.right().getValue();
                if(results.size() > 1) {
                    log.warn("More than one address for user id : " + groupId);
                }
                String mail = results.getJsonObject(0).getString(SqlZimbraService.ZIMBRA_NAME);
                handler.handle(new Either.Right<>(mail));
            }
        });
    }

    /**
     * Get account info from specified group from Zimbra
     * @param account Zimbra account name
     * @param handler result handler
     */
    private void getGroupAccount(String account,
                        Handler<Either<String, JsonObject>> handler) {

        JsonObject acct = new JsonObject()
                .put("by", "name")
                .put("_content", account);

        JsonObject getInfoRequest = new JsonObject()
                .put("name", "GetDistributionListRequest")
                .put("content", new JsonObject()
                        .put("_jsns", ZimbraConstants.NAMESPACE_ADMIN)
                        .put("limit", 1)
                        .put(ZimbraConstants.DISTRIBUTION_LIST, acct));

        soapService.callAdminSoapAPI(getInfoRequest, handler);
    }

    /**
     * Get the id part of an group email address
     * @param email address of the group
     * @return id part if it's a group address, null otherwise
     */
    String getGroupId(String email) {
        if(email.matches("[0-9a-z-]*@" + Zimbra.domain)) {
            return email.substring(0, email.indexOf("@"));
        }
        else {
            return null;
        }
    }
}
