/*
 * Copyright (c) Région Ile-de-France, Région Nouvelle-Aquitaine, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.zimbra.helper;

import fr.openent.zimbra.model.SlackConfiguration;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

@SuppressWarnings("WeakerAccess")
public class ConfigManager {

    private static final int DEFAULT_ACTION = 0;
    public static final int SYNC_ACTION = 1;
    public static final int UPDATE_ACTION = 2;

    private static final String NOSYNC = "no-sync";
    private static final String NOUPDATE = "no-update";
    public static final String SYNC_NEO = "neo4j";
    public static final String SYNC_SQL = "postgres";

    private final JsonObject rawConfig;
    private final JsonObject publicConfig;

    private final int httpClientMaxPoolSize;
    private final int mailListLimit;
    private final int mailListLimitConversation;

    private final String host;
    private final String dbSchema;
    private final String zimbraUri;
    private final String zimbraAdminUri;
    private final String zimbraAdminAccount;
    private final String zimbraAdminPassword;
    private final String preauthKey;
    private final String zimbraDomain;
    private final Integer zimbraFileUploadMaxSize;

    private final String synchroLang;
    private final String synchroCronDate;
    private final String synchroFromMail;

    private final String appSynchroType;
    private final String syncSynchroType;
    private final String appSyncTtl;

    private final int recallWorkerMaxQueue;
    private final String recallCron;
    private final String mailerCron;
    private final Integer maxRecipients;
    private final int devLevel;
    private final JsonObject mailConfig;
    private final String sharedFolderName;
    private String addressBookAccountName;
    private final Integer addressBookSynchroTtl;
    private final String structureAddressBookSynchroTtl;
    private Integer sqlInsertPaginationSize;

    private boolean forceExpertMode;
    private boolean enableAddressBookSynchro;

    private final boolean purgeEmailedContacts;

    private final boolean forceSyncAdressBook;

    private final int saveDraftAutoTime;
    private final int sendTimeout;

    private final int structureToSynchroABLimit;

    private final String filterUserProfileSynchAB;

    // Bug in Zimbra : when getting messages in conversations, alternative parts are inverted
    private boolean invertAltPartInConvMsg;

    private CircuitBreakerOptions circuitBreakerOptions;

    private SlackConfiguration slackConfiguration;

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    public ConfigManager(JsonObject config) {
        this.rawConfig = config;
        this.publicConfig = rawConfig.copy();
        initPublicConfig();
        this.httpClientMaxPoolSize = config.getInteger("http-client-max-pool-size", 0);
        this.mailListLimit = config.getInteger("mail-list-limit", 10);
        this.mailListLimitConversation = config.getInteger("mail-list-limit-thread", 1000);
        this.host = config.getString("host", "");
        this.dbSchema = config.getString("db-schema", "zimbra");
        this.zimbraUri = config.getString("zimbra-uri", "");
        this.zimbraAdminUri = config.getString("zimbra-admin-uri", "");
        this.zimbraAdminAccount = config.getString("admin-account","");
        this.zimbraAdminPassword = config.getString("admin-password","");
        this.preauthKey = config.getString("preauth-key","");
        this.zimbraDomain = config.getString("zimbra-domain", "");
        this.synchroLang = config.getString("zimbra-synchro-lang", "fr");
        this.synchroCronDate = config.getString("zimbra-synchro-cron", "");
        this.synchroFromMail = config.getString("zimbra-synchro-frommail", "zimbra-sync@cgi.com");
        this.zimbraFileUploadMaxSize = config.getInteger("zimbra-file-upload-max-size", 20);
        String appSynchroType = config.getString("app-synctype", SYNC_NEO);
        String syncSynchroType = config.getString("sync-synctype", SYNC_NEO);
        this.appSyncTtl = config.getString("app-sync-ttl", "30 minutes");
        this.mailerCron = config.getString("zimbra-mailer-cron", "");
        this.recallCron = config.getString("recall-cron", "");
        this.recallWorkerMaxQueue = config.getInteger("recall-worker-max-queue", 10000);
        this.maxRecipients = config.getInteger("max-recipients", 50);
        this.mailConfig = config.getJsonObject("mail-config", new JsonObject());
        this.sharedFolderName = config.getString("shared-folder-name", "-- Carnets Adresses ENT --");
        this.addressBookAccountName  = config.getString("address-book-account", "");
        this.addressBookSynchroTtl = config.getInteger("abook-sync-ttl-minutes", 1440); // default 24h
        this.structureAddressBookSynchroTtl = config.getString("structure-abook-sync-delay", "1 day");
        this.sqlInsertPaginationSize = config.getInteger("sql-insert-pagination-size", 5000);
        this.invertAltPartInConvMsg = config.getBoolean("invert-alt-part-in-conv-msg", false);
        this.purgeEmailedContacts = config.getBoolean("purge-emailed-contacts",false);
        this.forceSyncAdressBook = config.getBoolean("force-synchro-adressbook",false);
        this.saveDraftAutoTime = config.getInteger("save-draft-auto-time", 60000);
        this.sendTimeout = config.getInteger("send-timeout", 5000);
        this.structureToSynchroABLimit = config.getInteger("limit-structures-synchro-ab",5);
        this.filterUserProfileSynchAB = config.getString("filter-profile-sync-ab","");

        // In case of emergency
        this.forceExpertMode = config.getBoolean("force-expert-mode", false);
        this.enableAddressBookSynchro = config.getBoolean("enable-addressbook-synchro", true);

        String devLevelStr = config.getString("dev-level", "");
        if(NOSYNC.equals(devLevelStr)) {
            devLevel = SYNC_ACTION;
        } else if (NOUPDATE.equals(devLevelStr)) {
            devLevel = UPDATE_ACTION;
        } else {
            devLevel = DEFAULT_ACTION;
        }

        if(!appSynchroType.equals(SYNC_NEO) && !appSynchroType.equals(SYNC_SQL)) {
            this.appSynchroType = SYNC_NEO;
        } else {
            this.appSynchroType = appSynchroType;
        }
        if(!syncSynchroType.equals(SYNC_NEO) && !syncSynchroType.equals(SYNC_SQL)) {
            this.syncSynchroType = SYNC_NEO;
        } else {
            this.syncSynchroType = syncSynchroType;
        }

        if(host.isEmpty() || zimbraUri.isEmpty() || zimbraAdminAccount.isEmpty() || zimbraAdminUri.isEmpty()
                || zimbraAdminPassword.isEmpty() || preauthKey.isEmpty() || zimbraDomain.isEmpty()
                || addressBookAccountName.isEmpty() ) {
            log.fatal("Zimbra : Missing configuration in conf.properties");
        }

        this.circuitBreakerOptions = new CircuitBreakerOptions(config.getJsonObject("circuit-breaker", new JsonObject()));
        JsonObject slackConfig = config.getJsonObject("slack", new JsonObject());
        this.slackConfiguration = new SlackConfiguration(slackConfig.getString("api-uri", ""), slackConfig.getString("api-token", ""), slackConfig.getString("channel", ""), slackConfig.getString("bot-username", ""), config.getString("host", ""));
        initPublicConfig();
    }

    public boolean isActionBlocked(int actionLevel) {
        return actionLevel <= devLevel;
    }

    JsonObject getRawConfig() { return rawConfig;}
    public JsonObject getPublicConfig() { return publicConfig;}
    public int getHttpClientMaxPoolSize() { return httpClientMaxPoolSize;}
    public int getMailListLimit() { return mailListLimit;}
    public int getMailListLimitConversation() { return mailListLimitConversation;}
    public String getHost() { return host;}
    public String getDbSchema() { return dbSchema;}
    public String getZimbraUri() { return zimbraUri;}
    public String getZimbraAdminUri() { return zimbraAdminUri;}
    public String getZimbraAdminAccount() { return zimbraAdminAccount;}
    public String getZimbraAdminPassword() { return zimbraAdminPassword;}
    public String getPreauthKey() { return preauthKey;}
    public String getZimbraDomain() { return zimbraDomain;}
    public Integer getZimbraFileUploadMaxSize() { return zimbraFileUploadMaxSize;}
    public String getAppSynchroType() { return appSynchroType;}
    public String getSyncSynchroType() { return syncSynchroType;}
    public String getAppSyncTtl() { return appSyncTtl;}
    public String getSynchroLang() { return synchroLang;}
    public String getSynchroCronDate() { return synchroCronDate;}
    public String getSynchroFromMail() { return synchroFromMail;}
    public String getMailerCron() { return mailerCron;}
    public String getRecallCron() { return recallCron; }
    public int getRecallWorkerMaxQueue() { return recallWorkerMaxQueue; }
    public Integer getMaxRecipients() { return maxRecipients;}
    public JsonObject getMailConfig() { return mailConfig;}
    public String getSharedFolderName() { return sharedFolderName;}
    public String getAddressBookAccountName() { return addressBookAccountName;}
    public Integer getAddressBookSynchroTtl() { return addressBookSynchroTtl;}
    public String getStructureAddressBookSynchroTtl() { return structureAddressBookSynchroTtl;}
    public Integer getSqlInsertPaginationSize() { return sqlInsertPaginationSize;}
    public boolean getInvertAltPartInConvMsg() { return invertAltPartInConvMsg;}
    public boolean getPurgeEmailedContacts() {return purgeEmailedContacts;}
    public boolean getForceSyncAdressBooks() {return forceSyncAdressBook;}
    public CircuitBreakerOptions getCircuitBreakerOptions() {
        return this.circuitBreakerOptions;
    }
    public SlackConfiguration getSlackConfiguration() { return this.slackConfiguration; }
    public boolean isForceExpertMode() { return forceExpertMode;}
    public boolean isEnableAddressBookSynchro() { return enableAddressBookSynchro;}
    public int getsaveDraftAutoTime() { return saveDraftAutoTime;}
    public int getSendTimeout() { return sendTimeout;}
    public int getStructureToSynchroABLimit() { return structureToSynchroABLimit;}
    public String getFilterUserProfileSynchAB() {return this.filterUserProfileSynchAB;}

    private void initPublicConfig() {
        publicConfig.put("admin-password", hidePasswd(rawConfig.getString("admin-password","")));
        publicConfig.put("preauth-key", hidePasswd(rawConfig.getString("preauth-key","")));
    }

    private String hidePasswd(String passwd) {
        String newpwd;
        if(passwd.length() < 8) {
            newpwd = StringUtils.repeat("*", passwd.length());
        } else {
            newpwd = passwd.substring(0, 3) + StringUtils.repeat("*", passwd.length() - 3);
        }
        return newpwd;
    }

}
