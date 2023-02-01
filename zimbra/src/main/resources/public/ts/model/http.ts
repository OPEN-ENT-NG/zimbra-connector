import {idiom, toasts} from 'entcore';
import http, {AxiosRequestConfig} from 'axios';
import {SERVICE} from './constantes/codeZimbra';

class Http {
    private static parseError(e) {
        try {
            if (JSON.parse(e.response.data.error).code === SERVICE.CIRCUIT_BREAKER) {
                toasts.info(`${idiom.translate('zimbra.view.error.title')} ${idiom.translate('zimbra.view.error.footer')}`);
            }
            return e;
        } finally {
            throw e;
        }
    }
    async get(url: string, config?: AxiosRequestConfig) {
        try {
            return http.get(url, config);
        } catch (e) {
           Http.parseError(e);
        }
    }
    async delete(url: string, config?: AxiosRequestConfig) {
        try {
            return http.delete(url, config);
        } catch (e) {
            Http.parseError(e);
        }
    }
    async post(url: string, data?: any, config?: AxiosRequestConfig) {
        try {
            return http.post(url, data, config);
        } catch (e) {
            Http.parseError(e);
        }
    }
    async put(url: string, data?: any, config?: AxiosRequestConfig) {
        try {
            return http.put(url, data, config);
        } catch (e) {
            Http.parseError(e);
        }
    }
}

const httpCB = new Http();

export default httpCB;