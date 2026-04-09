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
    blockList: [
      // Exclude parent package's node_modules to prevent duplicate React
      new RegExp(`${path.resolve(__dirname, '..')}/node_modules/react/`),
      new RegExp(`${path.resolve(__dirname, '..')}/node_modules/react-native/`),
    ],
  },
  watchFolders,
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
