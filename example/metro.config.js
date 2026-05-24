const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');
const path = require('path');

const extraNodeModules = {
  'react': path.resolve(__dirname, 'node_modules/react'),
  'react-native': path.resolve(__dirname, 'node_modules/react-native'),
  'react-native-geckoview': path.resolve(__dirname, '..')
};
const watchFolders = [path.resolve(__dirname, '..')];

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('metro-config').MetroConfig}
 */
const config = {
  resolver: {
    extraNodeModules,
    // The library is consumed via `link:../` and its own node_modules isn't
    // installed (single-package repo). Resolve the linked source's bare deps
    // (e.g. the `@babel/runtime` helpers babel injects into index.tsx) from
    // the example's node_modules instead of the library's empty tree.
    nodeModulesPaths: [path.resolve(__dirname, 'node_modules')],
    blockList: [
      // Exclude parent package's node_modules to prevent duplicate React
      new RegExp(`${path.resolve(__dirname, '..')}/node_modules/react/`),
      new RegExp(`${path.resolve(__dirname, '..')}/node_modules/react-native/`),
    ],
  },
  watchFolders,
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
