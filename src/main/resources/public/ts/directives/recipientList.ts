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

import { ng, _, model } from "entcore";
import {User } from "../model";
import http from 'axios';


/**
 * @description Displays chips of items list with a search input and dropDown options. If more than
 * 4 items are in the list, only 2 of them will be shown.
 * @param ngModel The list of items to display in chips.
 * @param ngChange Called when the items list changed.
 * @param updateFoundItems (search, model, founds) Called to update the dropDown content according to
 * the search input.
 * @example
 * <recipient-list
        ng-model="<model>"
        ng-change="<function>()"
        update-found-items="<function>(search, model, founds)">
    </recipient-list>
 */
export const recipientList = ng.directive("recipientList", () => {
    return {
        restrict: "E",
        template: `
            <div class="twelve flex-row align-center" ng-click="unfoldChip()">
                <contact-chip class="block relative removable" 
                    ng-model="item"
                    action="deleteItem(item)"
                    ng-repeat="item in ngModel | limitTo : (needChipDisplay() ? 2 : ngModel.length)">
                </contact-chip>
                <label class="chip selected" ng-if="needChipDisplay()" ng-click="giveFocus()">
                    <span class="cell">... <i18n>chip.more1</i18n> [[ngModel.length - 2]] <i18n>chip.more2</i18n></span>
                </label>
                <img skin-src="/img/illustrations/loading.gif" width="30px" heigh="30px" ng-if="loading"/>
                <form class="input-help" ng-submit="update(true)">
                    <input class="chip-input right-magnet" type="text" ng-model="searchText" ng-change="update()" autocomplete="off" ng-class="{ move: searchText.length > 0 }" 
                    i18n-placeholder="[[restriction ? 'share.search.help' : 'share.search.placeholder' ]]"
                    ng-keyup="addExternalItem($event)"
                    />
                    
                </form>
                <drop-down
                    options="itemsFound"
                    ng-change="addItem()"
                    on-close="clearSearch()"
                    ng-model="currentReceiver">
                </drop-down>
            </div>
        `,

        scope: {
            ngModel: "=",
            ngChange: "&",
            restriction: "=",
            updateFoundItems: "&"
        },

        link: (scope, element, attributes) => {
            var firstFocus = true;
            var minWidth = 0;
            scope.focused = false;
            scope.loading = false;
            scope.searchText = "";
            scope.itemsFound = [];
            scope.currentReceiver = "undefined";
            scope.addedFavorites = [];

            element.find("input").on("focus", () => {
                if (firstFocus) firstFocus = false;
                scope.focused = true;
                element.find("div").addClass("focus");
                element.find("form").width(minWidth);
            });

            element.find("input").on("blur", () => {
                scope.focused = false;
                element.find("div").removeClass("focus");
                setTimeout(function() {
                    if (!scope.focused) element.find("form").width(0);
                }, 250);
            });

            element.find("input").on("keydown", function(e) {
                if (
                    e.keyCode === 8 &&
                    scope.searchText &&
                    scope.searchText.length === 0
                ) {
                    // BackSpace
                    var nb = scope.ngModel.length;
                    if (nb > 0) scope.deleteItem(scope.ngModel[nb - 1]);
                }
            });

            //prevent blur when look for more users in dropDown
            element
                .parents()
                .find(".display-more")
                .on("click", () => {
                    if (!firstFocus) {
                        scope.giveFocus();
                    }
                });

            scope.needChipDisplay = () => {
                return (
                    !scope.focused &&
                    typeof scope.ngModel !== "undefined" &&
                    scope.ngModel.length > 3
                );
            };

            scope.update = async (force?: boolean) => {
                if (force) {
                    await scope.doSearch();
                    scope.$apply('itemsFound');
                } else {
                    if (
                        (scope.restriction && scope.searchText.length < 3) ||
                        scope.searchText.length < 1
                    ) {
                        scope.itemsFound.splice(0, scope.itemsFound.length);
                    } else {
                        await scope.doSearch();
                        scope.$apply('itemsFound');
                    }
                }
            };

            scope.giveFocus = () => {
                if (!scope.focus) element.find("input").focus();
            };

            scope.unfoldChip = () => {
                if (!firstFocus && scope.needChipDisplay()) {
                    scope.giveFocus();
                }
            };


            scope.addOneItem = (item) => {
                for (var i = 0, l = scope.ngModel.length; i < l; i++) {
                    if (scope.ngModel[i].id === item.id) {
                        return false;
                    }
                }
                scope.ngModel.push(item);
                return true;
            };
            scope.addExternalItem = ( event)=>{
                let keyCode = event.which || event.keyCode;
                let key = event.key;
                let myUser =new User(scope.searchText, scope.searchText);
                if((keyCode === 13 || key == ";" ) && myUser.isAMail() && scope.searchText &&
                model.me.hasWorkflow('fr.openent.zimbra.controllers.ZimbraController|zimbraOutside')){
                    myUser.cleanMail();
                    scope.addItem((myUser));
                    scope.clearSearch();
                    scope.$apply();
                }
            };

            scope.addItem = async (mail?:User) => {
                scope.focused = true;
                element.find('input').focus();
                if (!scope.ngModel) {
                    scope.ngModel = [];
                }
                if(mail && _.findWhere(scope.ngModel, {id: mail.id}) == undefined ){
                    scope.ngModel.push(mail);
                }
                else if (scope.currentReceiver.type === 'sharebookmark') {
                    scope.loading = true;
                    var response = await http.get('/directory/sharebookmark/' + scope.currentReceiver.id);
                    response.data.groups.forEach(item => {
                        scope.addOneItem(item);
                    });
                    response.data.users.forEach(item => {
                        scope.addOneItem(item);
                    });
                    scope.addedFavorites.push(scope.currentReceiver);
                    scope.loading = false;
                } else {
                    scope.ngModel.push(scope.currentReceiver);
                }
                setTimeout(function(){
                    scope.itemsFound.splice(scope.itemsFound.indexOf(scope.currentReceiver), 1);
                    scope.$apply('itemsFound');
                }, 0);
                scope.$apply("ngModel");
                scope.$eval(scope.ngChange);
            };

            scope.deleteItem = item => {
                scope.ngModel = _.reject(scope.ngModel, function(i) {
                    return i === item;
                });
                scope.$apply("ngModel");
                scope.$eval(scope.ngChange);
                if (scope.itemsFound.length > 0)
                    scope.doSearch();
            };

            scope.clearSearch = () => {
                scope.itemsFound = [];
                scope.searchText = "";
                scope.addedFavorites = [];
            };

            scope.doSearch = async () => {
                await scope.updateFoundItems({
                    search: scope.searchText,
                    model: scope.ngModel,
                    founds: scope.itemsFound
                });
                scope.itemsFound = _.reject(scope.itemsFound, function (element) {
                    return _.findWhere(scope.addedFavorites, { id: element.id });
                });
            };

            // Focus when items list changes
            scope.$watchCollection("ngModel", function(newValue) {
                if (!firstFocus) {
                    scope.giveFocus();
                }
            });

            // Make the input width be the label help infos width
            setTimeout(function() {
                minWidth = element.find("form label").width();
            }, 0);
        }
    };
});
