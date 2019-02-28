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