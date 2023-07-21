#!/usr/bin/python
# -*- coding: utf-8 -*-
# Fichier de configuration

[general]
generalFilePathRelative=True
listeEtabsUAI=@@LISTETAB@@
logFilePrefix=@@LOGFILEPREFIX@@


# Informations liés au webservice NG
[entngServeurDef]
url=@@WSURL@@
protocol=@@PROTO@@
user=@@USERNG@@
pwd=@@PWDNG@@
http_proxy=@@HTTPPROXY@@
https_proxy=@@HTTPSPROXY@@

# Informations liés au Ldap Zimbra
[zimbraServeurDef]
zimbraDomain=@@ZIMBRADOMAIN@@
ip=@@ZIMBRAIP@@
port=@@ZIMBRAPORT@@
user=uid=zimbra,cn=admins,cn=zimbra
pwd=@@PWDZIMBRA@@

# Definition des fichiers temporaire de travail mettre le chemin complet ou relatif au script entZimbraImportComptes.py
# Si c'est un chemin relatif, il faut mettre tempFilePathRelative à True, sinon si c'est un chemin complet, mettre à False
[tempFile]
tempFilePathRelative=True
# Extract comptes zimbra (et y restera que les comptes non présent dans NG)
zimbraTempFile=@@ZTMPF@@
# Extract groupes zimbra
zimbraGroupTempFile=@@ZGTMPF@@
# Extract comptes NG (et y restera que les comptes non présent dans zimbra)
webServiceTempFile=@@WTMPF@@
# Extract groupes NG (et y restera que les groupes non présent dans zimbra)
webServiceGroupTempFile=@@WSGTMPF@@
# Comptes modifiés
modifiedAccountToZimbraTempFile=@@MATMPF@@
# Groupes modifiés
modifiedGroupToZimbraTempFile=@@MGTMPF@@


# Option pour la création du fichier d'import zimbra :
# Domaine des serveurs zimbra
# Liste des serveur dans lequel des stores zimbra sont disponibles
[zmprovImport]
workFilePathRelative=True
zmprovFilePathRelative=True
# fichier listant les comptes a créer car non présents dans Zimbra
zmprovCaFile=@@NEWACCF@@
zmprovMtsFile=@@MTS@@
# fichier listant les comptes a modifier (modifiés dans NG, écrase dans Zimbra)
zmprovMaFile=@@MODACCF@@
# fichier listant les groupes a modifier (modifiés dans NG, écrase dans Zimbra)
zmprovMgFile=@@MODGRPF@@
# fichier listant les comptes non présents dans NG
zmprovNINFile=@@DELACCF@@
# fichier listant les commandes à passer (create + modify)
zmprovExecAccountsZimbra=@@EXEF@@
# fichier listant les modification prioritaires
zmprovPrioExecZimbra=@@MODPRIOF@@
# fichier listant les groupes (listes de diffusion) à créer
zmprovGroupsZimbra=@@NEWGRPF@@