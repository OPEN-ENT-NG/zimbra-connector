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

package fr.openent.zimbra.model.constant;

public class ZimbraErrors {
    // Error codes can be found in /opt/zimbra/docs/soap.txt
    public static final String ERROR_GENERIC = "service.FAILURE";
    public static final String ERROR_BADREQUEST = "service.INVALID_REQUEST";
    public static final String ERROR_UNKNOWNREQ = "service.UNKNOWN_DOCUMENT";
    public static final String ERROR_PARSEERROR = "service.PARSE_ERROR";
    public static final String ERROR_PERMDENIED = "service.PERM_DENIED";
    public static final String ERROR_AUTHREQUIRED = "service.AUTH_REQUIRED";
    public static final String ERROR_AUTHEXPIRED = "service.AUTH_EXPIRED";
    public static final String ERROR_WRONGHOST = "service.WRONG_HOST";
    public static final String ERROR_PROXYERROR = "service.PROXY_ERROR";
    public static final String ERROR_TOOMANYHOPS = "service.TOO_MANY_HOPS";
    public static final String ERROR_INTERRUPTED = "service.INTERRUPTED";
    public static final String ERROR_NOTINPROGRESS = "service.NOT_IN_PROGRESS";
    public static final String ERROR_ALREADYINPROGRESS = "service.ALREADY_IN_PROGRESS";
    public static final String ERROR_NOSPELLCHECKURL = "service.NO_SPELL_CHECK_URL";
    public static final String ERROR_UNREACHABLE = "service.RESOURCE_UNREACHABLE";
    public static final String ERROR_TEMP_UNVAVAILABLE = "service.TEMPORARILY_UNAVAILABLE";
    public static final String ERROR_NONREADONLY_OPDENIED = "service.NON_READONLY_OPERATION_DENIED";

    public static final String ERROR_AUTHFAILED = "account.AUTH_FAILED";
    public static final String ERROR_CHANGEPWD = "account.CHANGE_PASSWORD";
    public static final String ERROR_PWDLOCK = "account.PASSWORD_LOCKED";
    public static final String ERROR_PWDTEMPLOCK = "account.PASSWORD_CHANGE_TOO_SOON";
    public static final String ERROR_PWDRECENTUSED = "account.PASSWORD_RECENTLY_USED";
    public static final String ERROR_INVALIDPWD = "account.INVALID_PASSWORD";
    public static final String ERROR_INVALIDATTRNAME = "account.INVALID_ATTR_NAME";
    public static final String ERROR_INVALIDATTRVALUE = "account.INVALID_ATTR_VALUE";
    public static final String ERROR_MULTIPLEACCOUNTMATCHED = "account.MULTIPLE_ACCOUNTS_MATCHED";
    public static final String ERROR_NOSUCHACCOUNT = "account.NO_SUCH_ACCOUNT";
    public static final String ERROR_NOSUCHALIAS = "account.NO_SUCH_ALIAS";
    public static final String ERROR_NOSUCHDOMAIN = "account.NO_SUCH_DOMAIN";
    public static final String ERROR_NOSUCHCOS = "account.NO_SUCH_COS";
    public static final String ERROR_NOSUCHID = "account.NO_SUCH_IDENTITY";
    public static final String ERROR_NOSUCHSIGNATURE = "account.NO_SUCH_SIGNATURE";
    public static final String ERROR_NOSUCHDATASRC = "account.NO_SUCH_DATA_SOURCE";
    public static final String ERROR_NOSUCHSERVER = "account.NO_SUCH_SERVER";
    public static final String ERROR_NOSUCHZIMLET = "account.NO_SUCH_ZIMLET";
    public static final String ERROR_NOSUCHDLIST = "account.NO_SUCH_DISTRIBUTION_LIST";
    public static final String ERROR_NOSUCHCALENDARRSC = "account.NO_SUCH_CALENDAR_RESOURCE";
    public static final String ERROR_NOSUCHMEMBER = "account.NO_SUCH_MEMBER";
    public static final String ERROR_MEMBEREXISTS = "account.MEMBER_EXISTS";
    public static final String ERROR_ACCOUNTEXISTS = "account.ACCOUNT_EXISTS";
    public static final String ERROR_DOMAINEXISTS = "account.DOMAIN_EXISTS";
    public static final String ERROR_COSEXISTS = "account.COS_EXISTS";
    public static final String ERROR_SERVEREXISTS = "account.SERVER_EXISTS";
    public static final String ERROR_DLISTEXISTS = "account.DISTRIBUTION_LIST_EXISTS";
    public static final String ERROR_IDEXISTS = "account.IDENTITY_EXISTS";
    public static final String ERROR_SIGNATUREEXISTS = "account.SIGNATURE_EXISTS";
    public static final String ERROR_DATASRCEXISTS = "account.DATA_SOURCE_EXISTS";
    public static final String ERROR_DOMAINNOTEMPTY = "account.DOMAIN_NOT_EMPTY";
    public static final String ERROR_MAINTENANCEMODE = "account.MAINTENANCE_MODE";
    public static final String ERROR_ACCOUNTINACTIVE = "account.ACCOUNT_INACTIVE";
    public static final String ERROR_TOOMANYACCOUNT = "account.TOO_MANY_ACCOUNTS";
    public static final String ERROR_TOOMANYIDS = "account.TOO_MANY_IDENTITIES";
    public static final String ERROR_TOOMANYSIGNATURE = "account.TOO_MANY_SIGNATURES";
    public static final String ERROR_TOOMANYDATASRC = "account.TOO_MANY_DATA_SOURCES";
    public static final String ERROR_TOOMANYSRCHRESULTS = "account.TOO_MANY_SEARCH_RESULTS";
    public static final String ERROR_2FAAUTHFAILED = "account.TWO_FACTOR_AUTH_FAILED";
    public static final String ERROR_2FASETUPFAILED = "account.TWO_FACTOR_SETUP_REQUIRED";

