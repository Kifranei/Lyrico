cmake_minimum_required(VERSION 3.22.1)
project("tagJNI")

get_filename_component(PROJECT_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../.." ABSOLUTE)
message(STATUS "Project root: ${PROJECT_ROOT}")

# -----------------------------
# TagLib 预构建路径
# -----------------------------
set(TAGLIB_PKG_DIR "${PROJECT_ROOT}/pkg/taglib/${ANDROID_ABI}")
set(TAGLIB_LIB_DIR "${TAGLIB_PKG_DIR}/lib")
set(TAGLIB_INCLUDE_DIR "${TAGLIB_PKG_DIR}/include")
set(TAGLIB_FILE_NAME "libtag.a")
set(TAGLIB_FILE_PATH "${TAGLIB_LIB_DIR}/${TAGLIB_FILE_NAME}")

message(STATUS "TagLib lib path: ${TAGLIB_FILE_PATH}")
message(STATUS "TagLib include path: ${TAGLIB_INCLUDE_DIR}")

# -----------------------------
# 导入静态库
# -----------------------------
add_library(taglib STATIC IMPORTED)
set_target_properties(taglib PROPERTIES
    IMPORTED_LOCATION "${TAGLIB_FILE_PATH}"                # 默认配置
    IMPORTED_LOCATION_RELEASE "${TAGLIB_FILE_PATH}"        # Release 配置
    IMPORTED_LOCATION_DEBUG "${TAGLIB_FILE_PATH}"          # Debug 配置
    INTERFACE_INCLUDE_DIRECTORIES "${TAGLIB_INCLUDE_DIR}"
)

add_library(${PROJECT_NAME} SHARED
    taglib_jni.cpp
    JTagMap.cpp
    JMetadataBuilder.cpp
    JClassRef.cpp
    JObjectRef.cpp
    JStringRef.cpp
    JByteArrayRef.cpp
)

target_link_options(${PROJECT_NAME} PRIVATE
    "-Wl,--exclude-libs,ALL,-z,max-page-size=16384"
)

# -----------------------------
# 链接库
# -----------------------------
target_link_libraries(${PROJECT_NAME}
    PRIVATE android
    PRIVATE log
    PRIVATE taglib
)