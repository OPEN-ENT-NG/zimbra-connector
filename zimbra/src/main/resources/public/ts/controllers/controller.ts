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

import {$, _, angular, Document, idiom as lang, moment, ng, notify, skin, template} from "entcore";
import {
    Attachment,
    Group,
    Mail,
    quota,
    RECEIVER_TYPE,
    REGEXLIB,
    SCREENS,
    SystemFolder,
    User,
    UserFolder,
    Users,
    ViewMode,
    Zimbra
} from "../model";


import {Preference} from "../model/preferences";
import http from "../model/http";


declare const window: any;

export let zimbraController = ng.controller("ZimbraController", [
    "$location",
    "$scope",
    "$timeout",
    "$compile",
    "$sanitize",
    "model",
    "route",
    function($location, $scope, $timeout, $compile, $sanitize, model, route) {
        $scope.state = {
            selectAll: false,
            filterUnread: false,
            searching: false,
            current: undefined,
            newItem: undefined,
            draftError: false,
            dragFolder: undefined,
            emptyMessage: lang.translate("folder.empty"),
            searchFailed: false,
            draftSaveDate: null
        };
        $scope.display = {
            searchLong: 'search.condition',
            searchSmall: 'search.condition.small',
        };

        $scope.quotaMessage = {
            active: false,
            percent: 0
        };

        $scope.initQuotaMessage = function () {
          if (window.user.quota.quota ===0) return;
          const percent = Math.round((100/window.user.quota.quota)*window.user.quota.storage*100)/100;
          $scope.quotaMessage = {
              active: percent > 90,
              percent
          }
        };

        $scope.zimbra = Zimbra.instance;

        $scope.initFolders = function () {
            $scope.folders = Zimbra.instance.folders;
            $scope.userFolders = Zimbra.instance.userFolders;
        };

        $scope.displayLightBox = {
            readMail : false
        };
        $scope.isFileLoading = false;
        route({
            readMail: async function(params) {
                await initPreference();
                await Zimbra.instance.sync();
                Zimbra.instance.folders.openFolder("inbox");
                $scope.constructNewItem();
                template.open("page", "folders");
                template.open("right-side","right-side");
                template.open("header","header-lists");
                $scope.readMail(new Mail(params.mailId));
                $scope.$apply();
            },
            writeMail: async function(params) {
                await initPreference();
                await Zimbra.instance.sync();
                Zimbra.instance.folders.openFolder("inbox");
                $scope.constructNewItem();
                switch (params.who) {
                    case RECEIVER_TYPE.USER:
                        await initUserTo(params.idUndefined);
                        if(!$scope.state.newItem.to) notify.error(lang.translate("zimbra.message.error.get.user"));
                        break;
                    case RECEIVER_TYPE.GROUP:
                        await initGroupTo(params.idUndefined);
                        if(!$scope.state.newItem.to) notify.error(lang.translate("zimbra.message.error.get.group"));
                        break;
                    case RECEIVER_TYPE.FAVORITE:
                        await initFavoriteTo(params.idUndefined);
                        if(!$scope.state.newItem.to) notify.error(lang.translate("zimbra.message.error.get.favorite"));
                        break;
                    default:
                        notify.error(lang.translate("zimbra.message.error.get.default"));
                        break
                }
                template.open("page", "folders");
                template.open("right-side","right-side");
                template.open("header","header-lists");
                template.open("right-side", "mail-actions/write-mail");
                $scope.$apply();
            },
            inbox: async () => {
                await initPreference();
                await Zimbra.instance.sync();
                template.open("page", "folders");
                $scope.openFolder("inbox");
                $scope.$apply();
            }
        });

        $scope.lang = lang;
        $scope.notify = notify;
        $scope.initFolders();
        $scope.users = {
            list: Zimbra.instance.users,
            search: "",
            found: [],
            foundCC: []
        };
        template.open("right-side","right-side");
        template.open("header","header-lists");
        template.open("main-right-side", "folders-templates/inbox");
        template.open("toaster", "folders-templates/toaster");
        $scope.formatFileType = Document.role;
        $scope.sending = false;

        const initPreference = async ():Promise<void> => {
            $scope.preference = await new Preference();
            if(window.innerWidth <= SCREENS.FAT_MOBILE){
                await $scope.preference.switchViewMode("LIST");
                $scope.preference.viewMode = ViewMode.LIST;
            }
        };

        const initUserTo = async (userId:string):Promise<void> => {
            const user = new User(userId);
            await user.findData();
            if(_.isString(userId)){
                if(user.displayName){
                    $scope.addUser(user);
                }
            }else if(userId !== undefined) {
                for(let i = 0; i < userId.length; i++){
                    let user = new User(userId[i]);
                    await user.findData();
                    $scope.addUser(user);
                }
            }
        };

        const initGroupTo = async (idGroup:string):Promise<void> => {
            const group = new Group();
            await group.getGroup(idGroup);
            if(group.id){
                group.isGroup = true;
                $scope.addUser(group);
            };
        };

        const initFavoriteTo = async (idGroupFavorite:string):Promise<void> => {
            const users = new Users();
            const favoriteUsers:Array<User> = await users.getUsersByFavoriteId(idGroupFavorite);
            if(favoriteUsers){
                favoriteUsers.forEach( (userInFavorite:User):void => {
                    $scope.addUser(userInFavorite);
                });
            }
        };

        const sendDeliveryReport = async (mail: Mail): Promise<void> => {
            if (mail.isReportRequired) {
                try {
                    await mail.sendDeliveryReport();
                    notify.info('send.delivery.report.success');
                } catch (e) {
                    notify.error('send.delivery.report.err');
                }
            }
        }

        $scope.addUser = user => {
            if (!$scope.state.newItem.to) {
                $scope.state.newItem.to = [];
            }

            $scope.state.newItem.to.push(user);
        };

        $scope.resetScope = function() {
            $scope.openInbox();
        };

        $scope.resetState = function() {
            $scope.state.selectAll = false;
            $scope.state.filterUnread = false;
            $scope.state.searching = false;
            $scope.state.draftError = false;
            $scope.state.emptyMessage = lang.translate("folder.empty");
            $scope.state.searchFailed = false;
            $scope.state.draftSaveDate = null;
        };

        $scope.constructNewItem = function() {
            $scope.state.newItem = new Mail();
            $scope.state.newItem.setMailSignature($scope.getSignature());
        };

        $scope.getSignature = () : string => {
            if (Zimbra.instance.preference.useSignature) {
                let signature : string = Zimbra.instance.preference.signature.replace(
                    new RegExp("\n", "g"),
                    "<br>"
                );
                signature = angular.element('<textarea />').html(signature).text();
                return signature;
            }
            return "";
        };

        $scope.openFolder = async folderName => {
            if (!folderName) {
                if (Zimbra.instance.currentFolder instanceof UserFolder) {
                    $scope.openUserFolder(Zimbra.instance.currentFolder, {});
                    return;
                }
                folderName = (Zimbra.instance.currentFolder as SystemFolder)
                    .folderName;
            }
            $scope.state.newItem = new Mail();
            $scope.state.newItem.setMailSignature($scope.getSignature());
            template.open("right-side","right-side");
            template.open("header","header-lists");
            template.open("main-right-side", "folders-templates/" + folderName);
            $scope.resetState();
            await Zimbra.instance.folders.openFolder(folderName);
            $scope.openRightSide();
            $scope.$apply();
            $scope.updateWherami();
        };

        $scope.openUserFolderOnDragover = async (folder: UserFolder, obj) => {
            if ((Zimbra.instance.currentFolder as UserFolder).id != folder.id)
                await $scope.openUserFolder(folder, obj);
        };

        $scope.openUserFolder = async (folder: UserFolder, obj, $event) => {
            if ($event) {
                if ($event.target.className.includes('trash')) return;
                if ($event.target.className.includes('arrow')) {
                    obj.template = "folder-content";
                    folder.selected = !folder.selected;
                    return;
                }
            }
            $scope.mail = undefined;
            $scope.state.newItem = new Mail();
            $scope.state.newItem.setMailSignature($scope.getSignature());
            obj.template = "folder-content";
            template.open("right-side","right-side");
            template.open("header","header-lists");
            template.open("main-right-side", "folders-templates/user-folder");
            $scope.resetState();
            await folder.open();
            $scope.openRightSide();
            $scope.$apply();
            $scope.nextPage();
            $scope.updateWherami();
        };

        $scope.isParentOf = function(folder, targetFolder) {
            if (!targetFolder || !targetFolder.parentFolder) return false;

            var ancestor = targetFolder.parentFolder;
            while (ancestor) {
                if (folder.id === ancestor.id) return true;
                ancestor = ancestor.parentFolder;
            }
            return false;
        };

        $scope.variableMailAction = function(mail) {
            var systemFolder = mail.getSystemFolder();
            if (systemFolder === "DRAFT") return $scope.viewMail(mail);
            else if (systemFolder === "OUTBOX") return $scope.viewMail(mail);
            else return $scope.readMail(mail);
        };

        $scope.removeFromUserFolder = async (event, mail) => {
            if (Zimbra.instance.currentFolder instanceof UserFolder) {
                await Zimbra.instance.currentFolder.removeMailsFromFolder();
                $scope.state.selectAll = false;
                $scope.refreshFolders();
                $scope.$apply();
            }
        };

        $scope.nextPage = async () => {
            if (template.containers['main-right-side'].indexOf("folders-templates") > 0) {
                await Zimbra.instance.currentFolder.nextPage(
                    $scope.state.selectAll
                );
                $scope.$apply();
            }
        };

        $scope.switchSelectAll = function() {
            if ($scope.state.selectAll) {
                Zimbra.instance.currentFolder.selectAll();
            } else {
                Zimbra.instance.currentFolder.deselectAll();
            }
        };

        $scope.refreshSelectionState =  function(mail) {
            if (!mail.selected) $scope.state.selectAll = false;
            if($('plus#mailOptions').children('div.opener').hasClass('minus')){
                $scope.stopPropagation= true;
                $('plus#mailOptions').trigger('click');
            }

        };

        function setCurrentMail(mail: Mail, doNotSelect?: boolean) {
            $scope.state.current = mail;
            Zimbra.instance.currentFolder.deselectAll();
            if (!doNotSelect) $scope.state.current.selected = true;
            $scope.mail = mail;
        }

        $scope.viewMail = async function(mail) {
            if( !$scope.preference.isColumn()){
                template.open("right-side", "mail-actions/view-mail");
                window.scrollTo(0, 0);
            }else{
                $scope.openMailOnRightTemplate();
            }
            setCurrentMail(mail, true);
            try {
                await mail.open();
                $scope.$root.$emit("refreshMails");
            } catch (e) {
                template.open("page", "errors/e404");
            }
        };

        $scope.refresh = async function() {
            notify.info("updating");
            await Zimbra.instance.currentFolder.mails.refresh();
            $scope.$apply();
        };

        $scope.readMail = async (mail: Mail, notMakeItRead?: boolean) => {
            if(!$scope.preference) {
                $scope.preference = await new Preference();
            }
            if(!$scope.preference.isColumn()){
                template.open("right-side", "mail-actions/read-mail");
                window.scrollTo(0, 0);
            }else{
                $scope.openMailOnRightTemplate();
            }
            setCurrentMail(mail, true);
            try {
                await mail.open(false,notMakeItRead);
                await sendDeliveryReport(mail);
                $scope.$root.$emit("refreshMails");
            } catch (e) {
                template.open("page", "errors/e404");
            }
        };
        $scope.search = async (text: string) => {
            if (text.trim().length > 2) {
                $scope.state.searchFailed = false;
                $scope.state.searching = true;
                $scope.state.emptyMessage = lang.translate("no.result");
                setTimeout(async function() {
                    await Zimbra.instance.currentFolder.search(text);
                    $scope.$apply();
                }, 1);
            } else {
                $scope.state.searchFailed = true;
            }
        };

        $scope.cancelSearch = async () => {
            $scope.state.searching = false;
            $scope.state.searchFailed = false;
            setTimeout(async function() {
                await Zimbra.instance.currentFolder.search("");
                $scope.$apply();
            }, 1);
        };

        $scope.filterUnread = async () => {
            setTimeout(async function() {
                if($scope.zimbra.currentFolder.nbUnread == 0) {
                    $scope.state.filterUnread = false;
                }
                await Zimbra.instance.currentFolder.filterUnread(
                    $scope.state.filterUnread
                );
                if(!$scope.isMobileScreen() ) $scope.openRightSide();
                $scope.$apply();
            }, 1);
        };

        $scope.isLoading = () => {
            return Zimbra.instance.currentFolder.mails.loading;
        };

        $scope.nextMail = async (trash?: boolean) => {
            var mails = Zimbra.instance.currentFolder.mails.all;
            var idx = mails.findIndex(mail => {
                return mail.id === $scope.state.current.id;
            });
            var nextMail = null;
            if (idx > -1 && idx < mails.length - 1) nextMail = mails[idx + 1];
            if (nextMail) {
                if (trash) {
                    setCurrentMail(nextMail, true);
                    await nextMail.open();
                    $scope.$apply();
                } else {
                    $scope.variableMailAction(nextMail);
                }
            }
            if (idx === mails.length - 2 && Zimbra.instance.currentFolder.count > mails.length) {
                await Zimbra.instance.currentFolder.nextPage(
                    $scope.state.selectAll
                );
                $scope.$apply();
            }
        };

        $scope.previousMail = async (trash?: boolean) => {
            var mails = Zimbra.instance.currentFolder.mails.all;
            var idx = mails.findIndex(mail => {
                return mail.id === $scope.state.current.id;
            });
            var previousMail = null;
            if (idx > -1 && idx > 0) previousMail = mails[idx - 1];
            if (previousMail) {
                if (trash) {
                    setCurrentMail(previousMail, true);
                    await previousMail.open();
                    $scope.$apply();
                } else {
                    $scope.variableMailAction(previousMail);
                }
            }
        };

        $scope.transfer = async () => {
            template.open("right-side", "mail-actions/write-mail");
            $('contextual-menu').removeClass('show');
            const mail = $scope.state.newItem as Mail;
            mail.parentConversation = $scope.mail;
            mail.replyType = "F";
            await mail.setMailContent(
                $scope.mail,
                "transfer",
                $compile,
                $sanitize,
                $scope,
                $scope.getSignature()
            );
            await Zimbra.instance.folders.draft.transfer(
                mail.parentConversation,
                $scope.state.newItem
            );
            $('div.drawing-zone').trigger('click') ;
            $scope.$apply();
        };
        $scope.cantSendMail = ()=>{
            let noA = $scope.state.newItem.to ? $scope.state.newItem.to.length===0 : !$scope.state.newItem.to;
            let noCC = $scope.state.newItem.cc  ? $scope.state.newItem.cc.length===0 : !$scope.state.newItem.cc;
            let noBCC = $scope.state.newItem.bcc ? $scope.state.newItem.bcc.length ===0 : !$scope.state.newItem.bcc;
            return  ( noA && noCC && noBCC )
                || ($scope.state.newItem.loadingAttachments && $scope.state.newItem.loadingAttachments.length > 0)
                || $scope.sending
                || $scope.isContainIframe;
        };
        $scope.reply = async (outbox?: boolean) => {
            template.open("right-side", "mail-actions/write-mail");
            const mail = $scope.state.newItem as Mail;
            mail.parentConversation = $scope.mail;
            await mail.setMailContent(
                $scope.mail,
                "reply",
                $compile,
                $sanitize,
                $scope,
                $scope.getSignature()
            );
            if (outbox) mail.to = $scope.mail.to;
            else $scope.addUser($scope.mail.sender());
            $scope.$apply();
            $('div.drawing-zone').trigger('click');
        };

        $scope.replyAll = async () => {
            template.open("right-side", "mail-actions/write-mail");
            const mail = $scope.state.newItem as Mail;
            mail.parentConversation = $scope.mail;
            await mail.setMailContent(
                $scope.mail,
                "reply",
                $compile,
                $sanitize,
                $scope,
                $scope.getSignature(),
                true
            );
            if ($scope.mail.sender().id !== model.me.userId)
                mail.to = _.filter($scope.state.newItem.to, function(user) {
                    return user.id !== model.me.userId;
                });
            if (
                !_.findWhere($scope.state.newItem.to, {
                    id: $scope.mail.sender().id
                })
            ) {
                $scope.addUser($scope.mail.sender());
            }
            $scope.$apply();
            $('div.drawing-zone').trigger('click');
        };

        $scope.editDraft = async (draft: Mail) => {
            template.open("right-side", "mail-actions/write-mail");
            window.scrollTo(0, 0);
            $scope.state.newItem = draft;
            await draft.open();
            $scope.$apply();
        };

        $scope.quickSaveDraft = async () => {
            $scope.saveDraft($scope.state.newItem);
        };

        $scope.hourIsit = () => $scope.state.draftSaveDate.format("HH");
        $scope.minIsit = () => $scope.state.draftSaveDate.format("mm");
        $scope.secIsit = () => $scope.state.draftSaveDate.format(":ss");

        $scope.saveDraft = async item => {
            try {
                await Zimbra.instance.folders.draft.saveDraft(item);
                $scope.state.draftError = false;
                $scope.state.draftSaveDate = moment();
            } catch (e) {
                $scope.state.draftError = true;
            }
        };

        $scope.isContainIframe = false;
        $scope.saveDraftAuto = async () => {
            $scope.isContainIframe = $scope.state.newItem.body.search(REGEXLIB.iframeSearch) !== -1;
            if (!$scope.draftSavingFlag) {
                $scope.draftSavingFlag = true;
                var temp = $scope.state.newItem;
                setTimeout(async function() {
                    if (!$scope.sending && temp.state != "SENT") {
                        $scope.saveDraft(temp);
                    }
                    if($scope.isContainIframe){
                        notify.info(lang.translate("zimbra.message.info.iframe"));
                    }
                    $scope.draftSavingFlag = false;
                }, $scope.zimbra.configSaveDraftAutoTime);
            }
        };

        $scope.refreshSignature = async (use: boolean) : Promise<void> => {
            await Zimbra.instance.putPreference();
            const body = $($scope.state.newItem.body);
            const signature : string = $scope.getSignature();
            if (body.filter(".new-signature").length > 0) {
                body.filter(".new-signature").text("");
                if (use) body.filter(".new-signature").append(signature);
                $scope.state.newItem.body = _.map(body, function(el) {
                    return el.outerHTML;
                }).join("");
                $scope.$apply();
            } else {
                $scope.state.newItem.setMailSignature(signature);
            }
        };

        $scope.result = {};

        $scope.sendMail = async () => {
            $timeout(() => $('div.drawing-zone').trigger('click'));
            $scope.sending = true; //Blocks submit button while message hasn't been send
            $timeout(() => $scope.sending = false, 5000)
            $scope.result = await $scope.state.newItem.send();
            if (!$scope.result.undelivered) {
                $scope.state.newItem = new Mail();
                if($scope.folders.draft.totalNb > 0 && $scope.folders.draft.totalNb) $scope.folders.draft.totalNb--;
                $scope.state.newItem.setMailSignature($scope.getSignature());
                await $scope.openFolder(Zimbra.instance.folders.inbox.folderName);
            }
            $scope.sending = false;
        };

        function removeSelectionFromTrash() {
            const folderIds = [];
            Zimbra.instance.folders.trash.userFolders.selected.forEach(({id}) => folderIds.push(id));
            Zimbra.instance.folders.trash.userFolders.all = Zimbra.instance.folders.trash.userFolders.all.filter(({id}) => folderIds.indexOf(id) === -1);

            const mailIds = [];
            Zimbra.instance.folders.trash.mails.selection.forEach(({id}) => mailIds.push(id));
            Zimbra.instance.folders.trash.userFolders.all = Zimbra.instance.folders.trash.userFolders.all.filter(({id}) => folderIds.indexOf(id) === -1);
            $scope.$apply();
        }

        $scope.restore = async () => {
            await Zimbra.instance.folders.trash.restore();
            await $scope.refreshFolders();
            removeSelectionFromTrash();
            $scope.state.selectAll = false;
            $scope.$apply();
        };

        $scope.removeSelection = async () => {
            await Zimbra.instance.currentFolder.removeSelection();
            $scope.refreshFolders();
            if ($scope.zimbra.currentFolder.folderName !== 'trash') {
                removeSelectionFromTrash();
            }
            $scope.state.selectAll = false;
            $scope.displayLightBox.folder =false;
            $scope.$apply();
        };

        $scope.toggleUnreadSelection = async (unread, folder) => {
            await Zimbra.instance.currentFolder.toggleUnreadSelection(unread);
            $scope.state.selectAll = false;
            $scope.updateWherami();
            $scope.$root.$emit("refreshMails");
            $scope.$apply();
        };

        $scope.canMarkUnread = () => {
            return (
                Zimbra.instance.currentFolder.mails.selection.selected.find(
                    e => e.getSystemFolder() !== "INBOX"
                ) == undefined &&
                Zimbra.instance.currentFolder.mails.selection.selected.find(
                    e => !e.unread
                )
            );
        };

        $scope.canMarkRead = () => {
            return (
                Zimbra.instance.currentFolder.mails.selection.selected.find(
                    e => e.getSystemFolder() !== "INBOX"
                ) == undefined &&
                Zimbra.instance.currentFolder.mails.selection.selected.find(
                    e => e.unread
                )
            );
        };

        $scope.allReceivers = function(mail) {
            var receivers = mail.to.slice(0);
            mail.toName &&
            mail.toName.forEach(function(deletedReceiver) {
                receivers.push({
                    deleted: true,
                    displayName: deletedReceiver
                });
            });
            return receivers;
        };

        $scope.filterUsers = function(mail) {
            return function(user) {
                if (user.deleted) {
                    return true;
                }
                var mapped = mail.map(user);
                return (
                    typeof mapped !== "undefined" &&
                    typeof mapped.displayName !== "undefined" &&
                    mapped.displayName.length > 0
                );
            };
        };
        $scope.anyRecipients = () => {
            return ($scope.state.newItem.cc && $scope.state.newItem.cc.length!==0) || ($scope.state.newItem.bcc && $scope.state.newItem.bcc.length!==0)
        };
        $scope.updateFoundUsers = async (search, model, founds) => {
            var include = [];
            var exclude = model || [];
            if ($scope.mail) {
                include = _.map($scope.mail.displayNames, function(item) {
                    return new User(item[0], item[1]);
                });
            }
            var users = await Zimbra.instance.users.findUser(
                search,
                include,
                exclude
            );
            Object.assign(founds, users, { length: users.length });
            $scope.$apply();
        };

        $scope.template = template;
        $scope.lightbox = {};

        $scope.rootFolderTemplate = { template: "folder-root-template" };
        $scope.refreshFolders = async () => {
            await Zimbra.instance.computeRootFolder();
            $scope.initFolders();
            $scope.updateWherami();
            $scope.$apply();
        };

        $scope.refreshFolder = async () => {
            await Zimbra.instance.currentFolder.sync();
            $scope.state.selectAll = false;
            if (Zimbra.instance.currentFolder instanceof UserFolder) {
                $scope.openUserFolder(Zimbra.instance.currentFolder, {});
            } else $scope.updateWherami();
            $scope.$apply();
        };

        $scope.currentFolderDepth = function() {
            if (!($scope.currentFolder instanceof UserFolder)) return 0;

            return $scope.currentFolder.depth();
        };

        $scope.moveSelection = function() {
            $scope.destination = {};
            $scope.lightbox.show = true;
            template.open("lightbox", "move-mail");
        };

        $scope.returnSelection = function() {
            $scope.lightbox.show = true;
            template.open("lightbox", "return-mail");
        };

        $scope.moveToFolderClick = async (folder, obj) => {
            obj.template = "";

            if (folder.userFolders.all.length > 0) {
                $timeout(function() {
                    obj.template = "move-folders-content";
                }, 10);
                return;
            }

            $timeout(function() {
                obj.template = "move-folders-content";
            }, 10);
        };

        $scope.returnMail = async (id, comment) => {
            $scope.lightbox.show = false;
            template.close("lightbox");
            var data: any = {id: id, comment: comment};
            let response = await http.put("/zimbra/return", data);
            if(response.status == 200) {
                $scope.refresh();
                $scope.$apply();
            }
        }

        $scope.moveMessages = async folderTarget => {
            $scope.lightbox.show = false;
            template.close("lightbox");
            let mailIds = [];
            let counter = 0;
            $scope.zimbra.currentFolder.mails.selection.selected.forEach(({id, unread}) => {
                mailIds.push(id);
                if (unread) counter++;
            });
            await Zimbra.instance.currentFolder.mails.moveSelection(
                folderTarget
            );
            folderTarget.nbUnread += counter;
            $scope.zimbra.currentFolder.nbUnread -= counter;
            $scope.zimbra.currentFolder.mails.selection.all = $scope.zimbra.currentFolder.mails.selection.all.filter(({id}) => mailIds.indexOf(id) === -1);
            $scope.refresh();
            $scope.$apply();
        };

        $scope.openNewFolderView = function() {
            $scope.newFolder = new UserFolder();
            if (Zimbra.instance.currentFolder instanceof UserFolder) {
                $scope.newFolder.parentFolderId = (Zimbra.instance
                    .currentFolder as UserFolder).id;
            }

            $scope.lightbox.show = true;
            template.open("lightbox", "create-folder");
        };
        $scope.createFolder = async () => {
            await $scope.newFolder.create();
            await $scope.refreshFolders();
            $scope.lightbox.show = false;
            template.close("lightbox");
            $scope.$apply();
        };
        $scope.openRenameFolderView = function(folder, $event) {
            $event.stopPropagation();
            $scope.targetFolder = new UserFolder();
            $scope.targetFolder.name = folder.name;
            $scope.targetFolder.oldName = folder.name;
            $scope.targetFolder.id = folder.id;
            $scope.targetFolder.path = folder.path;
            $scope.targetFolder.oldPath = folder.path;
            $scope.targetFolder.parentPath = folder.parentPath;
            $scope.lightbox.show = true;
            template.open("lightbox", "update-folder");
        };
        $scope.updateFolder = async () => {
            await $scope.targetFolder.update();
            const parentFolder = window.folderMap.get($scope.targetFolder.parentPath);
            const folders = parentFolder.path !== '/Inbox' ? parentFolder.userFolders : $scope.zimbra.userFolders;
            folders.all.forEach(folder => {
                if (folder.id === $scope.targetFolder.id) {
                    folder.name = $scope.targetFolder.name;
                    folder.path = folder.path.replace($scope.targetFolder.oldName, $scope.targetFolder.name);
                    folder.parentPath = $scope.targetFolder.parentPath;
                }
            });
            folders.all = folders.all.filter(folder => folder.id !== $scope.targetFolder.id);
            await $scope.refreshFolders();
            $scope.lightbox.show = false;
            template.close("lightbox");
            $scope.$apply();
        };
        $scope.isOpenedFolder = (folder: UserFolder) => {
            return folder.selected;
        };
        $scope.isClosedFolder = (folder: UserFolder) => {
            return !$scope.isOpenedFolder(folder);
        };
        $scope.trashFolder = async (folder: UserFolder) => {
            await folder.trash();
            await $scope.refreshFolders();
            const parentFolder = window.folderMap.get(folder.parentPath);
            const folders = parentFolder.path !== '/Inbox' ? parentFolder.userFolders : $scope.zimbra.userFolders;
            folders.all = folders.all.filter(f => f.path !== folder.path);
            if ($scope.zimbra.currentFolder.folderName !== 'trash') {
                $scope.openFolder('trash');
            } else {
                $scope.folders.trash.userFolders.all.push(folder);
            }
            $scope.$apply();
        };
        $scope.restoreFolder = function(folder) {
            folder.restore().done(function() {
                $scope.refreshFolders();
            });
        };
        $scope.deleteFolder = function(folder) {
            folder.delete().done(function() {
                $scope.refreshFolders();
            });
        };

        var letterIcon = document.createElement("img");
        letterIcon.src = skin.theme + "../../img/icons/message-icon.png";
        $scope.drag = function(item, $originalEvent) {
            var selected = [];
            $scope.state.dragFolder = Zimbra.instance.currentFolder;
            if (
                Zimbra.instance.currentFolder.mails.selection.selected.indexOf(
                    item
                ) > -1
            )
                selected =
                    Zimbra.instance.currentFolder.mails.selection.selected;
            else selected.push(item);

            $originalEvent.dataTransfer.setDragImage(letterIcon, 0, 0);
            try {
                $originalEvent.dataTransfer.setData(
                    "application/json",
                    JSON.stringify(selected)
                );
            } catch (e) {
                $originalEvent.dataTransfer.setData(
                    "Text",
                    JSON.stringify(selected)
                );
            }
        };
        $scope.dropCondition = function(targetItem) {
            return function(event) {
                let dataField =
                    event.dataTransfer.types.indexOf &&
                    event.dataTransfer.types.indexOf("application/json") > -1
                        ? "application/json" //Chrome & Safari
                        : event.dataTransfer.types.contains &&
                        event.dataTransfer.types.contains("application/json")
                        ? "application/json" //Firefox
                        : event.dataTransfer.types.contains &&
                        event.dataTransfer.types.contains("Text")
                            ? "Text" //IE
                            : undefined;

                if (
                    targetItem.foldersName &&
                    targetItem.foldersName !== "trash"
                )
                    return undefined;

                return dataField;
            };
        };

        $scope.dropTo = function(targetItem, $originalEvent) {
            var dataField = $scope.dropCondition(targetItem)($originalEvent);
            var originalItems = JSON.parse(
                $originalEvent.dataTransfer.getData(dataField)
            );
            if (targetItem.folderName === "trash")
                $scope.dropTrash(originalItems);
            else $scope.dropMove(originalItems, targetItem);
        };

        $scope.removeMail = async () => {
            await $scope.mail.remove();
            $scope.openFolder();
        };

        $scope.dropMove = async (mails, folder) => {
            var mailObj;
            for (let mail of mails) {
                mailObj = new Mail(mail.id);
                await mailObj.move(folder);
                $scope.$apply();
            }

            $scope.refreshFolders();
            $scope.$apply();
        };

        $scope.dropTrash = async mails => {
            var mailObj;
            for (let mail of mails) {
                mailObj = new Mail(mail.id);
                await mailObj.trash();
                $scope.$apply();
            }

            if (
                !(await $scope.countDraft(
                    $scope.state.dragFolder,
                    $scope.state.dragFolder
                ))
            ) {
                await $scope.state.dragFolder.countUnread();
            }
            $scope.$apply();
        };

        //Given a data size in bytes, returns a more "user friendly" representation.
        $scope.getAppropriateDataUnit = quota.appropriateDataUnit;

        $scope.formatSize = function(size) {
            var formattedData = $scope.getAppropriateDataUnit(size);
            return (
                Math.round(formattedData.nb * 10) / 10 +
                " " +
                formattedData.order
            );
        };

        $scope.deleteAttachment = async function (event, attachment, mail) {
            await mail.deleteAttachment(attachment);
            $scope.isFileLoading = false;
            $scope.$apply();
        };

        $scope.quota = quota;

        $scope.countDraft = async (folderSource, folderTarget) => {
            return folderSource.getName() === "DRAFT" ||
                folderTarget.getName() === "DRAFT";
        };

        $scope.emptyTrash = async () => {
            $scope.lightbox.show = true;
            await template.open("lightbox", "empty-trash");
        };

        $scope.removeTrashMessages = async () => {
            $scope.lightbox.show = false;
            await Zimbra.instance.folders.trash.removeAll();
            Zimbra.instance.folders.trash.userFolders.all = [];
            Zimbra.instance.folders.trash.mails.selection.all = [];
            await $scope.refreshFolders();
        };

        $scope.updateWherami = () => {
            $timeout(function() {
                $("body").trigger("whereami.update");
            }, 100);
        };

        $scope.isLocalAdmin = () => {
            return (
                model.me.functions &&
                model.me.functions.ADMIN_LOCAL &&
                model.me.functions.ADMIN_LOCAL.scope
            );
        };

        $scope.openExpertMode = () => {
            window.open('/zimbra/preauth');
        };

        $scope.goToExternal = (path) => {
            window.location.href = path;
        };

        $scope.openRightSide = async () => {
            if($scope.preference.viewMode == ViewMode.COLUMN){
                $scope.zimbra.currentFolder.mails &&  $scope.zimbra.currentFolder.mails.all.length > 0 ?
                    await $scope.readMail($scope.zimbra.currentFolder.mails.all[0], true)
                    : $scope.closeRightSide();
                $scope.$apply();
            }else{
                $scope.closeRightSide();
            }
        };
        $scope.switchViewMode = async (mode:string) =>{
            status = await $scope.preference.switchViewMode(mode);
            if(status !== '200'){
                $scope.preference = new Preference();
            }
            $scope.openRightSide();
        };
        $scope.closeRightSide = () => {
            template.close("right-view-mail");
            $scope.$apply();
        };
        $scope.openMailOnRightTemplate = () => {
            let templateName = _.contains( ['outbox', 'trash'], $scope.getFolder()) ? 'view-mail' : 'read-mail';
            if(!$scope.isMobileScreen()){
                template.open("right-view-mail", "mail-actions/"+templateName);
            }else{
                template.open("main-right-side", "mail-actions/"+templateName);
            }
        };
        $scope.isMobileScreen = () =>{
            return (window.outerWidth < SCREENS.FAT_MOBILE);
        };
        $scope.showRightSide = () =>
            $scope.preference.isColumn()
            && ($scope.getFolder() !== 'draft')
            && $scope.zimbra.currentFolder.mails.all.length;
        $scope.getFolder=()=>{
            return (Zimbra.instance.currentFolder as SystemFolder).folderName;
        };

        $scope.displayUser = async (user) =>  {
            $scope.user = new User();
            let id = user.id;
            let data = await $scope.user.checkIfIdGroup(id);
            if (data.result == true) {
                $scope.userInfo = user;
                $scope.userInfo['result'] = data.result;
            } else {
                $scope.userInfo = user;
            }
            $scope.displayLightBox.readMail = true;
            template.open("readmail-lightbox", "mail-actions/user-info");
        };

        $scope.selectedMail = (mail) => {
            return $scope.state.current
                ? mail.id === $scope.state.current.id && $scope.showRightSide()
                : false;
        };
        $scope.displayMailOptions = () => {
            if($('plus#mailOptions').children('div.opener').hasClass('plus') && !$scope.stopPropagation){
                Zimbra.instance.currentFolder.deselectAll();
            }
            $scope.stopPropagation = false;

        };
        $scope.showConfirm = () => {
            if($scope.getFolder() === 'trash'){
                $scope.displayLightBox.folder = true;
                template.open('folder-lightbox','folders-templates/lightboxs/confirm-delete-mail');
            }else{
                $scope.removeSelection();
                const mailIds = [];
                $scope.zimbra.currentFolder.mails.selection.selected.forEach(({id}) => mailIds.push(id));
                $scope.zimbra.currentFolder.mails.selection.all = $scope.zimbra.currentFolder.mails.all.filter(({id}) => mailIds.indexOf(id) === -1);
                $scope.$apply();
            }
        };

        $scope.uploadAttachment = async (attachment) => {
            $scope.isFileLoading = true;
            $scope.$apply();
            const mail = $scope.state.newItem as Mail;
            if (!mail.id) {
                await Zimbra.instance.folders.draft.saveDraft(mail);
            }

            let newAttachment = new Attachment(attachment);
            mail.attachments.push(newAttachment);

            $scope.displayLightBox.attachment = false;
            $scope.$apply();

            await mail.postAttachment($scope, newAttachment);
        }

        $scope.showAttachmentLightbox = (): void => {
            $scope.displayLightBox.attachment = true;
        }

        $scope.cancelDelete = () => {
            $scope.displayLightBox.folder = true;
            Zimbra.instance.currentFolder.deselectAll();
        };
        $scope.searchText = () => $scope.showRightSide() ? $scope.display.searchSmall : $scope.display.searchLong;
    }]);
