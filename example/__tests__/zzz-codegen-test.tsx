test('the public codegen spec exposes a native component', () => {
    const { default: NativeGeckoView } = require('react-native-geckoview/src/GeckoViewNativeComponent');
    expect(NativeGeckoView).toBeDefined();
});
