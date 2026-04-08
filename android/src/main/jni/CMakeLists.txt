cmake_minimum_required(VERSION 3.13)
project(react_codegen_GeckoViewSpec)
set(CMAKE_VERBOSE_MAKEFILE ON)

# Find ReactAndroid from the host application's node_modules
# This is needed because we're building as a library, not the main app
set(REACT_ANDROID_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../node_modules/react-native/ReactAndroid")
if(NOT EXISTS "${REACT_ANDROID_DIR}")
    # Fallback: look in the example app's node_modules
    set(REACT_ANDROID_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../example/node_modules/react-native/ReactAndroid")
endif()
set(CMAKE_PREFIX_PATH "${REACT_ANDROID_DIR}/cmake" ${CMAKE_PREFIX_PATH})

set(GECKO_ANDROID_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../..)
set(GECKO_GENERATED_DIR ${GECKO_ANDROID_DIR}/build/generated)
set(GECKO_GENERATED_JNI_DIR ${GECKO_GENERATED_DIR}/source/codegen/jni)
set(GECKO_GENERATED_COMPONENT_DIR ${GECKO_GENERATED_JNI_DIR}/react/renderer/components/GeckoViewSpec)


# Glob generated codegen sources
file(GLOB gecko_codegen_SRCS CONFIGURE_DEPENDS
    ${GECKO_GENERATED_JNI_DIR}/*.cpp
    ${GECKO_GENERATED_COMPONENT_DIR}/*.cpp
)

# Glob handwritten local sources (OnLoad.cpp, etc.)
file(GLOB gecko_local_SRCS CONFIGURE_DEPENDS
    ${CMAKE_CURRENT_SOURCE_DIR}/*.cpp
)

add_library(
  react_codegen_GeckoViewSpec
  SHARED
  ${gecko_codegen_SRCS}
  ${gecko_local_SRCS}
)

target_include_directories(
  react_codegen_GeckoViewSpec
  PUBLIC
  ${GECKO_GENERATED_JNI_DIR}
  ${GECKO_GENERATED_COMPONENT_DIR}
)

# Android 15+ requires 16KB page-size support
# https://developer.android.com/guide/practices/page-sizes#cmake
target_link_options(
  react_codegen_GeckoViewSpec
  PRIVATE
  "-Wl,-z,max-page-size=16384"
)


find_package(ReactAndroid REQUIRED CONFIG)

if(ReactAndroid_VERSION_MINOR GREATER_EQUAL 76)
  target_link_libraries(
    react_codegen_GeckoViewSpec
    ReactAndroid::reactnative
    ReactAndroid::jsi
  )
else()
  target_link_libraries(
    react_codegen_GeckoViewSpec
    folly_runtime
    glog
    jsi
    react_codegen_rncore
    react_debug
    react_nativemodule_core
    react_render_core
    react_render_debug
    react_render_graphics
    react_render_mapbuffer
    rrc_view
    react_render_componentregistry
    turbomodulejsijni
    yoga
    react_render_imagemanager
    react_utils
  )
endif()

find_package(fbjni REQUIRED CONFIG)

target_link_libraries(
  react_codegen_GeckoViewSpec
  fbjni
)

if(ReactAndroid_VERSION_MINOR GREATER_EQUAL 80)
  target_compile_reactnative_options(react_codegen_GeckoViewSpec PUBLIC)
else()
  target_compile_options(
    react_codegen_GeckoViewSpec
    PRIVATE
    -fexceptions
    -frtti
    -std=c++17
  )
endif()

target_compile_options(
  react_codegen_GeckoViewSpec
  PRIVATE
  -Wno-gnu-zero-variadic-macro-arguments
  -Wno-dollar-in-identifier-extension
)

target_compile_definitions(
  react_codegen_GeckoViewSpec
  PRIVATE
  REACT_NATIVE_MINOR_VERSION=${ReactAndroid_VERSION_MINOR}
)
