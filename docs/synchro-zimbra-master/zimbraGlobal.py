#!/usr/bin/python
# -*- coding: utf-8 -*-
# Installation des paquets : python-ldap python-argparse requests
# Detail : Vérifie les utilisateurs présents dans NG et dans Zimbra, les compares, et relève les différences suivantes:
#           - Compte non existant sur Zimbra mais présent sur NG, alors on le créé
#           - Compte comportant un login différent, nous le notons dans un fichier mais ne modifions rien
#           - Compte existant sur Zimbra, mais attributs différents sur NG, alors on le modifie sur Zimbra
#           - Compte inexistant sur NG mais présent sur Zimbra, alors nous le notons dans un fichier
#
# Dev : QMER, TCOL, LBRO

# Import des modules
import ConfigParser
import ldap
import os, sys
import datetime
import re

# Définition des variables globales
Z = W = ARGS = SYNCHRODATE = CONFIG_FILE = DIR = COMPARE_MAP = None
# Fichier de log
LOGFILE = None
# Liste des UAI à traiter
gListUAIEtabs = []
# Booleen de vérification de l'injection
gVerifInjectionFinal = True

# Fichiers de sortie
Z_CREATE_ACC_FILENAME =  Z_SEND_MAIL_FILENAME =  Z_MODIFY_ACC_FILENAME = Z_GROUPS_FILENAME = Z_MODIFY_GRP_FILENAME = None
Z_DELETE_ACC_FILENAME =  Z_EXEC_FINAL_FILENAME =  Z_PRIO_EXEC_FILENAME = Z_LOGIN_DIFF_FILENAME = None
# Fichiers temporaires
WS_DBM_FILENAME = WS_DBM_GROUPS_FILENAME = Z_DBM_FILENAME = Z_DBM_GROUPS_FILENAME = None
Z_MOD_ACC_DBM_FILENAME = Z_MOD_GRP_DBM_FILENAME = None

def	initSettings(script_args):
    global ARGS, DEBUG, DIR, CONFIG_FILE, Z, W
    global COMPARE_MAP, SYNCHRODATE, LOGFILE
    # Arguments du script
    ARGS = script_args
    DEBUG = ARGS._DEBUG
    # Répertoire du script
    DIR = os.path.dirname(os.path.abspath(__file__))
    # Chemin du fichier de configuration
    CONFIG_FILE = DIR + "/synchroZimbra.conf"
    # Initialisation des champs Zimbra
    Z = zimbraVars()
    # Initialisation des champs du webservice
    W = webserviceVars()
    # Map de comparaison des valeurs entre Zimbra et WS
    COMPARE_MAP = {	Z.NOM:W.NOM,
                       Z.PRENOM:W.PRENOM,
                       Z.ETAB:W.ETAB,
                       Z.FONCTION:W.PROFILE,
                       Z.CN:W.CN,
                       Z.ENTLOGIN:W.LOGIN,
                       Z.STATUS:W.STATUS }
    # Date de la synchronisation
    SYNCHRODATE = str(datetime.datetime.now().strftime("%Y%m%d"))


def debug(message):
    if DEBUG > 0:
        LOGFILE.write(str(datetime.datetime.now().strftime("%Y-%m-%d_%H.%M.%S")) + " DEBUG " + message + "\n")
        LOGFILE.flush()

def debug2(message):
    if DEBUG > 1:
        LOGFILE.write(str(datetime.datetime.now().strftime("%Y-%m-%d_%H.%M.%S")) + " DEBUG2 " + message + "\n")

def debug3(message):
    if DEBUG > 2:
        LOGFILE.write(str(datetime.datetime.now().strftime("%Y-%m-%d_%H.%M.%S")) + " DEBUG3 " + message + "\n")

def info(message):
    LOGFILE.write(str(datetime.datetime.now().strftime("%Y-%m-%d_%H.%M.%S")) + " INFO " + message + "\n")
    LOGFILE.flush()

def warn(message):
    LOGFILE.write(str(datetime.datetime.now().strftime("%Y-%m-%d_%H.%M.%S")) + " WARN " + message + "\n")
    LOGFILE.flush()

def error(message):
    LOGFILE.write(str(datetime.datetime.now().strftime("%Y-%m-%d_%H.%M.%S")) + " ERROR " + message + "\n")
    LOGFILE.flush()

def fatal(message):
    LOGFILE.write(str(datetime.datetime.now().strftime("%Y-%m-%d_%H.%M.%S")) + " FATAL " + message + "\n")
    LOGFILE.close()
    sys.exit(2)

def closeFiles():
    LOGFILE.close()

