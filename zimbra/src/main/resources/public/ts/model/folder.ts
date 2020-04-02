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

import {notify, toFormData, _} from "entcore";
import { Zimbra } from "./zimbra";
import { Mail, Mails } from "./mail";
import { quota } from "./quota";

import { Mix, Eventer, Selection, Selectable } from "entcore-toolkit";

import http from './http';

declare const window: any;

export abstract class Folder implements Selectable {
    pageNumber: number;
    mails: Mails;
    nbUnread: number;
    api: { get: string; post: string; put: string; delete: string };
    eventer = new Eventer();
    selected: boolean;
    filter: boolean;
    reverse: boolean;
    searchText: string;
    count: number;
    path: string;
    id: string;
    folders: Array<Folder>;
    parentPath: string;


    abstract removeSelection();
    abstract sync();
    abstract selectAll();
    abstract deselectAll();

    constructor(api: {
        get: string;
        post: string;
        put: string;
        delete: string;
    }) {
        this.api = api;
        this.filter = false;
        this.reverse = true;
        this.nbUnread = 0;
    }

    getName() {
        if (this instanceof SystemFolder) {
            return this.folderName.toUpperCase();
        }
        if (this instanceof UserFolder) {
            return this.id;
        }
        return "";
    }

    async nextPage(select: boolean) {
        if (!this.mails.full) {
            this.pageNumber++;
            await this.mails.sync({
                pageNumber: this.pageNumber,
                searchText: this.searchText,
                emptyList: false,
                filterUnread: this.filter,
                selectAll: select
            });
        }
    }

    async search(text: string) {
        this.mails.full = false;
        this.pageNumber = 0;
        this.searchText = text;
        await this.mails.sync({
            pageNumber: 0,
            searchText: this.searchText,
            emptyList: true,
            filterUnread: this.filter
        });
    }

    async filterUnread(filter: boolean) {
        this.mails.full = false;
        this.filter = filter;
        this.pageNumber = 0;
        await this.mails.sync({
            pageNumber: this.pageNumber,
            searchText: this.searchText,
            emptyList: true,
            filterUnread: this.filter
        });
    }

    async toggleUnreadSelection(unread) {
        let counter = this.mails.selection.selected.filter(mail => mail.unread === !unread).length;
        await this.mails.toggleUnread(unread);
        const increment = unread ? counter : -1 * counter;
        this.nbUnread = this.nbUnread + increment;
        this.mails.selection.deselectAll();
    }
}

export abstract class SystemFolder extends Folder {
    folderName: string;

    constructor(api) {
        super(api);

        var thatFolder = this;
        this.pageNumber = 0;
        this.mails = new Mails(api);
    }
}

export class Trash extends SystemFolder {
    userFolders: Selection<UserFolder> = new Selection<UserFolder>([]);

    constructor({unread, count, folders, path}) {
        super({
            get: `/zimbra/list?folder=${path}`
        });

        this.folderName = "trash";
        this.nbUnread = unread;
        this.count = count;
        this.folders = folders;
        this.path = path;
    }

    selectAll() {
        this.mails.selection.selectAll();
        this.userFolders.selectAll();
    }

    deselectAll() {
        this.mails.selection.deselectAll();
        this.userFolders.deselectAll();
    }

    async sync() {
        await Promise.all(
            [
            await this.mails.sync({ searchText: this.searchText }),
            await this.syncUsersFolders(),
        ]);
    }

    async syncUsersFolders() {
        this.userFolders.all.splice(0, this.userFolders.all.length);
        const response = await http.get("folders/list?trash=");
        response.data.forEach(f =>
            this.userFolders.all.push(Mix.castAs(UserFolder, f))
        );
    }

    async removeSelection() {
        if (this.mails.selection.selected.length > 0) {
            await this.removeMails();
            await this.mails.removeSelection();
        }
        for (let folder of this.userFolders.selected) {
            await folder.delete();
        }
    }

