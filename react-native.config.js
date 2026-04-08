// react-native.config.js
// Autolinking configuration for react-native-geckoview
// This tells React Native's CLI about our Fabric component descriptor,
// enabling automatic registration via the generated autolinking code.

module.exports = {
    dependency: {
        platforms: {
            android: {
                componentDescriptors: ['GeckoViewComponentDescriptor'],
                cmakeListsPath: 'src/main/jni/CMakeLists.txt',
            },
            // iOS support can be added here when implemented
            // ios: {
            //   componentDescriptors: ['GeckoViewComponentDescriptor'],
            // },
        },
    },
};
