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

import http from "axios";

import { Mix } from "entcore-toolkit";
import { ViewMode} from "./constantes/index";

export class Preference {
    modeExpert: object;
    viewMode: ViewMode;
    constructor() {
        this.sync();
    }

    async sync() {
        let {data } = await http.get('/userbook/preference/zimbra');
        let pref = JSON.parse( data.preference);
        Mix.extend(this, {
            modeExpert: pref.modeExpert,
            viewMode : pref.viewMode ? pref.viewMode : ViewMode.LIST
            });
        return this;
    }
    async save(){
        let {status} =  await http.put('/userbook/preference/zimbra', { 'modeExpert': this.modeExpert, 'viewMode': this.viewMode});
        return status
    }

    async switchViewMode(mode?:string) {
        if(ViewMode[mode]){
            this.viewMode = ViewMode[mode];
        }else{
            this.isColumn() ? this.viewMode = ViewMode.LIST :  this.viewMode = ViewMode.COLUMN;
        }
       return  await this.save();

    }
    isList()  {
        return this.viewMode == ViewMode.LIST;
    };
    isColumn() {
        return this.viewMode == ViewMode.COLUMN;
    };
}