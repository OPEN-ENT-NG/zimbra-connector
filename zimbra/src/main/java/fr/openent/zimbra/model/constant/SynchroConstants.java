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

public class SynchroConstants {

    public static final String FIRSTNAME = "givenName";
    public static final String LASTNAME = "sn";
    public static final String DISPLAYNAME = "cn";
    public static final String LOGIN = "labeledURI";
    public static final String GROUPID = "ou";
    public static final String ADDGROUPID = "+ou";
    public static final String ACCOUNT_STATUS = "zimbraAccountStatus";
    public static final String ALIAS = "zimbraMailAlias";
    public static final String HIDEINSEARCH = "zimbraHideInGal";
    public static final String PROFILE = "title";
    public static final String DATE_MODIFICATION = "telexNumber";
    public static final String STRUCTURES = "company";

    public static final String ACCT_STATUS_LOCKED = "locked";
    public static final String ACCT_STATUS_ACTIVE = "active";

    public static final String SYNC_DAILY = "DAILY";
    public static final String SYNC_APP = "APP";

    public static final String ACTION_CREATION = "CREATE";
    public static final String ACTION_MODIFICATION = "MODIFY";
    public static final String ACTION_DELETION = "DELETE";

    public static final String STATUS_TODO = "TODO";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_INPROGRESS = "IN_PROGRESS";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_MAILLING_DONE = "MAILLING_DONE";
    public static final String STATUS_ERROR = "ERROR";


    public static final String ABOOK_ROOT_FOLDER = "/Contacts";
    public static final String ABOOK_PERSONEL = "Personel";
    public static final String ABOOK_TEACHER = "Teacher";
    public static final String ABOOK_STUDENT = "Student";
    public static final String ABOOK_RELATIVE = "Relative";
    public static final String ABOOK_GUEST = "Guest";

    public static final String ABOOK_CSV_COLUMNS = "\"company\",\"department\",\"email\",\"firstName\",\"fullName\",\"jobTitle\",\"lastName\"\n";
}
