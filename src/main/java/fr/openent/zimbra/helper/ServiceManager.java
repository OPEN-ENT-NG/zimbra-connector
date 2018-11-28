package fr.openent.zimbra.helper;

import fr.openent.zimbra.service.impl.*;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.entcore.common.notification.TimelineHelper;

public class ServiceManager {

    private static ServiceManager serviceManager = null;

    private TimelineHelper timelineHelper;
    private UserService userService;
    private FolderService folderService;
    private AttachmentService attachmentService;
    private MessageService messageService;
    private SignatureService signatureService;
    private SqlZimbraService sqlService;
    private NotificationService notificationService;
    private CommunicationService communicationService;
    private GroupService groupService;
    private Neo4jZimbraService neoService;


    private void initServiceManager(Vertx vertx, JsonObject config, EventBus eb, String pathPrefix) {

        timelineHelper = new TimelineHelper(vertx, eb, config);
        this.sqlService = new SqlZimbraService(vertx, config.getString("db-schema", "zimbra"));
        SoapZimbraService soapService = new SoapZimbraService(vertx, config);
        SynchroUserService synchroUserService = new SynchroUserService(soapService, sqlService);
        this.userService = new UserService(soapService, synchroUserService, sqlService);
        this.folderService = new FolderService(soapService);
        this.signatureService = new SignatureService(userService, soapService);
        this.messageService = new MessageService(soapService, folderService,
                sqlService, userService, synchroUserService);
        this.attachmentService = new AttachmentService(soapService, messageService, vertx, config);
        this.notificationService = new NotificationService(soapService, userService, pathPrefix, timelineHelper);
        this.communicationService = new CommunicationService(messageService);
        this.groupService = new GroupService(soapService, sqlService, synchroUserService);
        this.neoService = new Neo4jZimbraService();


        soapService.setServices(userService, synchroUserService);
        synchroUserService.setUserService(userService);
    }

    public static ServiceManager init(Vertx vertx, JsonObject config, EventBus eb, String pathPrefix) {
        if(serviceManager == null) {
            serviceManager = new ServiceManager();
            serviceManager.initServiceManager(vertx, config, eb, pathPrefix);
        }
        return serviceManager;
    }

    public static ServiceManager getServiceManager() {
        return serviceManager;
    }

    public TimelineHelper getTimelineHelper() {
        return timelineHelper;
    }

    public SqlZimbraService getSqlService() {
        return sqlService;
    }

    public UserService getUserService() {
        return userService;
    }

    public FolderService getFolderService() {
        return folderService;
    }

    public SignatureService getSignatureService() {
        return signatureService;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public AttachmentService getAttachmentService() {
        return attachmentService;
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }

    public CommunicationService getCommunicationService() {
        return communicationService;
    }

    public GroupService getGroupService() {
        return groupService;
    }

    public Neo4jZimbraService getNeoService() {
        return neoService;
    }


}
