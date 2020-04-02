import {toasts, idiom} from 'entcore';
import http from 'axios';
import {SERVICE} from './constantes/codeZimbra';

class Http {
    private static parseError(e) {
        try {
            const {code} = JSON.parse(e.response.data.error);
            if (code && code === SERVICE.CIRCUIT_BREAKER) {
                toasts.info(`${idiom.translate('zimbra.view.error.title')} ${idiom.translate('zimbra.view.error.footer')}`);
            }
            return e;
        } finally {
            throw e;
        }
    }
    async get(url, config?) {
        try {
            const response = await http.get(url, config);
            return response;
        } catch (e) {
           Http.parseError(e);
        }
    }
    async delete(url, config?) {
        try {
            const response =  await http.delete(url, config);
            return response;
        } catch (e) {
            Http.parseError(e);
        }
    }
    async post(url, data?, config?) {
        try {
            const response =  await http.post(url, data, config);
            return response;
        } catch (e) {
            Http.parseError(e);
        }
    }
    async put(url, data?, config?) {
        try {
            const response =  await http.put(url, data, config);
            return response;
        } catch (e) {
            Http.parseError(e);
        }
    }
}

const httpCB = new Http();

export default httpCB;