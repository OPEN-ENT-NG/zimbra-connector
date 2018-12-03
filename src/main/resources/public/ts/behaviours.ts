import { Behaviours, http, notify, _ } from "entcore";

Behaviours.register("zimbra", {
    rights: {
        workflow: {
            draft: "fr.openent.zimbra.controllers.ZimbraController|createDraft",
            read: "fr.openent.zimbra.controllers.ZimbraController|view",
            expert: "fr.openent.zimbra.controllers.ZimbraController|preauth"
        }
    },
    sniplets: {
        ml: {
            title: "sniplet.ml.title",
            description: "sniplet.ml.description",
            controller: {
                init: function() {
                    this.message = {};
                },
                initSource: function() {
                    this.setSnipletSource({});
                },
                send: function() {
                    this.message.to = _.map(
                        this.snipletResource.shared,
                        function(shared) {
                            return shared.userId || shared.groupId;
                        }
                    );
                    this.message.to.push(this.snipletResource.owner.userId);
                    http()
                        .postJson("/zimbra/send", this.message)
                        .done(function() {
                            notify.info("ml.sent");
                        })
                        .e401(function() {});
                    this.message = {};
                }
            }
        }
    }
});