    async restore() {
        await this.restoreMails();
        for (let folder of this.userFolders.selected) {
            await folder.restore();
        }
    }

    async restoreMails() {
        if (!this.mails.selection.length) {
            return;
        }
        await http.put(
            "/zimbra/restore?" +
                toFormData({
                    id: _.pluck(this.mails.selection.selected, "id")
                })
        );
        this.mails.removeSelection();
    }

    async removeMails() {
        const response = await http.delete(
            "/zimbra/delete?" +
                toFormData({
                    id: _.pluck(this.mails.selection.selected, "id")
                })
        );
        this.mails.removeSelection();
    }

    async removeAll() {
        const response = await http.delete("/zimbra/emptyTrash");
    }
}

export class Inbox extends SystemFolder {
    constructor({unread, count, folders, path}) {
        super({
            get: `/zimbra/list?folder=${path}`
        });

        this.folderName = "inbox";
        this.nbUnread = unread;
        this.count = count;
        this.folders = folders;
        this.path = path;
    }

    async sync() {
        await this.mails.sync({ searchText: this.searchText });
    }

    async removeSelection() {
        await this.mails.toTrash();
    }

    selectAll() {
        this.mails.selection.selectAll();
    }

    deselectAll() {
        this.mails.selection.deselectAll();
    }
}

export class Draft extends SystemFolder {
    totalNb: number;

    constructor({unread, count, folders, path}) {
        super({
            get: `/zimbra/list?folder=${path}`
        });

        this.folderName = "draft";
        this.nbUnread = unread;
        this.count = count;
        this.folders = folders;
        this.path = path;
        this.totalNb = count;
    }

    selectAll() {
        this.mails.selection.selectAll();
    }

    deselectAll() {
        this.mails.selection.deselectAll();
    }

    async sync() {
        await this.mails.sync({ searchText: this.searchText });
    }

    async removeSelection() {
        await this.mails.toTrash();
    }

    async saveDraft(draft: Mail): Promise<any> {
        var id = draft.id;
        await draft.saveAsDraft();
        this.mails.push(draft);
        if (id == undefined && draft.id != undefined) this.totalNb++;
    }

    async transfer(mail: Mail, newMail: Mail) {
        await this.saveDraft(newMail);
        try {
            await http.put("message/" + newMail.id + "/forward/" + mail.id);
            for (let attachment of mail.attachments) {
                newMail.attachments.push(
                    JSON.parse(JSON.stringify(attachment))
                );
            }
            quota.refresh();
        } catch (e) {
            notify.error(e.data.error);
        }
    }
}

export class Outbox extends SystemFolder {
    constructor({unread, count, folders, path}) {
        super({
            get: `/zimbra/list?folder=${path}`
        });

        this.folderName = "outbox";
        this.nbUnread = unread;
        this.count = count;
        this.folders = folders;
        this.path = path;
    }

    selectAll() {
        this.mails.selection.selectAll();
    }

    deselectAll() {
        this.mails.selection.deselectAll();
    }

    async sync() {
        await this.mails.sync({ searchText: this.searchText });
    }

    async removeSelection() {
        await this.mails.toTrash();
        await quota.refresh();
    }
}

export class Spams extends SystemFolder {
    constructor({unread, count, folders, path, id}) {
        super({
            get: `/zimbra/list?folder=${path}`
        });

        this.folderName = "spams";
        this.nbUnread = unread;
        this.count = count;
        this.folders = folders;
        this.path = path;
        this.id = id;
    }

    selectAll() {
        this.mails.selection.selectAll();
    }

    deselectAll() {
        this.mails.selection.deselectAll();
    }

    async sync() {
        await this.mails.sync({ searchText: this.searchText });
    }

    async removeSelection() {
        await this.mails.toTrash();
        await quota.refresh();
    }
}

