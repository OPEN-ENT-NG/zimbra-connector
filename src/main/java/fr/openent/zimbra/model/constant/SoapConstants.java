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

public class SoapConstants {

    public static final String SEPARATOR = ",";

    public static final String REQ_NAME = "name";
    public static final String REQ_CONTENT = "content";
    public static final String REQ_NAMESPACE = "_jsns";
    public static final String BODY = "Body";
    public static final String ID_BY = "by";
    public static final String ATTR_NAME = "n";
    public static final String ATTR_VALUE = "_content";
    public static final String ATTR_LIST = "a";
    public static final String ACCOUNT = "account";
    public static final String ACTION = "action";
    public static final String RECURSIVE = "recursive";
    public static final Integer ZERO_FALSE = 0;
    public static final Integer ONE_TRUE = 1;
    public static final String EMPTY_VALUE = "";
    public static final String TRUE_VALUE = "TRUE";
    public static final String FALSE_VALUE = "FALSE";
    public static final String UUID = "uuid";
    public static final String ZIMBRA_ID = "id";
    public static final String ZIMBRA_ZID = "zid";

    public static final String OP_TRASH = "trash";
    public static final String OP_RENAME = "rename";
    public static final String OP_MOVE = "move";
    public static final String OP_DELETE = "delete";
    public static final String OP_EMPTY = "empty";
    public static final String OP_READ = "read";
    public static final String OP_UNREAD = "!read";
    public static final String OP_GRANT = "grant";
    public static final String OP_REVOKE_GRANT = "!grant";
    public static final String OPERATION = "op";

    public static final String GRANT_READ = "r";
    public static final String GRANT_WRITE = "w";

    public static final String NAMESPACE_ZIMBRA = "urn:zimbra";
    public static final String NAMESPACE_ADMIN = "urn:zimbraAdmin";
    public static final String NAMESPACE_ACCOUNT = "urn:zimbraAccount";
    public static final String NAMESPACE_MAIL = "urn:zimbraMail";

    // Admin Requests
    public static final String CREATE_ACCOUNT_REQUEST = "CreateAccountRequest";
    public static final String CREATE_ACCOUNT_RESPONSE = "CreateAccountResponse";
    public static final String ADD_ALIAS_REQUEST = "AddAccountAliasRequest";
    public static final String GET_ACCOUNT_REQUEST = "GetAccountRequest";
    public static final String GET_ACCOUNT_RESPONSE = "GetAccountResponse";
    public static final String MODIFY_ACCOUNT_REQUEST = "ModifyAccountRequest";
    public static final String GET_DISTRIBUTIONLIST_REQUEST = "GetDistributionListRequest";
    public static final String GET_DISTRIBUTIONLIST_RESPONSE = "GetDistributionListResponse";
    public static final String CREATE_DISTRIBUTIONLIST_REQUEST = "CreateDistributionListRequest";
    public static final String MODIFY_DISTRIBUTIONLIST_REQUEST = "ModifyDistributionListRequest";

    // Accounts Requests
    public static final String GET_ACCOUNT_INFO_REQUEST = "GetAccountInfoRequest";
    public static final String GET_ACCOUNT_INFO_RESPONSE = "GetAccountInfoResponse";
    public static final String GET_FOLDER_REQUEST = "GetFolderRequest";
    public static final String GET_FOLDER_RESPONSE = "GetFolderResponse";
    public static final String CREATE_FOLDER_REQUEST = "CreateFolderRequest";
    public static final String CREATE_FOLDER_RESPONSE = "CreateFolderResponse";
    public static final String CREATE_MOUNTPOINT_REQUEST = "CreateMountpointRequest";
    public static final String CREATE_MOUNTPOINT_RESPONSE = "CreateMountpointResponse";
    public static final String FOLDER_ACTION_REQUEST = "FolderActionRequest";
    public static final String FOLDER_ACTION_RESPONSE = "FolderActionResponse";
    public static final String IMPORT_CONTACTS_REQUEST = "ImportContactsRequest";
    public static final String IMPORT_CONTACTS_RESPONSE = "ImportContactsResponse";
    public static final String GET_CONVERSATION_REQUEST = "GetConvRequest";
    public static final String GET_CONVERSATION_RESPONSE = "GetConvResponse";
    public static final String SEARCH_REQUEST = "SearchRequest";
    public static final String SEARCH_RESPONSE = "SearchResponse";
    public static final String SEARCH_CONV_REQUEST = "SearchConvRequest";
    public static final String SEARCH_CONV_RESPONSE = "SearchConvResponse";
    public static final String CONVERSATION_ACTION_REQUEST = "ConvActionRequest";
    public static final String GET_MESSAGE_REQUEST = "GetMsgRequest";
    public static final String GET_MESSAGE_RESPONSE = "GetMsgResponse";
}
