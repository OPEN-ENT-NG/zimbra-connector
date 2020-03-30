﻿/*
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

import {
    idiom as lang,
    model,
    Model,
    notify,
    Collection,
    _,
    moment
} from "entcore";

import {
    Folder,
    UserFolder,
    UserFolders,
    SystemFolder,
    SystemFolders
} from "./folder";
import { User, Users } from "./user";
import { quota } from "./quota";

import { Eventer } from "entcore-toolkit";

import http from "axios";

export class Zimbra {
    folders: SystemFolders;
    userFolders: UserFolders;
    users: Users;
    systemFolders: string[];
    currentFolder: Folder;
    maxFolderDepth: number;
    eventer = new Eventer();
    preference = { useSignature: false, signature: "" };

    static _instance: Zimbra;
    static get instance(): Zimbra {
        if (!this._instance) {
            this._instance = new Zimbra();
        }
        return this._instance;
    }

    constructor() {
        this.users = new Users();
        this.folders = new SystemFolders();
        this.userFolders = new UserFolders();

        this.folders.inbox.countUnread();
    }

    async sync() {
        let response = await http.get("max-depth");
        this.maxFolderDepth = parseInt(response.data["max-depth"]);
        this.eventer.trigger("change");
        await this.getPreference();
        await this.userFolders.sync();
        await quota.refresh();
    }

    async getPreference() {
        try {
            let response = await http.get("/zimbra/signature");
            if (response.data.preference)
                this.preference = JSON.parse(response.data.preference);
        } catch (e) {
            notify.error(e.response.data.error);
        }
    }

    async putPreference() {
        await http.put("/zimbra/signature", this.preference);
    }
}
