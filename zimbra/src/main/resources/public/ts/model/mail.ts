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
    model,
    notify,
    idiom as lang,
    toFormData,
    moment,
    _,
    $
} from "entcore";

import {User} from "./user";
import {Zimbra} from "./zimbra";
import {SystemFolder, UserFolder, Folder} from "./folder";

import {Mix, Eventer, Selection, Selectable} from "entcore-toolkit";

import http from './http';

import {MAIL, SERVICE, QUOTA} from "./constantes";
import {AxiosError, AxiosResponse} from "axios";
import {attachmentService} from "../services";

export class Attachment {
    file: File;
    uploadStatus: string;
    id: string;
    filename: string;
    size: number;
    contentType: string;

    constructor(file: any) {
        this.file = file;
        this.uploadStatus = "loading";
        this.filename = file.metadata ? file.metadata.filename : file.name;
        this.size = file.metadata ? file.metadata.size : file.size;
        this.id = file._id;
    }
}
export class Mail implements Selectable {
    id: string;
    date: string;
    displayNames: string[];
    from: string;
    subject: string;
    body: string;
    to: User[];
    cc: User[];
    bcc: User[];
    isReportRequired: boolean;
    unread: boolean;
    state: string;
    systemFolder: string;
    parentConversation: Mail;
    replyType: string;
    newAttachments: FileList;
    loadingAttachments: Attachment[];
    attachments: Attachment[];
    eventer = new Eventer();
    hasAttachment: boolean;
    selected: boolean;
    allowReply: boolean;
    allowReplyAll: boolean;
    returned: string;

    constructor(id?: string) {
        this.id = id;
        this.loadingAttachments = [];
        this.attachments = [];
        this.allowReply = true;
        this.allowReplyAll = true;
        this.returned = "NONE";
    }

    isUserAuthor(): boolean {
        return this.from === model.me.userId;
    }

    getSystemFolder(): string {
        return this.systemFolder;
    }

    matchSystemIcon(): string {
        const systemFolder = this.getSystemFolder();
        if (systemFolder === "INBOX") return this.getInSystemIcon();
        if (systemFolder === "OUTBOX") return this.getOutSystemIcon();
        if (systemFolder === "DRAFT") return "draft";
        return "";
    }

    getInSystemIcon(): string {
        return "mail-in";
    }

    getOutSystemIcon(): string {
        return "mail-out";
    }

    isAvatarGroup(systemFolder: string): boolean {
        if (systemFolder === "INBOX") return false;
        return this.to.length > 1 || this.isRecipientGroup();
    }

    isAvatarUnknown(systemFolder: string): boolean {
        if (systemFolder === "INBOX") {
            return !this.from
        }
        if (systemFolder === "OUTBOX" && this.to.length === 1 && !this.to[0])
            return true;
        return this.to.length === 0;
    }

    isAvatarAlone(): boolean {
        const systemFolder = this.getSystemFolder();
        if (systemFolder === "INBOX") return true;
        return this.to.length === 1 && !this.isRecipientGroup();
    }

    matchAvatar(): string {
        const systemFolder = this.getSystemFolder();
        if (this.isAvatarGroup(systemFolder))
            return "/img/illustrations/group-avatar.svg?thumbnail=100x100";
        if (this.isAvatarUnknown(systemFolder))
            return "/img/illustrations/unknown-avatar.svg?thumbnail=100x100";
        if (this.isAvatarAlone()) {
            const id: User = systemFolder === "INBOX" ? this.map(this.from) : this.map(this.to[0]);
            if (id.isAMail()) {
                return "public/img/external-avatar.png?thumbnail=100x100";
            } else {
                return "/userbook/avatar/" + id.id + "?thumbnail=100x100";
            }
        }
        return "";
    }

    isUnread(currentFolder: Folder): boolean {
        const systemFolder = this.getSystemFolder();
        return (
            this.unread &&
            (systemFolder === "INBOX" || currentFolder.getName() === "INBOX")
        );
    }

    isRecipientGroup() {
        var to = this.to[0];
        if (!to) return false;
        if (!(to instanceof User)) {
            to = this.map(to);
        }
        return to.isAGroup();
    }

