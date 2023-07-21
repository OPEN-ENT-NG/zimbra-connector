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
import ldap
import shelve
import json
import requests
import time

# Import des modules spécifiques pour la synchro
import zimbraGlobal as G

# Récupère la liste des informations de chaques établissements pour comparer id structure et nom structure
def listAllStructures():
    G.info("Récupération des informations depuis OpenENT NG")
    etabs = {} # creation du dictionnaire des structures

    # Récupération des informations sur les structures
    requrlStructuresName = (G.W.URL + "/zimbra/export/structures")
    G.info("Requete webservice etablissements : " + requrlStructuresName)
    rs = requests.get(	requrlStructuresName,
                          auth=(G.W.USER, G.W.PWD),
                          proxies=G.W.PROXIES)
    allStructuresData = json.loads(rs.text)

    for structure in allStructuresData:
        # Ne récupère que les infos des établissements souhaités (listeEtablissement)
        if structure[G.W.UAI] in G.gListUAIEtabs:
            try:
                item = {G.W.ETAB_NAME:structure[G.W.ETAB_NAME],
                        G.W.UAI:structure[G.W.UAI]}
                etabs[structure[G.W.UAI]] = item
            except KeyError, error:
                G.error("Informations insuffisantes pour un établissement : "
                        + structure + " " + error[0]['desc'])

    return etabs

def get_etab_users_from_req(req_url):
    r = requests.get(req_url,
                     auth=(G.W.USER, G.W.PWD),
                     proxies=G.W.PROXIES)
    return json.loads(r.text)


# Traitement de chaque des utilisateurs de chaque etablissements de la table "tabListUAIEtabs"
# (provenant du fichier listeEtablissements)
def listAllEtabUsers(etabs, groupList):
    etabTraiteCount = 0
    all_users = []
    id_users = {}
    id_groups = {}
    urlPrefix = G.W.URL + "/zimbra/export/users?uai="
    G.info("Requete webservice utilisateurs : " + urlPrefix + "<UAI>")

    for etabUAI in G.gListUAIEtabs[:]:
        etabTraiteCount += 1

        # Récupération des informations sur les utilisateurs de cet établissement
        reqUrlStructure = urlPrefix + etabUAI
        G.debug("Requete recherche comptes users : " + reqUrlStructure)
        allUsersData = []
        try:
            allUsersData = get_etab_users_from_req(reqUrlStructure)
        except ValueError:
            try:
                time.sleep(30)
                allUsersData = get_etab_users_from_req(reqUrlStructure)
            except ValueError:
                try:
                    time.sleep(60)
                    allUsersData = get_etab_users_from_req(reqUrlStructure)
                except ValueError:
                    G.fatal("cant fetch structure " + etabUAI)


        for user in allUsersData:
            finalUser = {}
            userUAI = user[G.W.UAI]
            userID = user[G.W.ID]
            # Verifie si l'utilisateur externalId existe deja dans la liste
            # si oui, alors ajouter juste sa structure supplémentaire
            # si non ajouter le user integralement
            # On remplace l'ID de la structure par le NOM depuis tablistAllEtab
            if user[G.W.ID] in id_users:
                finalUser = id_users[userID]
                G.debug2(
                    "Utilisateur multi etablissement : "
                    + userID + ", egalement sur la structure "
                    + user[G.W.ETAB]
                )
            else :
                # Enregistrement des informations de base de l'utilisateur
                finalUser[G.W.LOGIN] = user[G.W.LOGIN].encode("utf-8")
                finalUser[G.W.ZIMBRALOGIN] = user[G.W.LOGIN].encode("utf-8")
                finalUser[G.W.ID] = userID
                finalUser[G.W.NOM] = user[G.W.NOM].encode("utf-8")
                finalUser[G.W.PRENOM] = user[G.W.PRENOM].encode("utf-8")
                finalUser[G.W.CN] = user[G.W.CN].encode("utf-8").replace('"', '')
                finalUser[G.W.PROFILE] = user[G.W.PROFILE].encode("utf-8")
                finalUser[G.W.GROUP] = []
                finalUser[G.W.STATUS] = G.Z.STATUS_ACTIVE
                if G.W.STATUS in user:
                    if user[G.W.STATUS] == "true":
                        finalUser[G.W.STATUS] = G.Z.STATUS_LOCKED


            # ajouter la structure dans l array de la liste all_users
            # pour l user concerné user['UAI']
            currentStructureName = (
                    etabs[userUAI][G.W.ETAB_NAME].encode("utf-8") + " " + user[G.W.UAI].encode("utf-8")
            )
            if G.W.ETAB in finalUser.keys():
                finalUser[G.W.ETAB] = finalUser[G.W.ETAB] + ' - ' + currentStructureName
            else:
                finalUser[G.W.ETAB] = currentStructureName

            # Récupérer les groupes
            groups = user[G.W.GROUP]
            finalGroups = finalUser[G.W.GROUP]
            for group in groups:
                groupId = group["groupId"]
                if groupId not in finalGroups:
                    finalGroups.append(groupId)
                if not id_groups.has_key(groupId):
                    groupList.append(group)
                    id_groups[groupId] = group
            finalUser[G.W.GROUP] = finalGroups

            # On ajoute l externalId du user dans les users passés
            # (pour gestion des multi étab et éviter le doublon ajout du user)
            id_users[userID] = finalUser
            all_users.append(finalUser)


    G.info("Nombre d etablissements traites : " + str(etabTraiteCount))
    return all_users