# Initialisation des variables du fichier de conf
def initVariable():

    global CONFIG_FILE
    configPars = ConfigParser.ConfigParser()
    configPars.read(CONFIG_FILE)

    # Variable fichier liste des Etablissements
    isRelativePathGeneral = configPars.getboolean("general", "generalFilePathRelative")
    relativePathGeneral = DIR if isRelativePathGeneral else ""
    listeEtabsUAI = relativePathGeneral + configPars.get("general", "listeEtabsUAI")
    logFilePrefix = relativePathGeneral + configPars.get("general", "logFilePrefix")

    try:
        # Création du fichier de log
        global LOGFILE
        LOGFILE = open(logFilePrefix
                       + str(datetime.datetime.now().strftime("%Y-%m-%d_%H.%M.%S"))
                       + ".log", "w")
    except IOError:
        print '\n/!\ ERREUR lors de l\'ouverture du fichier :', logFilePrefix, '\n'
        sys.exit(2)

    # Récupération des valeurs du fichier
    # Variables serveur EntNG
    ngUrl = configPars.get("entngServeurDef", "url")
    ngProtocol = configPars.get("entngServeurDef", "protocol")
    W.URL = ngProtocol + "://" + ngUrl
    httpProxy = configPars.get("entngServeurDef", "http_proxy")
    httpsProxy = configPars.get("entngServeurDef", "https_proxy")
    W.USER = configPars.get("entngServeurDef", "user")
    W.PWD = configPars.get("entngServeurDef", "pwd")
    W.PROXIES = {
        'http': httpProxy,
        'https': httpsProxy,
    }

    # Variables ldap Zimbra
    zimbraServeur = configPars.get("zimbraServeurDef", "ip")
    zimbraPort = configPars.get("zimbraServeurDef", "port")
    Z.USER = configPars.get("zimbraServeurDef", "user")
    Z.PWD = configPars.get("zimbraServeurDef", "Pwd")
    Z.URL = 'ldaps://' + zimbraServeur + ':' + zimbraPort
    Z.DOMAIN = configPars.get("zimbraServeurDef", "zimbraDomain")
    Z.BASE_DN = "ou=people,dc=" + Z.DOMAIN.replace('.', ',dc=')
    Z.BASE_DN_GROUPS = "cn=groups,dc=" + Z.DOMAIN.replace('.', ',dc=')

    # Récupération des chemins des fichiers temporaires
    global WS_DBM_FILENAME, Z_DBM_FILENAME, Z_MOD_ACC_DBM_FILENAME, Z_MOD_GRP_DBM_FILENAME
    global WS_DBM_GROUPS_FILENAME, Z_DBM_GROUPS_FILENAME
    isRelativePathTmp = configPars.getboolean("tempFile", "tempFilePathRelative")
    relativePathTmp = DIR if isRelativePathTmp else ""
    WS_DBM_FILENAME = relativePathTmp + configPars.get("tempFile", "webServiceTempFile")
    WS_DBM_GROUPS_FILENAME = relativePathTmp + configPars.get("tempFile", "webServiceGroupTempFile")
    Z_DBM_FILENAME = relativePathTmp + configPars.get("tempFile", "zimbraTempFile")
    Z_DBM_GROUPS_FILENAME = relativePathTmp + configPars.get("tempFile", "zimbraGroupTempFile")
    Z_MOD_ACC_DBM_FILENAME = relativePathTmp \
                             + configPars.get("tempFile", "modifiedAccountToZimbraTempFile")
    Z_MOD_GRP_DBM_FILENAME = relativePathTmp \
                             + configPars.get("tempFile", "modifiedGroupToZimbraTempFile")

    # Récupération des chemins des fichiers de sortie
    global Z_CREATE_ACC_FILENAME, Z_MODIFY_ACC_FILENAME, Z_LOGIN_DIFF_FILENAME, Z_GROUPS_FILENAME, Z_MODIFY_GRP_FILENAME
    global Z_DELETE_ACC_FILENAME, Z_EXEC_FINAL_FILENAME, Z_PRIO_EXEC_FILENAME
    global Z_SEND_MAIL_FILENAME
    isRelativePathWork = configPars.getboolean("zmprovImport", "workFilePathRelative")
    relativePathWork = DIR if isRelativePathWork else ""
    Z_CREATE_ACC_FILENAME = relativePathWork + configPars.get("zmprovImport", "zmprovCaFile")
    Z_SEND_MAIL_FILENAME = relativePathWork + configPars.get("zmprovImport", "zmprovMtsFile")
    Z_MODIFY_ACC_FILENAME = relativePathWork + configPars.get("zmprovImport", "zmprovMaFile")
    Z_MODIFY_GRP_FILENAME = relativePathWork + configPars.get("zmprovImport", "zmprovMgFile")
    Z_DELETE_ACC_FILENAME = relativePathWork + configPars.get("zmprovImport", "zmprovNINFile")
    Z_EXEC_FINAL_FILENAME = relativePathWork + configPars.get("zmprovImport", "zmprovExecAccountsZimbra")
    Z_PRIO_EXEC_FILENAME = relativePathWork + configPars.get("zmprovImport", "zmprovPrioExecZimbra")
    Z_GROUPS_FILENAME = relativePathWork + configPars.get("zmprovImport", "zmprovGroupsZimbra")

    info("[LDAP ZIMBRA] URL : " + Z.URL)
    debug("[LDAP ZIMBRA] USER : " + Z.USER)
    debug("[LDAP ZIMBRA] PWD : " + Z.PWD)
    info("[Fichiers Etablissements] : " + listeEtabsUAI)

    verifAllEtabs(listeEtabsUAI)