    isMeInsideGroup(list) {
        if (list[0] instanceof User) {
            for (let user of list) {
                if (
                    model.me.groupsIds.indexOf(user.id) !== -1 ||
                    user.id === model.me.userId
                )
                    return true;
            }
        } else {
            if (list.indexOf(model.me.userId) !== -1) return true;

            for (let id of list) {
                if (model.me.groupsIds.indexOf(id) !== -1) return true;
            }
        }
        return false;
    }

    setMailSignature(signature: string) {
        if (!this.body) this.body = "";
        this.body =
            this.body +
            '<div><br></div><div class="signature new-signature">' +
            signature +
            "</div>";
    }

    setMailContent(
        origin: Mail,
        mailType: string,
        compile,
        sanitize,
        $scope,
        signature,
        copyReceivers?: boolean
    ): Promise<any> {
        if (origin.subject.indexOf(format[mailType].prefix) === -1) {
            this.subject =
                lang.translate(format[mailType].prefix) + " " + origin.subject;
        } else {
            this.subject = origin.subject;
        }

        if (copyReceivers) {
            this.cc = origin.cc;
            this.bcc = origin.bcc;
            this.to = origin.to;
        }

        return new Promise<void>((resolve, reject) => {
            this.body =
                '<div><br></div><div class="signature new-signature">' +
                signature +
                "</div>" +
                format[mailType].content +
                "<blockquote>" +
                origin.body +
                "</blockquote>";
            const tempElement = compile(format[mailType].content)($scope);
            setTimeout(function () {
                this.body =
                    $(document.createElement("div")).append(tempElement)[0]
                        .outerHTML +
                    "<blockquote>" +
                    this.body +
                    "</blockquote>";
                tempElement.remove();
                resolve();
            }, 0);
        });
    }

    getSubject() {
        return this.subject ? this.subject : lang.translate("nosubject");
    }

    notifDate() {
        return moment(parseInt(this.date)).calendar();
    }

    longDate() {
        return moment(parseInt(this.date)).format("dddd DD MMMM YYYY");
    }

    isToday() {
        return moment(parseInt(this.date)).isSame(
            moment().startOf("day"),
            "day"
        );
    }

    isYesterday() {
        return moment(parseInt(this.date)).isSame(
            moment().subtract(1, "day"),
            "day"
        );
    }

    isMoreThanYesterday() {
        return !this.isToday() && !this.isYesterday();
    }

    getHours() {
        return moment(parseInt(this.date)).format("HH");
    }

    getMinutes() {
        return moment(parseInt(this.date)).format("mm");
    }

    getDate() {
        return moment(parseInt(this.date)).format("dddd D MMMM YYYY");
    }

    sender() {
        var that = this;
        return User.prototype.mapUser(this.displayNames, this.from);
    }

    map(id) {
        if (id instanceof User) {
            return id;
        }
        return User.prototype.mapUser(this.displayNames, id);
    }

    async updateAllowReply() {
        const systemFolder = this.getSystemFolder();

        // Reply
        var id = systemFolder === "INBOX" ? this.from : this.to[0];
        var exists;
        if (!id)
            // completely deleted user
            exists = false;
        else exists = await this.map(id).findData();
        this.allowReply = exists;

        // Reply all
        if (exists) {
            for (let to of this.to) {
                var receiver = this.map(to);
                exists = await receiver.findData();
                if (!exists) break;
            }
        }
        this.allowReplyAll = exists;
    }

    async saveAsDraft(): Promise<any> {
        try {
            var that = this;
            this.rewriteBody();
            var data: any = {subject: this.subject, body: this.body};
            data.to = _.pluck(this.to, "id");
            data.cc = _.pluck(this.cc, "id");
            data.bcc = _.pluck(this.bcc, "id");
            data.attachments = this.attachments;

            var path = "/zimbra/draft";
            if (this.id) {
                const response = await http.put(path + "/" + this.id, data);
                Mix.extend(this, response.data);
            } else {
                if (this.parentConversation) {
                    path += "?In-Reply-To=" + this.parentConversation.id;
                    path += "&reply=" + this.replyType;
                }
                let response = await http.post(path, data);
                Mix.extend(this, response.data);
            }
        } catch (e) {
            sendNotificationErrorZimbra(e.response.data.error);
        }
    }

