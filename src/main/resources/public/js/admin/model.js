function User(){}

function Group(){
    this.collection(User, {
        sync: function(){
            var that = this;
            http().get('/directory/user/admin/list', { groupId: that.model.id }).done(function(data){
                that.load(data);
                model.scope.$apply()
            })
        }
    })
}
Group.prototype.addLink = function(roleId){
    return http().put('/appregistry/authorize/group/' + this.id + '/role/' + roleId)
};
Group.prototype.removeLink = function(roleId){
    return http().delete('/appregistry/authorize/group/' + this.id + '/role/' + roleId)
};


function Role() {

}
Role.prototype.sync = function(callback){
    http().get('/zimbra/role').done(function (response) {
       var role = (angular.fromJson(response.role) || {});
        if(typeof callback === 'function'){
            callback(role || {});
        }
    })
};

function Structure(){
    this.collection(Group, {
        sync: function(hook){
            http().get('/appregistry/groups/roles', { structureId: this.model.id }).done(function(groups){
                this.load(groups);
                if(typeof hook === 'function')
                    hook()
            }.bind(this));
        }
    });
}

model.build = function(){
    this.makeModels([Structure, Group, User]);

    this.collection(Structure, {
        sync: function(){
            var that = this;
            http().get('/directory/structure/admin/list').done(function(data){
                that.load(data);
                _.forEach(that.all, function(struct){
                    struct.parents = _.filter(struct.parents, function(parent){
                        var parentMatch = _.findWhere(that.all, {id: parent.id});
                        if(parentMatch){
                            parentMatch.children = parentMatch.children ? parentMatch.children : [];
                            parentMatch.children.push(struct);
                            return true;
                        } else
                            return false;
                    });
                    if(struct.parents.length === 0)
                        delete struct.parents
                });
                if(model.scope)
                    model.scope.$apply()
            })
        }
    })

};
