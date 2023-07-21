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

# Import des modules spécifiques pour la synchro
import zimbraGlobal as G

# Nettoie le contenu de la première base en fonction de la présence de sa clé dans la seconde. PI : _dbmFile1 = extract NG & _dbmFile2 = extract Zimbra
def cleanDbmFromDbm(_dbmFileNg,
                    _dbmFileZimbra,
                    _dbmFileModifiedAccounts):
    # Tous les comptes NG (au final, sera la liste des comptes présents dans NG mais non dans Zimbra)
    dbNG = shelve.open(_dbmFileNg, 'w')
    # Tous les comptes Zimbras (au final, sera la liste des comptes présents dans zimbra mais non dans NG)
    # -> sera donc le stockage des comptes supprimés
    dbZimbra = shelve.open(_dbmFileZimbra, 'w')
    #Stockage comptes modifiés
    dbModifies = shelve.open(_dbmFileModifiedAccounts, 'c')

    G.info("Comparaison des listes utilisateurs...")
    compteInexistant = 0
    compteAvecEcartModif = 0
    compteSansEcarts = 0

    # Boucle sur tout les users récupérés via le webservice
    for user_id in dbNG.keys():
        #todo fusion des comptes

        G.debug3("Utilisateur NG :" + str(user_id))
        modified = False
        userNG = dbNG[user_id]

        # Si l'id user du webservice est présent sur liste user zimbra
        if dbZimbra.has_key(user_id):

            G.debug3("L utilisateur " + user_id + " existe dans Zimbra, comparaison des attributs")
            userZimbra = dbZimbra[user_id]

            # boucle sur chaques champs qu il est possible de modifier dans NG
            # par ex : dbNG[externalId]['functions'] avec dbFlux2[externalId]['title']
            for zim_attr_name in G.COMPARE_MAP.keys():
                ng_attr_name = G.COMPARE_MAP[zim_attr_name]
                ng_attr_value = ""
                if userNG.has_key(ng_attr_name):
                    ng_attr_value = userNG[ng_attr_name]
                zim_attr_value = ""
                if userZimbra.has_key(zim_attr_name):
                    zim_attr_value = userZimbra[zim_attr_name][0]

                G.debug3("Comparaison de l'attribut :" + str(zim_attr_name))
                G.debug3(str(zim_attr_value) + " <=> " + str(ng_attr_value))
                if ng_attr_value != zim_attr_value:
                    modified = True

                    G.debug2(
                        "L'attribut " + str(zim_attr_name) + " est different => NG : " + str(ng_attr_value)
                        + " != Zimbra :" + str(zim_attr_value)
                    )
                    # Si au moins un champ est modifé, pas besoin de vérifier les autres, toutes les
                    # informations seront mises à jour
                    break

            # Comparaison des groupes
            ng_groups = []
            zim_groups = []
            if userNG.has_key(G.W.GROUP):
                ng_groups = userNG[G.W.GROUP]
            if userZimbra.has_key(G.Z.GROUP):
                zim_groups = userZimbra[G.Z.GROUP]

            new_groups = []
            for nggroup in ng_groups:
                if nggroup is None:
                    continue
                zimgroup = nggroup.encode("utf-8")
                if not zimgroup in zim_groups:
                    modified = True
                    new_groups.append(nggroup)
                else:
                    zim_groups.remove(zimgroup)

            if len(zim_groups) > 0:
                modified = True

            # new_groups contient les groupes à créer
            # zim_groups contient les groupes à supprimer
            userNG[G.W.GROUP] = new_groups
            userNG[G.Z.GROUP] = zim_groups

            # todo vérifier l'utilité de cette ligne
            # besoin de la supprimer car les deux données ne sont pas liées...
            # OUI car le login c'est l'uid du compte zimbra à modifier !!!!
            userNG[G.W.ZIMBRALOGIN] = userZimbra[G.Z.LOGIN][0]

            # Si le compte était masqué dans la GAL (supprimé logiquement)
            # On le réactive
            if ((userZimbra.has_key(G.Z.HIDEINGAL)
                 and userZimbra[G.Z.HIDEINGAL][0] != 'FALSE')
                    or (userZimbra.has_key(G.Z.STATUS)
                        and userZimbra[G.Z.STATUS][0] == G.Z.STATUS_LOCKED)):
                G.debug("Compte réactivé : " + user_id)
                modified = True

            alias_list = userZimbra[G.Z.ID][0]
            if isinstance(alias_list, basestring):
                alias_list = [alias_list]

            if modified and len(alias_list) == 1:
                compteAvecEcartModif += 1
                dbModifies[user_id] = userNG
            else:
                compteSansEcarts += 1
                G.debug3("L'utilisateur " + user_id + " n'a pas de difference et n'a donc pas ete modifie.")

            G.debug3("Fin de la comparaison de l'utilisateur : " + user_id)
            G.debug3("Suppression du user dans le dbFlux1 car ce dernier est present dans Zimbra, ou est "
                     + "present et a ete modifie : " + str(userNG))


            for alias in alias_list:
                id_user_occ = alias.split("@")[0]
                # ne restera à la fin plus que les comptes non créé dans zimbra donc on bouclera pour création
                if dbNG.has_key(id_user_occ):
                    del dbNG[id_user_occ]
                # ne restera à la fin plus que les comptes qui ne sont plus dans NG mais qui sont dans Zimbra
                del dbZimbra[id_user_occ]

        else:  # Sinon si user du webservice n'est pas présent sur liste user zimbra
            compteInexistant += 1
            G.debug3("Compte inexistant : " + str(userNG))

    G.info("Nombre de comptes dans OpenENT NG : ")
    G.info("-> Comptes sans ecarts : " + str(compteSansEcarts))
    G.info("-> Comptes avec un attribut modifie : " + str(compteAvecEcartModif))
    G.info("-> Comptes a importer : " + str(len(dbNG)))
    G.info("Total : " + str(compteAvecEcartModif + compteSansEcarts + len(dbNG)))
    G.debug("Nombre de comptes inexistants : " + str(compteInexistant))

    G.info("Nombre de comptes dans Zimbra : ")
    G.info("-> Comptes sans ecarts : " + str(compteSansEcarts))
    G.info("-> Comptes avec un attribut modifie : " + str(compteAvecEcartModif))
    G.info("-> Comptes a supprimer : " + str(len(dbZimbra)))
    G.info("Total : " + str(compteAvecEcartModif + compteSansEcarts + len(dbZimbra)))

    # Les comptes Zimbra supprimés sont référencés par login et plus par externalId
    keyList = dbZimbra.keys()
    for k in keyList:
        user = dbZimbra[k]
        dbZimbra[user[G.Z.LOGIN][0]] = dbZimbra.pop(k)

    dbNG.close()
    dbZimbra.close()
    dbModifies.close()

