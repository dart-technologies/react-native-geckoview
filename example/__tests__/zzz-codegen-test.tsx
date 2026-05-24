test('codegen path', () => {
  let threw = false;
  let val: any;
  try {
    // mirror index.tsx's require
    val = require('react-native-geckoview/src/GeckoViewNativeComponent');
  } catch (e) {
    threw = true;
  }
  console.log('CODEGEN_THREW=' + threw + ' default=' + (val && typeof val.default));
  expect(true).toBe(true);
});
