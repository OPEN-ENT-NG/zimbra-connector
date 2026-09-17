module.exports = {
    "transform": {
        ".(ts|tsx)": "<rootDir>/node_modules/ts-jest/preprocessor.js"
    },
    "testRegex": "(/__tests__/.*|\\.(test|spec))\\.(ts|tsx|js)$",
    "moduleFileExtensions": [
        "ts",
        "tsx",
        "js",
        "json"
    ],
    "testPathIgnorePatterns": [
        "/node_modules/",
        "<rootDir>/apizimbra/build/",
        "<rootDir>/apizimbra/out/",
        "<rootDir>/zimbra/build/",
        "<rootDir>/zimbra/out/"
    ],
    "verbose": true,
    "testURL": "http://localhost/",
    "coverageDirectory": "coverage/front",
    "coverageReporters": [
        "text",
        "cobertura"
    ],
    "moduleNameMapper": {
        "^@zimbra(.*)$": "<rootDir>/zimbra/src/main/resources/ts$1"
    }
};
