#!/bin/bash
# verify-fabric-build.sh
# Quick verification script to check if Fabric components are properly built

set -e

echo "🔍 Verifying Fabric Build Configuration..."
echo ""

# Check if OnLoad.cpp exists
echo "✓ Checking for OnLoad.cpp..."
if [ -f "android/src/main/jni/OnLoad.cpp" ]; then
  echo "  ✅ OnLoad.cpp found"
else
  echo "  ❌ OnLoad.cpp missing - this will cause registration failures!"
  exit 1
fi

# Check if CMakeLists.txt globs local sources
echo "✓ Checking CMakeLists.txt for local source globbing..."
if grep -q "gecko_local_SRCS" android/src/main/jni/CMakeLists.txt; then
  echo "  ✅ Local source globbing configured"
else
  echo "  ❌ CMakeLists.txt does not glob local sources!"
  exit 1
fi

# Check for 16KB page size flag
echo "✓ Checking for 16KB page-size linker flag..."
if grep -q "max-page-size=16384" android/src/main/jni/CMakeLists.txt; then
  echo "  ✅ 16KB page-size flag present"
else
  echo "  ⚠️  Missing 16KB page-size flag (Android 15+ compatibility)"
fi

# Check example app for newArchEnabled
echo "✓ Checking example app Fabric configuration..."
if [ -f "example/android/gradle.properties" ]; then
  if grep -q "newArchEnabled=true" example/android/gradle.properties; then
    echo "  ✅ Fabric enabled in example app"
  else
    echo "  ⚠️  Fabric not enabled in example app (set newArchEnabled=true)"
  fi
else
  echo "  ⚠️  Example gradle.properties not found"
fi

echo ""
echo "📦 Build Recommendations:"
echo "  1. Clean build: cd example/android && ./gradlew clean"
echo "  2. Rebuild: ./gradlew :react-native-geckoview:assembleDebug"
echo "  3. Check for OnLoad.cpp compilation in build logs"
echo "  4. Verify libreact_codegen_geckoviewspec.so is created"
echo ""
echo "✅ Verification complete!"