# Stockage dans un tableau de chaque UAI Etablissement à traiter
def verifAllEtabs(listeEtabsUAI):
    #  Si oui, nous vérifions son contenu et le stockons dans un tableau "tabListUAIEtabs"
    # Nous vérifions que le fichier liste Etab existe.
    try:
        debug("Verification de la liste des etablissements : " + str(listeEtabsUAI))
        etabTotalCount = 0

        # Vérification du bon format de chaque UAI dans le fichier "listeEtablissements"
        with open(listeEtabsUAI, 'r') as fileEtabsUAI:
            for etabUAI in fileEtabsUAI:
                etabUAI = etabUAI.rstrip('\n')
                if (len(re.findall('[A-Za-z0-9]', etabUAI)) == 8) \
                        and (re.match("^[A-Za-z0-9]*$", etabUAI)):
                    etabTotalCount += 1
                    gListUAIEtabs.append(etabUAI)
                else:
                    # On arrête le traitement immédiatement si un UAI ne peut pas être traité
                    fatal("L'UAI de cet etabablissement ne comporte pas le bon format :" + etabUAI)

        info("Nombre total d etablissements a traiter :" + str(etabTotalCount))

    # Fin, si le fichier "listeEtablissements" n'existe pas
    except IOError:
        fatal("Erreur lors de l'ouverture du fichier :" + listeEtabsUAI)

# Constantes spécifiques Zimbra
class zimbraVars:
    def __init__(self):
        # Composants zmprov
        self.MODIFY_ACC = 'modifyAccount'
        self.CREATE_ACC = 'createAccount'
        self.RENAME_ACC = 'renameAccount'
        # Paramètres de recherche ldap
        self.LDAP_SCOPE = ldap.SCOPE_ONELEVEL
        # Paramètres de requête ldap
        self.URL = ""
        self.DOMAIN = ""
        self.BASE_DN = ""
        self.BASE_DN_GROUPS = ""
        self.USER = ""
        self.PWD = ""
        self.ldapCon = None
        # Champs récupérés dans le ldap Zimbra
        self.NOM = 'sn'
        self.PRENOM = 'givenName'
        self.ETAB = 'company'
        self.FONCTION = 'title'
        self.CN = 'cn'
        self.HIDEINGAL = 'zimbraHideInGal'
        self.LOGIN = 'uid'
        self.MAIL = 'mail'
        self.ID = 'zimbraMailAlias'
        self.DATE_MODIF = 'telexNumber'
        self.STATUS = 'zimbraAccountStatus'
        self.STATUS_LOCKED = 'locked'
        self.STATUS_ACTIVE = 'active'
        self.GROUP = 'ou'
        self.DISPLAYNAME = 'displayName'
        self.ENTLOGIN = 'labeledURI'
        self.MEMBER_URL = "memberURL"
        self.LDAP_ATTRS = [self.ETAB, self.FONCTION, self.LOGIN, self.ENTLOGIN,
                           self.NOM, self.PRENOM, self.MAIL, self.CN,
                           self.ID, self.HIDEINGAL, self.GROUP,
                           self.DATE_MODIF, self.STATUS]
        self.LDAP_ATTRS_GROUPS = [self.CN, self.DISPLAYNAME, self.MEMBER_URL]
        self.LDAP_FILTER = (
            "(&"
            + "(objectClass=zimbraAccount)"
            + "(!(zimbraIsAdminAccount=TRUE))"
            + "(!(zimbraIsSystemResource=TRUE))"
            + "(!(uid=listmaster)))"
        )
        self.LDAP_FILTER_GROUPS = "(objectClass=zimbraGroup)"
        self.MBRURL_PREF = "ldap:///??sub?(&(objectClass=zimbraAccount)(|(ou="
        self.MBRURL_SUFF = ")(ou=allgroupsaccount)))"

# Constantes spécifiques webservice
class webserviceVars:
    def __init__(self):
        # Paramètres de requête du WS
        self.URL = ""
        self.PROTOCOL = ""
        self.PROXIES = None
        self.USER = ""
        self.PWD = ""
        # Champs récupérés par le webservice
        self.NOM = 'lastName'
        self.PRENOM = 'firstName'
        self.ETAB = 'structures'
        self.FONCTION = 'functions'
        self.CLASSE = 'classes'
        self.CN = 'displayName'
        self.ETAB_NAME = 'name'
        self.UAI = 'UAI'
        self.ID = 'id'
        self.LOGIN = 'login'
        self.ZIMBRALOGIN = 'zimbralogin'
        self.GROUP = 'groups'
        self.PROFILE = 'profiles'
        self.STATUS = "blocked"
