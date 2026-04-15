cmake_minimum_required(VERSION 3.22.1)

project("ebur128")

add_library(${CMAKE_PROJECT_NAME} SHARED
    ebur128_jni.cpp
    ebur128/ebur128.c)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR})

if(${CMAKE_ANDROID_ARCH_ABI} MATCHES "^x86")
    # 如果是 x86 或 x86_64 架构，开启 SSE2 硬件 FTZ
    target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        -msse2
        -mfpmath=sse
    )
endif()

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log)