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

import { ng, _ } from "entcore";

export const switchSearch = ng.directive("switchSearch", () => {
    return {
        restrict: "E",
        transclude: true,
        template: `
            <div ng-class="{'hide-search': hide}" class="flex-row align-center justify-between search-pagination">
                <a class="zero mobile-fat-mobile" ng-click='cancelSearch()'><i class="close horizontal-spacing"></i></a>
                <div class="cell">
                    <input class="twelve mobile-fat-mobile" type="text" ng-model="ngModel"
                    ng-keyup="$event.keyCode == 13 ? ngChange({words: ngModel}) : null"
                    i18n-placeholder="search.condition"/>
                    <i class="search mobile-fat-mobile flex-row align-center justify-center" ng-click="hide ? extend() : ngChange({words: ngModel});"></i>
                </div>
                <ng-transclude></ng-transclude>
            </div>
        `,

        scope: {
            ngModel: "=",
            ngChange: "&",
            cancel: "&"
        },

        link: (scope, element, attributes) => {
            scope.hide = true;

            scope.extend = () => {
                scope.hide = false;

                // element.find('.cell').addClass("twelve-mobile");
                // element.find('a').removeClass("zero-mobile");

                element.find("a").removeClass("zero");
                element.find(".cell").addClass("twelve");
            };

            scope.cancelSearch = () => {
                scope.hide = true;
                scope.ngModel = "";

                element.find(".cell").removeClass("twelve");
                element.find("a").addClass("zero");

                //element.find('.cell').removeClass("twelve-mobile");
                //element.find('a').addClass("zero-mobile");
                scope.cancel();
            };
        }
    };
});
