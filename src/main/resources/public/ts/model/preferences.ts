import http from "axios";

import { Mix } from "entcore-toolkit";

export class Preference {
    modeExpert: object;

    constructor() {
        this.sync();
    }

    async sync() {
        let {data } = await http.get('/userbook/preference/zimbra');
        Mix.extend(this, JSON.parse( data.preference) );
        return this;
    }
    async save(){
        let {status} =  await http.put('/userbook/preference/zimbra', { 'modeExpert': this.modeExpert});
        return status
    }

}