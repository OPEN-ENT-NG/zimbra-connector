import http from "axios";

export class Group {
        id: String;
        name: String;
        nbUsers: Number;
        isGroup: Boolean;

        async getGroup(groupId:String):Promise<void>{
            try{
                const {data} = await http.get(`/directory/group/${groupId}`);
                this.name = data.name;
                this.nbUsers = data.nbUsers;
                this.id = data.id;
            } catch (error) {
                return;
            }
        }
}