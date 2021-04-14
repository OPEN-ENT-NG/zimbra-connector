






Circuit Breaker, protect eventloop if zimbra doesn't respond
-
- maxFailures : max bad request before open circuit
- timeout : max timeout request befoe open request (-1 disable)


Users Sync
-


Address Book Sync
-
Address Book of user is sync when user log on zimbra expert mode

Addess Book of a structure is sync when a user of this structure lo on zimbra expert mode

- enable-addressbook-synchro : is addressbook sync to zimbra enabled ?  (default true) 
- shared-folder-name : name of created addess book root folder (defaut -- Carnets Adresses ENT --)
- abook-sync-ttl-minutes : delay in minutes between two sync of AdressBook of a user (default 1440)
- structure-abook-sync-delay : delay (with postgres syntax) between two sync of adressBook of a structure (default '1 day')
- force-synchro-adressbook : if true, even if user should respect rights&grants rules, all adressbook of structure is shared with users (default false)
- purge-emailed-contacts :  
