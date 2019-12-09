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

import {idiom as lang, _, model, notify} from "entcore";

import { Mix, Selection, Selectable, Eventer } from "entcore-toolkit";

import http from "axios";

export class User implements Selectable {
    displayName: string;
    name: string;
    profile: string;
    id: string;
    selected: boolean;
    isGroup: boolean;
    email: string;
    result: boolean;

    constructor(id?: string, displayName?: string) {
        this.displayName = displayName;
        this.id = id;
    }

    toString() {
        return (
            (this.displayName || "") +
            (this.name || "") +
            (this.profile ? " (" + lang.translate(this.profile) + ")" : "")
        );
    }

    async findData(): Promise<boolean> {
        let that = this;
        const response = await http.get("/userbook/api/person?id=" + this.id);
        const userData = response.data;
        if (!userData.result[0])
            // If group
            return true;
        // If deleted ??
        Mix.extend(this, {
            id: that.id,
            displayName: userData.result[0].displayName
        });

        return true;
    }
    async findMe(): Promise<boolean> {
        let {data} = await http.get("/userbook/api/person");
        if (data && data.result[0])
            Mix.extend(this, data.result[0]);
        return true;
    }

    mapUser(displayNames, id) {
        return _.map(
            _.filter(displayNames, function(user) {
                return user[0] === id;
            }),
            function(user) {
                return new User(user[0], user[1]);
            }
        )[0];
    }

    isMe() {
        return model.me.userId == this.id;
    }

    isAGroup() {
        if (!this.id) return false;
        return this.id.length < 36;
    }

    checkIfGroup(){
        if (!this.id) return false;
        return this.result == true;
    }

    isAMail() {
        if (!this.id) return false;
        return this.id.includes("@");
    }

    cleanMail() {
        this.id = this.id.replace(";","");
        this.displayName = this.displayName.replace(";","");
    }

    async checkIfIdGroup(id) {
        let {data} = await http.get("/zimbra/idToCheck/" + id);
        return data;
    }
}

export class Users {
    eventer = new Eventer();
    searchCachedMap = {};

    async sync(search: string) {
        let newArr = [];
        let response = await http.get('/directory/sharebookmark/all');
        let bookmarks = _.map(response.data, function(bookmark) {
            bookmark.type = 'sharebookmark';
            return bookmark;
        });
        newArr = Mix.castArrayAs(User, bookmarks);
        response = await http.get("/zimbra/visible?search=" + search);
        response.data.groups.forEach(group => {
            group.isGroup = true;
            newArr.push(Mix.castAs(User, group));
        });

        newArr = newArr.concat(Mix.castArrayAs(User, response.data.users));
        return newArr;
    }

    async findUser(search, include, exclude): Promise<User[]> {
        const startText = search.substr(0, 10);
        if (!this.searchCachedMap[startText]) {
            this.searchCachedMap[startText] = [];
            this.searchCachedMap[startText] = await this.sync(startText);
        }
        var searchTerm = lang.removeAccents(search).toLowerCase();
        var found = _.filter(
            this.searchCachedMap[startText]
                .filter(function(user) {
                    var includeUser = _.findWhere(include, { id: user.id });
                    if (includeUser !== undefined)
                        includeUser.profile = user.profile;
                    return includeUser === undefined;
                })
                .concat(include),
            function(user) {
                var testDisplayName = "",
                    testNameReversed = "";
                if (user.displayName) {
                    testDisplayName = lang
                        .removeAccents(user.displayName)
                        .toLowerCase();
                    testNameReversed = lang
                        .removeAccents(
                            user.displayName.split(" ")[1] +
                                " " +
                                user.displayName.split(" ")[0]
                        )
                        .toLowerCase();
                }
                var testName = "";
                if (user.name) {
                    testName = lang.removeAccents(user.name).toLowerCase();
                }

                return (
                    testDisplayName.indexOf(searchTerm) !== -1 ||
                    testNameReversed.indexOf(searchTerm) !== -1 ||
                    testName.indexOf(searchTerm) !== -1
                );
            }
        );
        return _.reject(found, function(element) {
            return _.findWhere(exclude, { id: element.id });
        });
    }

    async getUsersByFavoriteId(favoriteId:String):Promise<Array<User>>{
        try{
            let usersArray:Array<User> = [];
            const {data} = await http.get(`/directory/sharebookmark/${favoriteId}`);
            data.users.forEach( (user:User):void => {
                usersArray.push(Mix.castAs(User, user));
            });
            return usersArray;
        } catch (error) {
           return [];
        }
    }
}
