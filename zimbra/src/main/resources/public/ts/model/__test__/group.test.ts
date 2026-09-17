import { http } from 'entcore-toolkit';
import {Group} from "../group";

jest.mock('entcore-toolkit', () => Object.assign({}, jest.requireActual('entcore-toolkit'), {
    http: { get: jest.fn(), post: jest.fn(), put: jest.fn(), delete: jest.fn(), postFile: jest.fn(), putFile: jest.fn() }
}));

describe('Group', () => {
    const group = new Group();
    test('returns data of group when retrieve is correctly called', done => {
        const data = {"id": "2", "name": "GroupB", "nbUsers": 1};
        (http.get as jest.Mock).mockResolvedValueOnce({ data });
        group.getGroup("3").then(() => {
            expect(http.get).toHaveBeenCalledWith(`/directory/group/3`, undefined);
            expect(group.id).toEqual(data.id);
            expect(group.name).toEqual(data.name);
            expect(group.nbUsers).toEqual(data.nbUsers);
            done();
        });
    });
});