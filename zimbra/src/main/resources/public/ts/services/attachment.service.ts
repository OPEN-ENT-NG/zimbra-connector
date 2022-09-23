import {ng} from 'entcore';
import http, {AxiosPromise} from 'axios';
import {Attachment, Mail} from "../model";

export interface IAttachmentService {
    deleteAttachment(attachment : Attachment, mail : Mail): AxiosPromise;
    downloadAttachmentInWorkspace(attachment : Attachment, mail : Mail): AxiosPromise;
    postAttachmentFromWorkspace(attachment : Attachment, mail : Mail): AxiosPromise;
    postAttachmentFromComputer(attachment : Attachment, mail : Mail): AxiosPromise;
}

export const attachmentService: IAttachmentService = {
    /**
     * Delete Attachment
     */
    deleteAttachment: (attachment : Attachment, mail : Mail): AxiosPromise =>
        http.delete(`message/${mail.id}/attachment/${attachment.id}`),

    /**
     * download Attachment in workspace
     */
    downloadAttachmentInWorkspace: (attachment : Attachment, mail : Mail): AxiosPromise =>
        http.get(`message/${mail.id}/attachment/${attachment.id}/workspace`),

    /**
     * post Attachment from workspace
     */
    postAttachmentFromWorkspace: (attachment : Attachment, mail : Mail): AxiosPromise =>
        http.post(`message/${mail.id}/upload/${attachment.id}`),

    /**
     * post Attachment from computer
     */
    postAttachmentFromComputer: (attachment : Attachment, mail : Mail): AxiosPromise =>
        http.post(
            `message/${mail.id}/attachment`,
            attachment.file,
            {
                headers: {
                    "Content-Disposition":
                        'attachment; filename="' +
                        attachment.file.name.replace(
                            /[\u00A0-\u9999<>\&]/gim,
                            function (i) {
                                return "&#" + i.charCodeAt(0) + ";";
                            }
                        ) +
                        '"'
                }
            }),
};

export const AttachmentService = ng.service('AttachmentService', (): IAttachmentService => attachmentService);