    async send() {
        this.rewriteBody();
        var data: any = {subject: this.subject, body: this.body};
        data.to = _.pluck(this.to, "id");
        data.cc = _.pluck(this.cc, "id");
        data.bcc = _.pluck(this.bcc, "id");
        data.attachments = this.attachments;
        if (data.to.indexOf(model.me.userId) !== -1) {
            Zimbra.instance.folders["inbox"].nbUnread++;
        }
        if (data.cc.indexOf(model.me.userId) !== -1) {
            Zimbra.instance.folders["inbox"].nbUnread++;
        }
        if (data.bcc.indexOf(model.me.userId) !== -1) {
            Zimbra.instance.folders["inbox"].nbUnread++;
        }
        var path = "/zimbra/send?";
        if (!data.subject) {
            data.subject = lang.translate("nosubject");
        }
        if (this.id) {
            path += "id=" + this.id + "&";
        }
        if (this.parentConversation) {
            path += "In-Reply-To=" + this.parentConversation.id;
        }

        try {
            const response = await http.post(path, data);
            const result = response.data;
            // Zimbra.instance.folders["outbox"].mails.refresh();
            // Zimbra.instance.folders["draft"].mails.refresh();

            if (parseInt(result.sent) > 0) {
                this.state = "SENT";
                notify.success("mail.sent");
                return {
                    undelivered: false
                };
            } else {
                sendNotificationErrorZimbra('{"code":"resul.sent<=0"}');
                return {
                    undelivered: true
                };
            }
        } catch (e) {
            sendNotificationErrorZimbra(e.response.data.error);
            return {
                undelivered: true
            };
        }
    }

    async open(forPrint?: boolean, notMakeItRead?: boolean) {
        if (this.unread && this.state !== "DRAFT" && !notMakeItRead) {
            Zimbra.instance.currentFolder.nbUnread--;
        }
        let response = await http.get("/zimbra/message/" + this.id);

        response.data.attachments.map(attachment => {
            attachment.filename = decodeURI((attachment.filename));
        })

        Mix.extend(this, response.data);
        this.to = this.to.map(user =>
            Mix.castAs(User, {
                id: user,
                displayName: this.displayNames.find(
                    name => name[0] === (user as any)
                )[1]
            })
        );

        this.cc = this.cc.map(user =>
            Mix.castAs(User, {
                id: user,
                displayName: this.displayNames.find(
                    name => name[0] === (user as any)
                )[1]
            })
        );

        this.bcc = this.bcc.map(user =>
            Mix.castAs(User, {
                id: user,
                displayName: this.displayNames.find(
                    name => name[0] === (user as any)
                )[1]
            })
        );

        if (!forPrint) {
            await this.updateAllowReply();
        }
    }

    async sendDeliveryReport() {
        await http.post(`/zimbra/message/${this.id}/deliveryReport`);
    }

    async remove() {
        if (!this.id) return;
        if (
            (Zimbra.instance.currentFolder as SystemFolder).folderName !==
            "trash"
        ) {
            await http.put("/zimbra/trash", { id: [this.id] });
            Zimbra.instance.currentFolder.mails.refresh();
            Zimbra.instance.folders["trash"].mails.refresh();
        } else {
            await http.delete("/zimbra/delete", { data: { id: [this.id]} });
            Zimbra.instance.folders["trash"].mails.refresh();
        }
    }

    async removeFromFolder() {
        return http.put("move/root", { id: [this.id] });
    }

    async restore() {
        await http.put("/zimbra/restore", { id: [this.id] });
        Zimbra.instance.folders["trash"].mails.refresh();
    }

    async move(destinationFolder) {
        await http.put(
            "move/userfolder/" + destinationFolder.id,
            { id: [this.id] },
        );
        await Zimbra.instance.currentFolder.mails.refresh();
        await Zimbra.instance.folders.draft.mails.refresh();
    }

    async trash() {
        await http.put("/zimbra/trash", { id: [this.id] });
        await Zimbra.instance.currentFolder.mails.refresh();
        await Zimbra.instance.folders.draft.mails.refresh();
    }