    public static final String ERROR_MAINTENANCE = "mail.MAINTENANCE";
    public static final String ERROR_NOSUCHMBOX = "mail.NO_SUCH_MBOX";
    public static final String ERROR_NOSUCHITEM = "mail.NO_SUCH_ITEM";
    public static final String ERROR_NOSUCHCONV = "mail.NO_SUCH_CONV";
    public static final String ERROR_NOSUCHMSG = "mail.NO_SUCH_MSG";
    public static final String ERROR_NOSUCHPART = "mail.NO_SUCH_PART";
    public static final String ERROR_NOSUCHFOLDER = "mail.NO_SUCH_FOLDER";
    public static final String ERROR_NOSUCHTAG = "mail.NO_SUCH_TAG";
    public static final String ERROR_NOSUCHCONTACT = "mail.NO_SUCH_CONTACT";
    public static final String ERROR_NOSUCHCALITEM = "mail.NO_SUCH_CALITEM";
    public static final String ERROR_NOSUCHAPPT = "mail.NO_SUCH_APPT";
    public static final String ERROR_NOSUCHTASK = "mail.NO_SUCH_TASK";
    public static final String ERROR_NOSUCHDOC = "mail.NO_SUCH_DOC";
    public static final String ERROR_NOSUCHUPLOAD = "mail.NO_SUCH_UPLOAD";
    public static final String ERROR_NOSUCHWAITSET = "mail.NO_SUCH_WAITSET";
    public static final String ERROR_QUERYPARSE = "mail.QUERY_PARSE_ERROR";
    public static final String ERROR_MODIFYCONFLICT = "mail.MODIFY_CONFLICT";
    public static final String ERROR_ALREADYEXISTS = "mail.ALREADY_EXISTS";
    public static final String ERROR_INVALID_ID = "mail.INVALID_ID";
    public static final String ERROR_INVALIDSYNCTOKEN = "mail.INVALID_SYNC_TOKEN";
    public static final String ERROR_INVALIDNAME = "mail.INVALID_NAME";
    public static final String ERROR__NVALIDTYPE = "mail.INVALID_TYPE";
    public static final String ERROR_INVALIDCONTENTTYPE = "mail.INVALID_CONTENT_TYPE";
    public static final String ERROR_ISNOTCHILD = "mail.IS_NOT_CHILD";
    public static final String ERROR_CANTCONTAIN = "mail.CANNOT_CONTAIN";
    public static final String ERROR_CANTCOPY = "mail.CANNOT_COPY";
    public static final String ERROR_CANTTAG = "mail.CANNT_TAG";
    public static final String ERROR_CANTPARENT = "mail.CANNOT_PARENT";
    public static final String ERROR_CANTRENAME = "mail.CANNOT_RENAME";
    public static final String ERROR_CANTSUSCRIBE = "mail.CANNOT_SUBSCRIBE";
    public static final String ERROR_IMMUTABLEOBJ = "mail.IMMUTABLE_OBJECT";
    public static final String ERROR_WRONGMAILBOX = "mail.WRONG_MAILBOX";
    public static final String ERROR_TRY_AGAIN = "mail.TRY_AGAIN";
    public static final String ERROR_SCANERROR = "mail.SCAN_ERROR";
    public static final String ERROR_UPLOADREJECTED = "mail.UPLOAD_REJECTED";
    public static final String ERROR_TOOMANYTAGS = "mail.TOO_MANY_TAGS";
    public static final String ERROR_TOOMANYUPLOAD = "mail.TOO_MANY_UPLOADS";
    public static final String ERROR_TOOMANYCONTACT = "mail.TOO_MANY_CONTACTS";
    public static final String ERROR_UNABLETOIMPORT_CONTACTS = "mail.UNABLE_TO_IMPORT_CONTACTS";
    public static final String ERROR_UNABLETOIMPORT_APPOINTMENTS = "mail.UNABLE_TO_IMPORT_APPOINTMENTS";
    public static final String ERROR_QUOTA_EXCEEDED = "mail.QUOTA_EXCEEDED";
    public static final String ERROR_MSGPARSERROR = "mail.MESSAGE_PARSE_ERROR";
    public static final String ERROR_ADDRPARSERROR = "mail.ADDRESS_PARSE_ERROR";
    public static final String ERROR_ICALPARSERROR = "mail.ICALENDAR_PARSE_ERROR";
    public static final String ERROR_MUSTBEORGANIZER = "mail.MUST_BE_ORGANIZER";
    public static final String ERROR_CANNOTCANCELINSTANCEOFEXCEPTION = "mail.CANNOT_CANCEL_INSTANCE_OF_EXCEPTION";
    public static final String ERROR_INVITE_OUTOFDATE = "mail.INVITE_OUT_OF_DATE";
    public static final String ERROR_SENDABORTED_ADDRFAILURE = "mail.SEND_ABORTED_ADDRESS_FAILURE";
    public static final String ERROR_SENDPARTIAL_ADDRFAILURE = "mail.SEND_PARTIAL_ADDRESS_FAILURE";
    public static final String ERROR_SENDFAILYRE = "mail.SEND_FAILURE";
    public static final String ERROR_TOOMANYQUERYTERMSEXPANDED = "mail.TOO_MANY_QUERY_TERMS_EXPANDED";
    public static final String ERROR_INVALIDCOMMITID = "mail.INVALID_COMMIT_ID";
    public static final String ERROR_GRANT_EXISTS = "mail.GRANT_EXISTS";

}
