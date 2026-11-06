/** RN 0.88 exposes view registration through codegen, with no private warm-up. */
describe('public native component startup', () => {
    it('does not load the private ReactNativeViewConfigRegistry', () => {
        const registry = jest.fn(() => { throw new Error('private RN API must not be loaded'); });
        jest.doMock('react-native/Libraries/Renderer/shims/ReactNativeViewConfigRegistry', registry, { virtual: true });
        jest.isolateModules(() => {
            expect(require('react-native-geckoview').default).toBeDefined();
        });
        expect(registry).not.toHaveBeenCalled();
        jest.dontMock('react-native/Libraries/Renderer/shims/ReactNativeViewConfigRegistry');
    });
});
