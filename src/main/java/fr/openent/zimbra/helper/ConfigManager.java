package fr.openent.zimbra.helper;

import fr.openent.zimbra.Zimbra;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ConfigManager {


    private String zimbraUri;
    private String zimbraAdminUri;
    private String zimbraAdminAccount;
    private String zimbraAdminPassword;
    private String preauthKey;
    private String zimbraDomain;
    private String synchroLang;

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    public ConfigManager(JsonObject config) {
        this.zimbraUri = config.getString("zimbra-uri", "");
        this.zimbraAdminUri = config.getString("zimbra-admin-uri", "");
        this.zimbraAdminAccount = config.getString("admin-account","");
        this.zimbraAdminPassword = config.getString("admin-password","");
        this.preauthKey = config.getString("preauth-key","");
        this.zimbraDomain = config.getString("zimbra-domain", "");
        this.synchroLang = config.getString("zimbra-synchro-lang", "fr");

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
}
