** Prérequis **
Le(s) store(s) du domaine doivent être installés
Installer les dépendances pour python : 
sudo yum install python2-pip gcc python-devel openldap-devel python2-requests
sudo pip install --upgrade pip
sudo pip install python-ldap


1 - Créer deux dossiers : data et work


drwxr-x---. 2 zimbra zimbra 4096 20 avril 10:31 data
drwxr-x---. 2 zimbra zimbra 6 20 avril 10:31 work

2 - Créer un fichier liste établissement 

[zimbra]$ touch listeEtablissements.txt

Chaque ligne porte un UAI d'un établissement à synchroniser.

3 - Créer un fichier synchroZimbra.conf, en partant du fichier synchroZimbra.conf.tpl

[zimbra]$ cp synchroZimbra.conf.tpl synchroZimbra.conf


8 - Modifier les valeurs dans le fichier synchroZimbra.conf:

#!/bin/sed -f
s|@@LISTETAB@@|/listeEtablissements.txt|
s|@@LOGFILEPREFIX@@|/data/synchroZimbra|
s/@@WSURL@@/ent.iledefrance.fr/
s/@@PROTO@@/https/
s/@@USERNG@@/API_ZIMBRA/
s/@@PWDNG@@/xxxxxxxxxxxxxxxxxxxxxxxxx/
s/@@HTTPPROXY@@/""/
s/@@HTTPSPROXY@@/""/
s/@@ZIMBRADOMAIN@@/monlycee.net/
s/@@ZIMBRAIP@@/ldap01.prod.zimbra.lan/
s/@@ZIMBRAPORT@@/636/
s/@@PWDZIMBRA@@/xxxxxxxxxxxxxxxxxxxxxxxxxx/
s|@@ZTMPF@@|/work/zimbraTempFile|
s|@@ZGTMPF@@|/work/zimbraGroupTempFile|
s|@@WTMPF@@|/work/webServiceTempFile|
s|@@WSGTMPF@@|/work/webServiceGroupTempFile|
s|@@MATMPF@@|/work/modifiedAccountToZimbraTempFile|
s|@@MGTMPF@@|/work/modifiedGroupToZimbraTempFile|
s|@@NEWACCF@@|/data/newAccountToZimbra.txt|
s|@@MTS@@|/data/newMailToSend.txt|
s|@@MODACCF@@|/data/modifiedAccountToZimbra.txt|
s|@@MODGRPF@@|/data/modifiedGroupsToZimbra.txt|
s|@@DELACCF@@|/data/deletedAccount.txt|
s|@@EXEF@@|/data/execAccountsZimbra.txt|
s|@@MODPRIOF@@|/data/prioExecAccountsZimbra.txt|
s|@@NEWGRPF@@|/data/groupsZimbra.txt|

 
 
