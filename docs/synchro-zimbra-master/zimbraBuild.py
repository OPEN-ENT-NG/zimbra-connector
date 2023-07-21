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
import shelve
import re

# Import des modules spécifiques pour la synchro
import zimbraGlobal as G

SPCD = " \""
SPC = "\" \""
SPCF = "\" "

def dedupLogin(old_login, db_delete):
    increment = 0
    new_login = old_login
    while new_login in db_delete:
        increment = increment + 1
        new_login = old_login + "-" + str(increment)
    return new_login

def check_login(_modify, old_login, db_delete):
    login_regex = "^[A-Za-z0-9][A-Za-z0-9.+_-]*[A-Za-z0-9]$"
    if(_modify):
        return old_login
    loginUser = old_login
    if (not _modify and not re.match(login_regex, old_login)):
        if (re.match(login_regex, "0" + old_login)):
            loginUser = "0" + old_login
        else:
            if (re.match(login_regex, old_login + "0")):
                loginUser = old_login + "0"
            else:
                if (re.match(login_regex, "0" + old_login + "0")):
                    loginUser = "0" + old_login + "0"
        G.warn("Login incorrect, changement de login pour le compte " + loginUser)
    if re.match(login_regex, loginUser):
        return dedupLogin(loginUser, db_delete)
    else:
        G.warn("Impossible de creer le compte " + old_login + " : adresse mail incorrecte")
        return None

# Crée un fichier a importer via zmprov en se basant sur les comptes
# contenu dans le dbm avec pour domain _domain
def createAccountFilesZimbra(_modify,
                             _dbmFileCreate,
                             _fluxFilename):
    db_create = shelve.open(_dbmFileCreate, 'r')
    db_delete = shelve.open(G.Z_DBM_FILENAME, 'w')
    zmprovFlux = open(_fluxFilename, 'a+')
    zmprovFluxExec = open(G.Z_EXEC_FINAL_FILENAME, 'a+')
    zmprovFluxPrio = open(G.Z_PRIO_EXEC_FILENAME, 'a+')

    for k in db_create.keys():
        user = db_create[k]

        loginUser = check_login(_modify, user[G.W.ZIMBRALOGIN], db_delete)
        if loginUser is not None:
            account = createStringAccount(	_modify, loginUser, G.Z.DOMAIN,
                                              user[G.W.PRENOM],
                                              user[G.W.NOM],
                                              user[G.W.CN],
                                              user[G.W.ETAB],
                                              user[G.W.PROFILE],
                                              user[G.W.LOGIN],
                                              user[G.W.GROUP] if G.W.GROUP in user else [],
                                              user[G.Z.GROUP] if G.Z.GROUP in user else [],
                                              user[G.W.STATUS], "" )
            addalias = "aaa " + loginUser + "@" + G.Z.DOMAIN + " " + user[G.W.ID] + "@" + G.Z.DOMAIN + "\n"
            zmprovFlux.write(account)
            zmprovFluxExec.write(account)
            if not _modify:
                zmprovFlux.write(addalias)
                zmprovFluxExec.write(addalias)

            if _modify:
                G.debug3("Modification du compte : " + account)
            else:
                G.debug3("Création du compte : " + account)

    db_create.close()
    db_delete.close()
    zmprovFlux.close()
    zmprovFluxExec.close()
    zmprovFluxPrio.close()


def createStringAccount(_modify, _login, _domain, _givenName, _sn, _cn, _company, _title, _entlogin,
                        _groups_to_add, _groups_to_del, _status, _hideingal=None):
    verb = (G.Z.MODIFY_ACC if _modify else G.Z.CREATE_ACC)
    groupmod = "+" if _modify else ""
    password = ('' if _modify else SPC)
    account = verb + SPCD
    account = account + _login + "@" + _domain + SPC
    account = account + password
    account = account + G.Z.PRENOM + SPC + _givenName + SPC
    account = account + G.Z.NOM + SPC + _sn + SPC
    account = account + G.Z.CN + SPC + _cn + SPC
    account = account + G.Z.ETAB + SPC +  _company + SPC
    account = account + G.Z.FONCTION + SPC + _title + SPC
    account = account + G.Z.ENTLOGIN + SPC + _entlogin + SPC
    for group in _groups_to_add:
        if group is not None:
            account = account + groupmod + G.Z.GROUP + SPC + group.encode("utf-8") + SPC
    for group in _groups_to_del:
        if group is not None:
            account = account + "-" + G.Z.GROUP + SPC + group.encode("utf-8") + SPC
    account = account + G.Z.DATE_MODIF + SPC + G.SYNCHRODATE + SPC
    account = account + G.Z.STATUS + SPC + _status + SPC
    # Si un compte est supprimé, lui retirer tous ses groupes
    if _hideingal == 'TRUE':
        account = account + G.Z.GROUP + SPC + SPC
    account = account + G.Z.HIDEINGAL + SPC + _hideingal + "\"\n"
    return account


