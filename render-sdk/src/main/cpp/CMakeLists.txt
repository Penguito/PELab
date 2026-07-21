cmake_minimum_required(VERSION 3.22.1)

project(pelab_sdk LANGUAGES CXX)

add_library(
    pelab_sdk
    SHARED
    native_bridge.cpp
)

target_compile_features(
    pelab_sdk
    PRIVATE
    cxx_std_17
)
target_compile_options(
    pelab_sdk
    PRIVATE
    -Wall
    -Wextra
    -Werror=return-type
)

target_link_libraries(
    pelab_sdk
    PRIVATE
    EGL
    GLESv3
    log
)
