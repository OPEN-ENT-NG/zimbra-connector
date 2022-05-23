# About Zimbra Connetor
* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright CGI
* Développer(s) : CGI
* Financer(s) : Région Ile de France, CGI
* Description : Module to connect to Zimbra.

## Circuit Breaker, protect eventloop if zimbra doesn't respond
* maxFailures : max bad request before open circuit
* timeout : max timeout request befoe open request (-1 disable)

## Address Book Sync
Address Book of user is sync when user log on zimbra expert mode
Addess Book of a structure is sync when a user of this structure lo on zimbra expert mode

* enable-addressbook-synchro : is addressbook sync to zimbra enabled ? (default true)
* shared-folder-name : name of created addess book root folder (defaut -- Carnets Adresses ENT --)
* abook-sync-ttl-minutes : delay in minutes between two sync of AdressBook of a user (default 1440)
* structure-abook-sync-delay : delay (with postgres syntax) between two sync of adressBook of a structure (default '1 day')
* force-synchro-adressbook : if true, even if user should respect rights&grants rules, all adressbook of structure is shared with users (default false)
* purge-emailed-contacts :

## Configuration 
The zimbra-connector module contains several modules within it: apizimbra and zimbra.

### Apizimbra
<pre>
{
  "config": {
    ...
    "zimbra-uri" : "$zimbraUri",
    "zimbra-domain" : "$zimbraDomain",
    "zimbra-synchro-lang" : "$zimbraLang",
    ...
  }
}
</pre>

Specific configuration that must be seen :

<pre>
zimbraUri = ${String}
zimbraDomain = ${String}
zimbraLang = ${String}
</pre>

### zimbra

<pre>
{
  "config": {
    ...
    "max-recipients": $zimbraMaxRecipients,
    ...
    "zimbra-uri" : "$zimbraUri",
    "preauth-key" : "$zimbraPreauthKey",
    "zimbra-domain" : "$zimbraDomain",
    "zimbra-synchro-lang" : "$zimbraLang",
    "zimbra-admin-uri" : "$zimbraAdminUri",
    "admin-account" : "$zimbraAdminAccount",
    "address-book-account" : "$zimbraAddressBookAccount",
    "admin-password" : "$zimbraAdminPassword",
    "zimbra-synchro-cron" : "$zimbraSynchroCron",
    "mail-config" : {
      "imaps":{
        ...
      },
      "smtps":{
        ...
      }
    },
    ...
    "purge-emailed-contacts" : $zimbraPurgeEmailedContacts,
    "force-synchro-adressbook" : $zimbraForceSyncAdressBook,
    "save-draft-auto-time" : $saveDraftAutoTime,
    "filter-profile-sync-ab" : $filterProfileSyncAB,
    ...
    "slack": {
      "api-uri": "$zimbraSlackApiUri",
      "api-token": "$zimbraSlackApiToken",
      "channel": "$zimbraSlackChannel",
      "bot-username": "$zimbraSlackBotUsername"
    }
  }
}

</pre>

Specific configuration that must be seen :

<pre>
zimbraMaxRecipients = Integer
zimbraUri = ${String}
zimbraPreauthKey = ${String}
zimbraDomain = ${String}
zimbraLang = ${String}
zimbraAdminUri = ${String}
zimbraAdminAccount = ${String}
zimbraAddressBookAccount = ${String}
zimbraAdminPassword = ${String}
zimbraSynchroCron = ${String}
zimbraPurgeEmailedContacts = boolean
zimbraForceSyncAdressBook = boolean
saveDraftAutoTime = ${String}
filterProfileSyncAB = ${String}
zimbraSlackApiUri = ${String}
zimbraSlackApiToken = ${String}
zimbraSlackChannel = ${String}
zimbraSlackBotUsername = ${String}
</pre>

you can add the following properties depending on your use of zimbra : 

<pre>
{
  ...
  circuit-breaker: { },
  ...
  cwd: ${String},
  assets-path: ${String}
}
</pre>