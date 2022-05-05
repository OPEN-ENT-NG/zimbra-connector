import axios from 'axios';
import MockAdapter from "axios-mock-adapter";
import {Group} from "../group";

describe('Group', () => {
    const group = new Group();
    test('returns data of group when retrieve is correctly called', done => {
        const mock = new MockAdapter(axios);
        const data = {"id": "2", "name": "GroupB", "nbUsers": 1};
        mock.onGet(`/directory/group/3`).reply(200, data);
        group.getGroup("3").then(() => {
            expect(group.id).toEqual(data.id);
            expect(group.name).toEqual(data.name);
            expect(group.nbUsers).toEqual(data.nbUsers);
            done();
        });
    });
});