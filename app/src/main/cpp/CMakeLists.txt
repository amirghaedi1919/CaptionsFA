cmake_minimum_required(VERSION 3.22.1)

project("captions_whisper")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

FetchContent_Declare(
    whispercpp
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG master
    GIT_SHALLOW TRUE
)

FetchContent_MakeAvailable(whispercpp)

add_library(
    whisper_jni
    SHARED
    whisper_jni.cpp
)

target_include_directories(
    whisper_jni
    PRIVATE
    ${whispercpp_SOURCE_DIR}/include
    ${whispercpp_SOURCE_DIR}/ggml/include
)

find_library(
    log-lib
    log
)

target_link_libraries(
    whisper_jni
    whisper
    android
    ${log-lib}
)

target_compile_options(
    whisper_jni
    PRIVATE
    -O3
    -fvisibility=hidden
)
