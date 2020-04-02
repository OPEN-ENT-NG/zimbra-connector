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

import {
    idiom as lang,
    model,
    Model,
    notify,
    Collection,
    _,
    moment, widgets
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

import http from './http';

declare const window: any;

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
        this.compute();
    }

    compute() {
        window.folderMap = new Map();
        this.users = new Users();
        this.folders = new SystemFolders();
        this.userFolders = new UserFolders(this.folders.inbox.folders);
        this.userFolders.all.map(userFolder => userFolder.parentPath = this.folders.inbox.path);
        window.folderMap.set(this.folders.inbox.path, this.folders.inbox);
    }

    async sync() {
        // let response = await http.get("max-depth");
        // this.maxFolderDepth = parseInt(response.data["max-depth"]);
        this.eventer.trigger("change");
        await this.getPreference();
        await quota.initialValues();
    }

    async getPreference() {
        try {
            if ('signature' in window.user) {
                const { prefered, content } = window.user.signature;
                this.preference = {
                    useSignature: prefered,
                    signature: content
                };
            }
        } catch (e) {
            notify.error(e.response.data.error);
        }
    }

    async putPreference() {
        await http.put("/zimbra/signature", this.preference);
    }

    async computeRootFolder() {
        const {data} = await http.get('/zimbra/root-folder');
        const newValues = [];

        const compute = function (newFolder) {
            newValues.push(newFolder.path);
            if (window.folderMap.has(newFolder.path)) {
                const folder: Folder = window.folderMap.get(newFolder.path);
                folder.count = newFolder.count;
                folder.nbUnread = newFolder.unread;
                newFolder.folders.forEach(compute);
            } else {
                const parentPath = newFolder.path.replace(`/${newFolder.folderName}`, '');
                const parentFolder = window.folderMap.get(parentPath);
                if (parentFolder) {
                    const f = new UserFolder(new UserFolder({get: `/zimbra/list?folder=${newFolder.path}`}, newFolder));
                    parentFolder.userFolders.all.push(f);
                    f.name = newFolder.folderName;
                    f.id = newFolder.id;
                    f.path = newFolder.path;
                    f.nbUnread = newFolder.unread;
                    f.count = newFolder.count;
                    f.parentPath = parentPath;
                    window.folderMap.set(newFolder.path, f);
                }
            }
        };

        data.forEach(compute);
        window.folderMap.forEach((value, key) => {
           if (newValues.indexOf(key) === -1) window.folderMap.delete(key)
        });
    }
}
