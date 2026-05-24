/**
 * Shared ESLint config for the library root and the example app.
 * Example extends this via `./example/.eslintrc.js`.
 */
module.exports = {
    root: true,
    extends: ['@react-native/eslint-config'],
    parserOptions: {
        ecmaVersion: 2022,
        sourceType: 'module',
    },
    ignorePatterns: [
        'lib/',
        'node_modules/',
        'example/android/',
        'example/ios/',
        'example/node_modules/',
    ],
    rules: {
        'react-native/no-inline-styles': 'off',
        'no-console': 'off',
    },
};
