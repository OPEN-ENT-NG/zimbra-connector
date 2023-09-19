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

export const ngBottomScroll = ng.directive('ngBottomScroll', function () {
    return {
        restrict: 'A',
        link: function (scope, element, attrs) {
            let currentScrollHeight = 0;

            const checkScrollPosition = async () => {
                let MyElement = element[0];
                let latestHeightBottom = 50;
                let scrollHeight = MyElement.scrollHeight;
                let scrollPos = Math.floor(MyElement.offsetHeight + MyElement.scrollTop);
                let isBottom = scrollHeight - latestHeightBottom < scrollPos;
                if (isBottom && currentScrollHeight < scrollHeight) {
                    let oldScrollTop = MyElement.scrollTop;
                    await scope.$eval(attrs.ngBottomScroll);
                    currentScrollHeight = scrollHeight;
                    if(Math.floor(MyElement.offsetHeight + MyElement.scrollTop) ==  MyElement.scrollHeight){// if adding element did not scroll up then we do it
                        MyElement.scrollTop = oldScrollTop;
                    }
                }
            };

            element.bind('scroll', checkScrollPosition);
        }
    };
});

