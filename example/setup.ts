// Jest setup: register the shared react-native mock once for every test file.
// Previously each test file carried its own one-line boilerplate; centralizing
// here keeps the mock surface in a single place.

jest.mock('react-native', () => {
    const { setupReactNativeMock } = require('./__tests__/testUtils');
    return setupReactNativeMock();
});
