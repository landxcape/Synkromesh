cmake_minimum_required(VERSION 3.22.1)
project(meshride_audio C CXX)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 17)

# Disable unnecessary targets in libopus
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "Build programs" FORCE)
set(OPUS_BUILD_TESTING OFF CACHE BOOL "Build tests" FORCE)

add_subdirectory(opus)

add_library(meshride_audio SHARED
    meshride_audio_jni.cpp
)

target_include_directories(meshride_audio PRIVATE
    opus/include
)

target_link_libraries(meshride_audio PRIVATE
    opus
    android
    log
)