export class UserFolder extends Folder {
    id: string;
    name: string;
    parentFolderId: string;
    parentFolder: UserFolder;
    userFolders: Selection<UserFolder> = new Selection<UserFolder>([]);

    async removeMailsFromFolder() {
        for (let mail of this.mails.selection.selected) {
            await mail.removeFromFolder();
        }
        this.mails.removeSelection();
    }

    async removeSelection() {
        await this.mails.toTrash();
        await quota.refresh();
    }

    async open() {
        this.mails.full = false;
        this.pageNumber = 0;
        this.searchText = null;
        this.filter = false;
        Zimbra.instance.currentFolder = this;
        await this.sync();
    }

    async sync() {
        await this.mails.sync({ searchText: this.searchText });
    }

    selectAll() {
        this.mails.selection.selectAll();
    }

    deselectAll() {
        this.mails.selection.deselectAll();
    }

    constructor(data?, obj?) {
        super(data);

        this.mails = new Mails(this);
        var thatFolder = this;
        this.pageNumber = 0;

        if (obj && 'folders' in obj && obj.folders.length > 0) {
            this.userFolders = new Selection<UserFolder>( new UserFolders(obj.folders).all);
        }
    }

    depth(): number {
        var depth = 1;
        var ancestor = this.parentFolder;
        while (ancestor) {
            ancestor = ancestor.parentFolder;
            depth = depth + 1;
        }
        return depth;
    }

    async create() {
        var json = !this.parentFolderId
            ? {
                  name: this.name
              }
            : {
                  name: this.name,
                  parentId: this.parentFolderId
              };

        return await http.post("folder", json);
    }

    async update() {
        var json = {
            name: this.name
        };
        return await http.put("folder/" + this.id, json);
    }

    async trash() {
        return http.put("folder/trash/" + this.id);
    }

    async restore() {
        return http.put("folder/restore/" + this.id);
    }

    async delete() {
        return http.delete("folder/" + this.id);
    }
}

export class UserFolders {
    all: UserFolder[];
    selection: Selection<UserFolder>;

    constructor(folders) {
        this.all = [];
        folders.forEach(folder => {
            const f = new UserFolder({get: `/zimbra/list?folder=${folder.path}`}, folder);
            f.name = folder.folderName;
            f.id = folder.id;
            f.path = folder.path;
            f.nbUnread = folder.unread;
            f.count = folder.count;
            f.userFolders.all.map(userFolder => userFolder.parentPath = f.path);
            window.folderMap.set(f.path, f);
            this.all.push(f);
        });
    }

    forEach(cb: (item: UserFolder, index: number) => void) {
        return this.all.forEach(cb);
    }
}

export class SystemFolders {
    list: SystemFolder[];
    sync: any;
    inbox: Inbox;
    trash: Trash;
    outbox: Outbox;
    draft: Draft;
    spams: Spams;
    systemFolders: string[];

    constructor() {
        this.list = [];
        const folders = JSON.parse(window.user.folders);
        folders.forEach(folder => {
            switch (folder.path) {
                case '/Inbox':
                    this.inbox = new Inbox(folder);
                    break;
                case '/Sent':
                    this.outbox = new Outbox(folder);
                    break;
                case '/Drafts':
                    this.draft = new Draft(folder);
                    break;
                case '/Trash':
                    this.trash = new Trash(folder);
                    break;
                case '/Junk':
                    this.spams = new Spams(folder);
                    break;
            }
        });
    }

    async openFolder(folderName) {
        Zimbra.instance.currentFolder = this[folderName];
        Zimbra.instance.currentFolder.searchText = null;
        Zimbra.instance.currentFolder.filter = false;
        Zimbra.instance.currentFolder.mails.full = false;
        await Zimbra.instance.currentFolder.sync();
        Zimbra.instance.currentFolder.pageNumber = 0;
        Zimbra.instance.currentFolder.eventer.trigger("change");
    }
}
