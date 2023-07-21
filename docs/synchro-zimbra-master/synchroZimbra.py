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
import argparse
import ldap
import os, sys
import datetime
import subprocess
from shutil import copyfile
import errno

# Import des modules spécifiques pour la synchro
import zimbraGlobal as G
import zimbraExport
import zimbraCompare
import zimbraBuild

# Fonction usage
def usage():
    usage_def = argparse.ArgumentParser(
        description='Création en masse des compte zimbra non existants pour un porteur')
    usage_def.add_argument('-d', '--debug', dest='_DEBUG', action='store',
                           default=0, type=int, choices=[1, 2, 3],
                           help='Active le mode Debug niveau 1, niveau 2, niveau 3')
    usage_def.add_argument('-e', '--etape', dest='_ETAPES', action='store', default="all",
                           choices=["all", "export", "compare", "build", "import"],
                           help='Action à réaliser : all (default)')
    usage_def.add_argument('-v', '--version', dest='_VERSION', action='version',
                           version='%(prog)s v1.0.0',help='Version')

    args = usage_def.parse_args()
    return args

# Comptage durée totale du script
def totalTimeCountScript(isStart):
    if isStart:
        global dateScriptStart
        dateScriptStart = datetime.datetime.now()
        G.info("Debut synchro zimbra")
    else:
        dateScriptEnd = datetime.datetime.now()
        G.info("Fin synchro zimbra")
        diff = dateScriptEnd - dateScriptStart
        days, seconds = diff.days, diff.seconds
        hours = days * 24 + seconds // 3600
        minutes = (seconds % 3600) // 60
        seconds = seconds % 60
        G.info(	"Duree Totale du traitement :"
                   + str(hours) + "h" + str(minutes)
                   + "m" + str(seconds) + "s")

# Permet d'importer le fichier _file dans zimbra
def importZimbra(_file):
    importZimbraFile = subprocess.Popen("/opt/zimbra/bin/zmprov -f " + _file,
                                        stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE, shell=True)
    _stdout, _stderr = importZimbraFile.communicate()

    isOk = True
    countError = 0
    if not len(_stderr) == 0:
        G.error(_stderr)
        isOk = False
        # Comptages d'erreurs et comptages de comptes modifiés/créés
        countError = _stderr.count("ERROR")
    countOk = _stdout.count("prov>")
    countOk = countOk - countError - 1
    G.info("Le nombre total d'erreur = " + str(countError))
    G.info("Le nombre total de comptes créés/modifiés = " + str(countOk))
    return isOk

def silentremove(filename):
    try:
        os.remove(filename)
    except OSError as e: # this would be "except OSError, e:" before Python 2.6
        if e.errno != errno.ENOENT: # errno.ENOENT = no such file or directory
            raise # re-raise exception if a different error occurred