    async postAttachments(attachmentToUpload : Attachment, workspace : boolean) : Promise<void> {
            let post : Promise<AxiosResponse>;
            if (workspace) {
                post = attachmentService.postAttachmentFromWorkspace(attachmentToUpload, this);
            } else {
                post = attachmentService.postAttachmentFromComputer(attachmentToUpload, this)
            }
            const promise: Promise<void> = post
                .then((response: AxiosResponse) => {
                    const attachmentWaiting : Attachment[] =
                        this.attachments.filter((attachment : Attachment) =>
                            attachment.uploadStatus && attachment.uploadStatus == "loading" && attachment != attachmentToUpload);
                    this.attachments = response.data.attachments as Attachment[];
                    this.attachments.forEach((attach: Attachment) => {
                        attach.filename = decodeURI(attach.filename);
                        attach.uploadStatus = "loaded";
                    });
                    this.attachments = this.attachments.concat(attachmentWaiting);
                })
                .catch((e: AxiosError) => {
                    sendNotificationErrorZimbra(e.response.data.error);
                });
            await Promise.resolve(promise);
    }

    downloadAttachmentInWorkspace(attachment : Attachment) : void {
        attachmentService.downloadAttachmentInWorkspace(attachment, this)
            .then(() => {
                notify.info("zimbra.attachment.download.workspace.success");
            })
            .catch((e : AxiosError) => {
                sendNotificationErrorZimbra(e.response.data.error);
            });
    }

    async deleteAttachment(attachment) {
        this.attachments.splice(this.attachments.indexOf(attachment), 1);
        const response = await attachmentService.deleteAttachment(attachment, this);
        this.attachments = response.data.attachments;
        this.attachments.map(attachment => {
            attachment.filename = decodeURI(attachment.filename);
        })
    }

    rewriteBody() {
        const regex = /<img([^>]*)\ssrc=["']\//gi;
        const proto = window.location.protocol;
        const host = window.location.host;
        this.body = this.body.replace(
            regex,
            '<img $1 src="' + proto + "//" + host + "/"
        );
    }
}

export class Mails {
    pageNumber: number;
    api: { get: string; put: string; post: string; delete: string };
    full: boolean;
    selection: Selection<Mail>;
    userFolder: UserFolder;
    loading: boolean;

    push(item: Mail) {
        this.all.push(item);
    }

    get all(): Mail[] {
        return this.selection.all;
    }

    constructor(
        api:
            | { get: string; put: string; post: string; delete: string }
            | UserFolder
    ) {
        if (api instanceof UserFolder) {
            this.userFolder = api;
        } else {
            this.api = api;
        }
        this.loading = false;
        this.selection = new Selection<Mail>([]);
    }

    async removeFromFolder() {
        await http.put(
            "move/root",
            { id: this.selection.selected.map(mail => mail.id) },
        );
    }

    addRange(arr: Mail[], selectAll: boolean) {
        if (!(arr[0] instanceof Mail)) {
            arr.forEach(d => {
                var m = Mix.castAs(Mail, d);
                if (selectAll) m.selected = true;
                this.all.push(m);
            });
        } else {
            arr.forEach(m => {
                if (selectAll) m.selected = true;
                this.all.push(m);
            });
        }
    }

    async sync(data?: {
        pageNumber?: number;
        searchText?: string;
        emptyList?: boolean;
        filterUnread?: boolean;
        selectAll?: boolean;
    }) {
        this.loading =
            !data ||
            !data.pageNumber ||
            data.pageNumber == 0 ||
            (data.searchText != undefined && data.pageNumber == 0);
        if (this.userFolder) {
            await this.userFolderSync(data);
        } else {
            await this.apiSync(data);
        }
        this.loading = false;
    }

    async userFolderSync(data?: {
        pageNumber?: number;
        searchText?: string;
        emptyList?: boolean;
        filterUnread?: boolean;
        selectAll?: boolean;
    }) {
        if (!data) {
            data = {};
        }
        if (!data.pageNumber) {
            data.pageNumber = 0;
        }
        if (!data.searchText) {
            data.searchText = "";
        } else {
            data.searchText = "&search=" + data.searchText;
        }
        if (!data.filterUnread) {
            data.filterUnread = false;
        }
        if (!data.selectAll) {
            data.selectAll = false;
        }
        const response = await http.get(
            "/zimbra/list?folder=" +
            this.userFolder.path +
            "&restrain=&page=" +
            data.pageNumber +
            "&unread=" +
            data.filterUnread +
            data.searchText
        );
        if (data.emptyList !== false) {
            this.all.splice(0, this.all.length);
        }
        let idxmail = 0;
        response.data.forEach(m => {
            if (data.selectAll) m.selected = true;
            m.count = idxmail;
            idxmail++;
            this.all.push(Mix.castAs(Mail, m));
        });
        if (response.data.length === 0) {
            this.full = true;
        }
        this.selection.all = _.sortBy(this.all, 'date').reverse();
    }

