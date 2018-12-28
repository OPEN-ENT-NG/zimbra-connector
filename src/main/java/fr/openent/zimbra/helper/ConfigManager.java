package fr.openent.zimbra.helper;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ConfigManager {


    private final String zimbraUri;
    private final String zimbraAdminUri;
    private final String zimbraAdminAccount;
    private final String zimbraAdminPassword;
    private final String preauthKey;
    private final String zimbraDomain;
    private final String synchroLang;
    private final String synchroCronDate;
    private final String synchroFromMail;
    private final String mailerCron;
    private final Integer maxRecipients;

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    public ConfigManager(JsonObject config) {
        this.zimbraUri = config.getString("zimbra-uri", "");
        this.zimbraAdminUri = config.getString("zimbra-admin-uri", "");
        this.zimbraAdminAccount = config.getString("admin-account","");
        this.zimbraAdminPassword = config.getString("admin-password","");
        this.preauthKey = config.getString("preauth-key","");
        this.zimbraDomain = config.getString("zimbra-domain", "");
        this.synchroLang = config.getString("zimbra-synchro-lang", "fr");
        this.synchroCronDate = config.getString("zimbra-synchro-cron", "");
        this.synchroFromMail = config.getString("zimbra-synchro-frommail", "zimbra-sync@cgi.com");
        this.mailerCron = config.getString("zimbra-mailer-cron", "");
        this.maxRecipients = config.getInteger("max-recipients", 50);

        if(zimbraUri.isEmpty() || zimbraAdminAccount.isEmpty() || zimbraAdminUri.isEmpty()
                || zimbraAdminPassword.isEmpty() || preauthKey.isEmpty() || zimbraDomain.isEmpty()) {
            log.fatal("Zimbra : Missing configuration in conf.properties");
        }
    }

    public String getZimbraUri() { return zimbraUri;}
    public String getZimbraAdminUri() { return zimbraAdminUri;}
    public String getZimbraAdminAccount() { return zimbraAdminAccount;}
    public String getZimbraAdminPassword() { return zimbraAdminPassword;}
    public String getPreauthKey() { return preauthKey;}
    public String getZimbraDomain() { return zimbraDomain;}
    public String getSynchroLang() { return synchroLang;}
    public String getSynchroCronDate() { return synchroCronDate;}
    public String getSynchroFromMail() { return synchroFromMail;}
    public String getMailerCron() { return mailerCron;}
    public Integer getMaxRecipients() { return maxRecipients;}

}
