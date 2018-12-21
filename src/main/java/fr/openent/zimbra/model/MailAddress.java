package fr.openent.zimbra.model;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.helper.ServiceManager;
import fr.openent.zimbra.service.impl.GroupService;
import fr.openent.zimbra.service.data.SqlZimbraService;
import fr.openent.zimbra.service.impl.UserService;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class MailAddress {

    /**
     * The local part of an email address is the part before the '@'
     * See RFC 5322 Chapter 3.4.1 https://tools.ietf.org/html/rfc5322
     */
    private String localPart = "";
    private String domain = "";
    private String completeCleanAddress = "";
    private String rawAddress = "";
    private String neoId = "";
    private boolean isLocal;

    private SqlZimbraService sqlService;
    private UserService userService;
    private GroupService groupService;

    private static Logger log = LoggerFactory.getLogger(MailAddress.class);



    static public MailAddress createFromRawAddress(String rawAddress) throws IllegalArgumentException {
        return new MailAddress(rawAddress);
    }

    @SuppressWarnings("WeakerAccess")
    static public MailAddress createFromLocalpartAndDomain(String localPart, String domain)
            throws IllegalArgumentException {
        return new MailAddress(localPart, domain);
    }

    public boolean isExternal() {
        return !isLocal;
    }

    public String getNeoId() {
        return neoId;
    }

    @SuppressWarnings("WeakerAccess")
    public String getLocalPart() {
        return localPart;
    }

    private MailAddress(String rawAddress) throws IllegalArgumentException {
        this.rawAddress = rawAddress;
        processRawAddress();
        initServices();
    }

    @Override
    public String toString() {
        return this.localPart + "@" + this.domain;
    }

    private void processRawAddress() throws IllegalArgumentException {
        if(rawAddress.isEmpty()) {
            throw new IllegalArgumentException("Empty address can't be processed");
        }
        String addr = rawAddress.replaceAll(".*<","");
        addr = addr.replaceAll(">.*","");
        completeCleanAddress = addr;
        processCleanAddress();
    }

    private void processCleanAddress() throws IllegalArgumentException {
        if(completeCleanAddress.isEmpty()) {
            throw new IllegalArgumentException("Empty address can't be processed");
        }
        setLocalPart( completeCleanAddress.substring(0, completeCleanAddress.indexOf('@')) );
        setDomain( completeCleanAddress.substring(completeCleanAddress.indexOf('@')+1) );
    }

    private MailAddress(String localPart, String domain) throws IllegalArgumentException {
        setLocalPart(localPart);
        setDomain(domain);
        initServices();
    }

    public String getRawCleanAddress() {
        return completeCleanAddress;
    }

    private void setLocalPart(String localPart) {
        //todo check valid localPart
        this.localPart = localPart;
    }

    private void setDomain(String domain) {
        //todo check valid domain
        this.domain = domain;
        isLocal = Zimbra.domain.equals(domain);
    }

    private void initServices() {
        ServiceManager serviceManager = ServiceManager.getServiceManager();
        sqlService = serviceManager.getSqlService();
        userService = serviceManager.getUserService();
        groupService = serviceManager.getGroupService();
    }

    public void fetchNeoId(Handler<String> handler) {
        if(!neoId.isEmpty()) {
            handler.handle(neoId);
            return;
        }
        sqlService.getNeoIdFromMail(completeCleanAddress, sqlResponse -> {
            if(sqlResponse.isLeft() || sqlResponse.right().getValue().isEmpty()) {
                log.debug("no user in database for address : " + completeCleanAddress);
                userService.getAliases(completeCleanAddress, zimbraResponse -> {
                    if(zimbraResponse.succeeded()) {
                        JsonArray aliases = zimbraResponse.result().getJsonArray("aliases");
                        if(aliases.size() > 1) {
                            log.warn("More than one alias for address : " + completeCleanAddress);
                        }
                        if(!aliases.isEmpty()) {
                            this.neoId = aliases.getString(0);
                        }
                    } else {
                        this.neoId = groupService.getGroupId(completeCleanAddress);
                    }
                    handler.handle(this.neoId);
                });
            } else {
                JsonArray results = sqlResponse.right().getValue();
                if(results.size() > 1) {
                    log.warn("More than one user id for address : " + completeCleanAddress);
                }
                this.neoId = results.getJsonObject(0).getString(SqlZimbraService.NEO4J_UID);
                handler.handle(this.neoId);
            }
        });
    }
}