def deletedAccountFilesZimbra(	_dbmFileDeleteUser,
                                _resultFileDeleteUser, _domain):
    dbFluxDeleteUser = shelve.open(_dbmFileDeleteUser, 'r')
    zmprovFlux = open(_resultFileDeleteUser, 'a+')
    zmprovFluxExec = open(G.Z_EXEC_FINAL_FILENAME, 'a+')
    nbCompteASupp = 0
    nbCompteSupped = 0


    for k in dbFluxDeleteUser.keys():
        user = dbFluxDeleteUser[k]
        G.debug2("Compte a supprimer : " + str(user))
        # Si l'utilisateur était déjà supprimé, on ne le mets pas à jour
        if (G.Z.HIDEINGAL not in user
            or user[G.Z.HIDEINGAL][0] != "TRUE"
            or G.Z.STATUS not in user
            or user[G.Z.STATUS][0] == G.Z.STATUS_ACTIVE):
            nbCompteASupp += 1
            account = createStringAccount(True,
                                          user[G.Z.LOGIN][0] if G.Z.LOGIN in user else "",
                                          _domain,
                                          user[G.Z.PRENOM][0]if G.Z.PRENOM in user else "",
                                          user[G.Z.NOM][0]if G.Z.NOM in user else "",
                                          user[G.Z.CN][0]if G.Z.CN in user else "",
                                          user[G.Z.ETAB][0]if G.Z.ETAB in user else "",
                                          user[G.Z.FONCTION][0]if G.Z.FONCTION in user else "",
                                          "",
                                          [], [], G.Z.STATUS_LOCKED,
                                          'TRUE')
            zmprovFlux.write(account)
            zmprovFluxExec.write(account)
            G.debug3("Compte supprime : " + account)
        else:
            nbCompteSupped += 1

    G.info("Nombre de comptes déjà supprimés logiquement : " + str(nbCompteSupped))
    G.info("Nombre de comptes à supprimer logiquement : " + str(nbCompteASupp))
    G.info("Total : " + str(nbCompteSupped + nbCompteASupp))
    dbFluxDeleteUser.close()
    zmprovFlux.close()
    zmprovFluxExec.close()

def createGroupsZimbra(	_modify, _dbmFileCreateGroups,
                            _resultFileCreateGroups, _domain):
    dbGroups = shelve.open(_dbmFileCreateGroups, 'r')
    zmprovFlux = open(_resultFileCreateGroups, 'a+')
    zmprovFluxExec = open(G.Z_EXEC_FINAL_FILENAME, 'a+')

    verb = ("mdl" if _modify else "cddl")


    for k in dbGroups.keys():
        group = dbGroups[k]
        G.debug2("Groupe a creer : " + str(group))

        group_line = verb + " " + group["groupId"] + "@" + _domain
        group_line = group_line + " memberURL" + " '" + G.Z.MBRURL_PREF + group["groupId"] + G.Z.MBRURL_SUFF + "'"
        group_line = group_line + " displayName" + SPCD + group["groupName"].replace('"', '\\"') + SPCF
        if(not _modify):
            group_line = group_line + " zimbraIsACLGroup FALSE"
        group_line = group_line + "\n"
        group_line = group_line.encode("utf-8")
        zmprovFlux.write(group_line)
        zmprovFluxExec.write(group_line)
        G.debug3("Groupe cree : " + group_line)

    dbGroups.close()
    zmprovFlux.close()
    zmprovFluxExec.close()
