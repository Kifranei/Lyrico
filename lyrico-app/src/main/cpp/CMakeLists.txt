cmake_minimum_required(VERSION 3.22.1)

project("ebur128")

add_library(${CMAKE_PROJECT_NAME} SHARED
    ebur128.cpp
    ebur128/ebur128.c)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR})

set(COMPILE_FLAGS
    -Wno-cpp
)

# 如果是 x86 或 x86_64 架构，开启 SSE2 硬件 FTZ
if(CMAKE_ANDROID_ARCH_ABI MATCHES "^x86")
    list(APPEND COMPILE_FLAGS -msse2 -mfpmath=sse)
endif()

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
    ${COMPILE_FLAGS}
)

target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE
    android
    log)