def main():

    args = usage()
    G.initSettings(args)

    G.initVariable()

    totalTimeCountScript(True)


    # Choix de l etape au lancement du script
    etapes = {'export': False, 'compare': False, 'build': False, 'import': False}
    if G.ARGS._ETAPES == 'all':
        etapes['export'] = True
        etapes['compare'] = True
        etapes['build'] = True
        etapes['import'] = True
    else:
        etapes[G.ARGS._ETAPES] = True
    try:
        ## Connexion au ldap Zimbra
        ldap.set_option(ldap.OPT_X_TLS_REQUIRE_CERT, ldap.OPT_X_TLS_NEVER)
        G.Z.ldapCon = ldap.initialize(G.Z.URL)
        G.Z.ldapCon.protocol_version = ldap.VERSION3
        G.Z.ldapCon.simple_bind_s(G.Z.USER, G.Z.PWD)
    except ldap.LDAPError, error:
        G.warn("LDAP ZIMBRA connexion error : " + error[0]['desc'])

    if etapes['export']:
        G.info("ETAPE n1 : EXPORT")
        # Récupération des infos structures NG via le WebService
        webServiceNgStructures = zimbraExport.listAllStructures()
        G.debug2("Liste des Structures : " + str(webServiceNgStructures))

        # Récupération des comptes NG via le WebService
        webserviceGroupList = []
        webServiceNgAccount = zimbraExport.listAllEtabUsers(webServiceNgStructures, webserviceGroupList)
        G.debug3("Liste des Comptes utilisateurs NG : "
                 + str(webServiceNgAccount))

        zimbraExport.saveWsDataToDbm(G.WS_DBM_FILENAME, webServiceNgAccount, G.W.ID)
        zimbraExport.saveWsDataToDbm(G.WS_DBM_GROUPS_FILENAME, webserviceGroupList, "groupId")
        len_webservice_account = str(len(webServiceNgAccount))
        len_webservice_group = str(len(webserviceGroupList))
        del webServiceNgAccount
        del webserviceGroupList


        ldapZimbraAccount = zimbraExport.ldapSearchToList(	G.Z.ldapCon,
                                                              G.Z.BASE_DN,
                                                              G.Z.LDAP_ATTRS,
                                                              G.Z.LDAP_FILTER)

        ldapZimbraGroups = zimbraExport.ldapSearchToList(G.Z.ldapCon,
                                                          G.Z.BASE_DN_GROUPS,
                                                          G.Z.LDAP_ATTRS_GROUPS,
                                                          G.Z.LDAP_FILTER_GROUPS)

        G.debug3("Liste des Comptes utilisateurs Zimbra : "
                 + str(ldapZimbraAccount))


        G.info("Nombre de comptes Total trouves :")
        G.info("ENT NG : " + len_webservice_account)
        G.info("ZIMBRA : " + str(len(ldapZimbraAccount)))
        G.info("Nombre de groupes Total trouves :")
        G.info("ENT NG : " + len_webservice_group)
        G.info("ZIMBRA : " + str(len(ldapZimbraGroups)))

        # On met l'ensemble des comptes dans des dbm avec pour clé ENTPersonLogin et uid
        # pour faire les comparaison et on libère les listes
        # /!\ externalId est modifié en UTF-8 dans la fonction listJsonToDbm
        zimbraExport.saveZimbraUsersToDbm(G.Z_DBM_FILENAME, ldapZimbraAccount, G.Z.ID, G.Z.LOGIN)
        zimbraExport.saveZimbraDataToDbm(G.Z_DBM_GROUPS_FILENAME, ldapZimbraGroups, G.Z.CN)

        # Nous supprimons ces deux listes
        del ldapZimbraAccount
        del ldapZimbraGroups

    # Il n'est plus necessaire de rester connecté au ldap
    try:
        G.Z.ldapCon.unbind_s()
    except ldap.LDAPError, erreur:
        G.error("Ldap closing connexion error : " + erreur[0]['desc'])


    if etapes['compare']:
        G.info("ETAPE n2 : COMPARE")
        # Nettoyage des comptes ENT si leur email existe
        zimbraCompare.cleanDbmFromDbm(
            G.WS_DBM_FILENAME,
            G.Z_DBM_FILENAME,
            G.Z_MOD_ACC_DBM_FILENAME
        )
        zimbraCompare.cleanGroupsDbm(
            G.WS_DBM_GROUPS_FILENAME,
            G.Z_DBM_GROUPS_FILENAME,
            G.Z_MOD_GRP_DBM_FILENAME
        )

    if etapes['build']:
        G.info("ETAPE n3 : BUILD")

        # Creation des fichiers txt a importer dans zimbra
        # (ne seront importés que les creations et les modifications des users)
        silentremove(G.Z_CREATE_ACC_FILENAME)
        silentremove(G.Z_MODIFY_ACC_FILENAME)
        silentremove(G.Z_DELETE_ACC_FILENAME)
        silentremove(G.Z_PRIO_EXEC_FILENAME)
        silentremove(G.Z_EXEC_FINAL_FILENAME)
        silentremove(G.Z_SEND_MAIL_FILENAME)
        silentremove(G.Z_GROUPS_FILENAME)
        # CREATION
        zimbraBuild.createAccountFilesZimbra(	False,
                                                 G.WS_DBM_FILENAME,
                                                 G.Z_CREATE_ACC_FILENAME)

        # AUTRES MODIFICATIONS
        zimbraBuild.createAccountFilesZimbra(	True,
                                                 G.Z_MOD_ACC_DBM_FILENAME,
                                                 G.Z_MODIFY_ACC_FILENAME)
        # COMPTES SUPPRIMES
        zimbraBuild.deletedAccountFilesZimbra(	G.Z_DBM_FILENAME,
                                                  G.Z_DELETE_ACC_FILENAME,
                                                  G.Z.DOMAIN)
        zimbraBuild.createGroupsZimbra( False, G.WS_DBM_GROUPS_FILENAME,
                                        G.Z_GROUPS_FILENAME,
                                        G.Z.DOMAIN)
        zimbraBuild.createGroupsZimbra( True, G.Z_MOD_GRP_DBM_FILENAME,
                                        G.Z_MODIFY_GRP_FILENAME,
                                        G.Z.DOMAIN)

        os.remove(G.WS_DBM_FILENAME)
        os.remove(G.WS_DBM_GROUPS_FILENAME)
        os.remove(G.Z_DBM_GROUPS_FILENAME)
        os.remove(G.Z_MOD_ACC_DBM_FILENAME)
        os.remove(G.Z_MOD_GRP_DBM_FILENAME)
        os.remove(G.Z_DBM_FILENAME)

    if etapes['import']:
        G.info("ETAPE n4 : IMPORT ")
        # Import dans zimbra des modifications prioritaires
        if not os.path.getsize(G.Z_PRIO_EXEC_FILENAME) == 0:
            G.info("Chargement des modifications prioritaires...")
            bInjectionPrio = importZimbra(G.Z_PRIO_EXEC_FILENAME)
            if not bInjectionPrio:
                G.fatal("Erreur lors de l'injection des modifications prioritaires, arrêt de la synchro")
            
        # Import dans zimbra des nouveaux comptes
        if not os.path.getsize(G.Z_CREATE_ACC_FILENAME) == 0:
            G.info("Chargement des créations de comptes...")
            G.gVerifInjectionFinal = importZimbra(G.Z_CREATE_ACC_FILENAME)
            copyfile(G.Z_CREATE_ACC_FILENAME, G.Z_SEND_MAIL_FILENAME)

        # Import dans zimbra des modifications de comptes
        if not os.path.getsize(G.Z_MODIFY_ACC_FILENAME) == 0:
            G.info("Chargement des modifications de comptes...")
            if not importZimbra(G.Z_MODIFY_ACC_FILENAME):
                G.gVerifInjectionFinal = False

        # Import dans zimbra des modifications de comptes
        if not os.path.getsize(G.Z_DELETE_ACC_FILENAME) == 0:
            G.info("Chargement des suppressions logiques de comptes...")
            if not importZimbra(G.Z_DELETE_ACC_FILENAME):
                G.gVerifInjectionFinal = False

        # Import dans zimbra des ajouts de groupes
        if not os.path.getsize(G.Z_GROUPS_FILENAME) == 0:
            G.info("Chargement des ajouts de groupes...")
            if not importZimbra(G.Z_GROUPS_FILENAME):
                G.gVerifInjectionFinal = False

        # Import dans zimbra des modifications de groupes
        if not os.path.getsize(G.Z_MODIFY_GRP_FILENAME) == 0:
            G.info("Chargement des modifications de groupes...")
            if not importZimbra(G.Z_MODIFY_GRP_FILENAME):
                G.gVerifInjectionFinal = False


    # Comptage durée totale de traitement du script
    totalTimeCountScript(False)

    # Vérification si l'injection Finale s'est bien terminée,
    # sinon sortie en erreur
    if not G.gVerifInjectionFinal:
        G.fatal("L'injection ne s'est pas déroulée correctement")

if __name__ == '__main__':
    main()
    G.closeFiles()
    sys.exit(0)
