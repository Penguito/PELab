cmake_minimum_required(VERSION 3.22.1)

project(pelab_sdk LANGUAGES CXX)

add_library(
    pelab_sdk
    SHARED
    filter_pass.cpp
    gl_utils.cpp
    image_pass.cpp
    native_bridge.cpp
    native_renderer.cpp
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
    android
    EGL
    GLESv3
    jnigraphics  #bitmap
    log
)