    async apiSync(data?: {
        pageNumber?: number;
        searchText?: string;
        emptyList?: boolean;
        filterUnread?: boolean;
        selectAll?: boolean;
    }): Promise<void> {
        if (!data) {
            data = {};
        }
        if (!data.pageNumber) {
            data.pageNumber = 0;
        }
        if (!data.searchText) {
            data.searchText = "";
        } else {
            data.searchText = "&search=" + data.searchText;
        }
        if (!data.filterUnread) {
            data.filterUnread = false;
        }
        if (!data.selectAll) {
            data.selectAll = false;
        }
        let response = await http.get(
            this.api.get +
            "&page=" +
            data.pageNumber +
            "&unread=" +
            data.filterUnread +
            data.searchText
        );
        if (data.emptyList !== false) {
            this.all.splice(0, this.all.length);
        }

        this.addRange(response.data, data.selectAll);
        if (response.data.length === 0) {
            this.full = true;
        }
        this.selection.all = _.sortBy(this.all, 'date').reverse();
        return;
    }

    refresh() {
        this.pageNumber = 0;
        this.full = false;
        return this.sync();
    }

    async toTrash() {
        await http.put(
            "/zimbra/trash",
            { id: this.selection.selected.map(mail => mail.id) },
        );
        this.selection.removeSelection();
    }

    removeSelection() {
        this.selection.removeSelection();
    }

    async moveSelection(destinationFolder) {
        await http.put(
            "move/userfolder/" +
            destinationFolder.id,
            { id: this.selection.selected.map(mail => mail.id) },
        );
    }

    async toggleUnread(unread) {
        // Unselect mails that are not from inbox
        var selected = [];
        var folder = "";

        this.selection.selected.forEach(mail => {
            folder = mail.getSystemFolder();
            if (folder === "INBOX" || folder === "OUTBOX") {
                selected.push(mail);
            }
        });
        if (selected.length === 0) return;

        var paramsIds = toFormData({id: _.pluck(selected, "id")});
        var paramUnread = `unread=${unread}`;

        try {
            await http.post(`/zimbra/toggleUnread?${paramsIds}&${paramUnread}`);
            selected.forEach(mail => (mail.unread = unread));
        } catch (e) {
            notify.error(e.response.data.error);
        }
    }
}

let mailFormat = {
    reply: {
        prefix: "reply.re",
        content: ""
    },
    transfer: {
        prefix: "reply.fw",
        content: ""
    }
};

http.get("/zimbra/public/template/mail-content/transfer.html").then(
    response => {
        format.transfer.content = response.data;
    }
);

http.get("/zimbra/public/template/mail-content/reply.html").then(response => {
    format.reply.content = response.data;
});

export const format = mailFormat;

export const sendNotificationErrorZimbra = (errorReturnByZimbra: string): void => {

    //TODO extraire la gestion d'erreur du mail
    try {
        console.error("Zimbra returning : ", errorReturnByZimbra);
        switch (JSON.parse(errorReturnByZimbra).code) {
            case SERVICE.INVALID_REQUEST:
                notify.error(lang.translate("zimbra.message.error.attachment"));
                break;
            case MAIL.MESSAGE_TOO_BIG:
                notify.info(lang.translate("zimbra.message.error.mail.size"));
                break;
            case QUOTA.QUOTA_EXCEEDED:
                notify.error(lang.translate("zimbra.quota.info.warning"));
                break;
            default:
                notify.error(lang.translate("zimbra.default.intern.error" + errorReturnByZimbra));

        }
    } catch (error) {
        console.error("Error in processing notification error : ", error);
        notify.error(lang.translate(errorReturnByZimbra));
    }
};
