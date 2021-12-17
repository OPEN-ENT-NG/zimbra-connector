
export class SnipletUtil {

    /**
     * get updated source assigned to your sniplet's template source props
     *
     * @param templateName name of your sniplet's template {string}
     */
    static getUpdatedSource(templateName: string): any {
        return JSON.parse(document
            .querySelector(`sniplet[template=${templateName}]`)
            .getAttribute("source")
        )
    }
}