# Renvoi une liste en fonction de nos critères de recherche
def ldapSearchToList(_ldapCon, _baseDn, _attrsNone, _filter):
    G.info("Recherche des utilisateurs dans Zimbra : " + _baseDn)
    G.debug("Recherche utilisateurs via LDAP : "
            + str(_ldapCon) + " " + _baseDn + " " + str(_attrsNone) + " " + _filter)

    searchList = []
    try:
        searchList = _ldapCon.search_s(_baseDn, G.Z.LDAP_SCOPE, _filter, _attrsNone)
    except ldap.LDAPError, error:
        G.fatal(	"Erreur sur la recherche de comptes LDAP : " + _baseDn + " " + error[0]['desc'])

    listResult = []

    for dn, entry in searchList:
        listResult.append(entry)

    return listResult

#Met le contenu de la liste dans un fichier dbm avec comme clé key
def saveZimbraUsersToDbm(dbmFile, listToSave, sortKey, secondKey):
    dbFlux = shelve.open(dbmFile, 'n')
    for element in listToSave:
        if sortKey in element:
            elem_keys = element[sortKey]
            if len(elem_keys) > 1:
                G.error("Un compte a plusieurs alias : " + str(element))
            for key in elem_keys:
                final_key = key.split("@")[0]
                dbFlux[final_key] = element
        else:
            # Ajout des users sans alias par leur login
            key = element[secondKey]
            final_key = key[0].split("@")[0]
            dbFlux[final_key] = element
            G.error("Un compte sans alias est présent dans Zimbra : "+ str(element))
    dbFlux.close()

#Met le contenu de la liste dans un fichier dbm avec comme clé key
def saveZimbraDataToDbm(dbmFile, listToSave, sortKey):
    dbFlux = shelve.open(dbmFile, 'n')
    for element in listToSave:
        dbFlux[element[sortKey][0]] = element
    dbFlux.close()

# Met le contenu de la liste dans un fichier dbm avec comme clé key
def saveWsDataToDbm(_dbmFile, _list, _key):
    dbFlux = shelve.open(_dbmFile, 'n')
    for element in _list:
        if element[_key] is not None:
            dbFlux[element[_key].encode("utf-8")] = element
    dbFlux.close()
	
	
	