def cleanGroupsDbm(_dbmFileNg,
                    _dbmFileZimbra,
                    _dbmFileModgrp):
    dbNGgroups = shelve.open(_dbmFileNg, 'w')
    dbZimbragroups = shelve.open(_dbmFileZimbra, 'r')
    #Stockage groupes modifiés
    dbGrpMod = shelve.open(_dbmFileModgrp, 'c')

    G.info("Comparaison des groupes...")

    for group_id in dbNGgroups.keys():
        G.debug3("Groupe :" + str(group_id))
        if dbZimbragroups.has_key(group_id):
            group_ng = dbNGgroups[group_id]
            group_zimbra = dbZimbragroups[group_id]
            memberUrl = G.Z.MBRURL_PREF + group_id + G.Z.MBRURL_SUFF
            if (not group_zimbra.has_key("displayName")
                or group_ng["groupName"].encode("utf-8") != group_zimbra["displayName"][0]
                or group_zimbra[G.Z.MEMBER_URL][0] != memberUrl):
                dbGrpMod[group_id] = dbNGgroups[group_id]
            del dbNGgroups[group_id]



    G.info("Nombre de groupes à créer : " + str(len(dbNGgroups)))

    dbGrpMod.close()
    dbNGgroups.close()
    dbZimbragroups.close()

