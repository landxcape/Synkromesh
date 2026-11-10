cmake_minimum_required(VERSION 3.22.1)
project(synkromesh_audio C CXX)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 17)

# 16 KB page-size support for Android 15 (Target SDK 35)
add_link_options("-Wl,-z,max-page-size=16384")

# Disable unnecessary targets in libopus
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "Build programs" FORCE)
set(OPUS_BUILD_TESTING OFF CACHE BOOL "Build tests" FORCE)

add_subdirectory(opus)

add_library(synkromesh_audio SHARED
    synkromesh_audio_jni.cpp
)

target_include_directories(synkromesh_audio PRIVATE
    opus/include
)

target_link_libraries(synkromesh_audio PRIVATE
    opus
    android
    log
)
