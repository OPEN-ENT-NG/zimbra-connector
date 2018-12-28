package fr.openent.zimbra.helper;

import fr.openent.zimbra.service.data.Neo4jZimbraService;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.openent.zimbra.service.data.SqlZimbraService;
import fr.openent.zimbra.service.impl.*;
import fr.openent.zimbra.service.data.SqlSynchroService;
import fr.openent.zimbra.service.synchro.SynchroGroupService;
import fr.openent.zimbra.service.synchro.SynchroMailerService;
import fr.openent.zimbra.service.synchro.SynchroService;
import fr.openent.zimbra.service.synchro.SynchroUserService;
import fr.wseduc.webutils.email.EmailSender;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.entcore.common.email.EmailFactory;
import org.entcore.common.notification.TimelineHelper;

@SuppressWarnings("unused")
public class ServiceManager {

    private static ServiceManager serviceManager = null;


    private TimelineHelper timelineHelper;
    private EmailSender emailSender;

    private SoapZimbraService soapService;
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
    private ExpertModeService expertModeService;


    private SynchroUserService synchroUserService;
    private SynchroService synchroService;
    private SqlSynchroService sqlSynchroService;
    private SynchroGroupService synchroGroupService;
    private SynchroMailerService synchroMailerService;



    private ServiceManager(Vertx vertx, JsonObject config, EventBus eb, String pathPrefix) {

        timelineHelper = new TimelineHelper(vertx, eb, config);
        EmailFactory emailFactory = new EmailFactory(vertx, config);
        emailSender = emailFactory.getSender();

        this.sqlService = new SqlZimbraService(vertx, config.getString("db-schema", "zimbra"));
        this.sqlSynchroService = new SqlSynchroService(config.getString("db-schema", "zimbra"));
        this.soapService = new SoapZimbraService(vertx);
        this.neoService = new Neo4jZimbraService();
        this.synchroUserService = new SynchroUserService(sqlService, sqlSynchroService);
        this.userService = new UserService(soapService, synchroUserService, sqlService);
        this.folderService = new FolderService(soapService);
        this.signatureService = new SignatureService(userService, soapService);
        this.messageService = new MessageService(soapService, folderService,
                sqlService, userService, synchroUserService);
        this.attachmentService = new AttachmentService(soapService, messageService, vertx, config);
        this.notificationService = new NotificationService(userService, pathPrefix, timelineHelper);
        this.communicationService = new CommunicationService();
        this.groupService = new GroupService(soapService, sqlService, synchroUserService);
        this.expertModeService = new ExpertModeService();

        this.synchroService = new SynchroService(sqlSynchroService);
        this.synchroGroupService = new SynchroGroupService(soapService, synchroUserService);
        this.synchroMailerService = new SynchroMailerService(sqlSynchroService);

        soapService.setServices(userService, synchroUserService);
        synchroUserService.setUserService(userService);
    }

    public static ServiceManager init(Vertx vertx, JsonObject config, EventBus eb, String pathPrefix) {
        if(serviceManager == null) {
            serviceManager = new ServiceManager(vertx, config, eb, pathPrefix);
        }
        return serviceManager;
    }

    public static ServiceManager getServiceManager() {
        return serviceManager;
    }

    public TimelineHelper getTimelineHelper() {
        return timelineHelper;
    }

    public EmailSender getEmailSender() {
        return emailSender;
    }

    public SoapZimbraService getSoapService() {
        return soapService;
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

    public ExpertModeService getExpertModeService() {
        return expertModeService;
    }

    public SynchroUserService getSynchroUserService() {
        return synchroUserService;
    }

    public SynchroService getSynchroService() {
        return synchroService;
    }

    public SqlSynchroService getSqlSynchroService() {
        return sqlSynchroService;
    }

    public SynchroGroupService getSynchroGroupService() {
        return synchroGroupService;
    }

    public SynchroMailerService getSynchroMailerService() {
        return synchroMailerService;
    }
}
