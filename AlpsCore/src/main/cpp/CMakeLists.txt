cmake_minimum_required(VERSION 3.22.1)

project("alpsnative")

add_library(dlb_alps_native
        SHARED
        IMPORTED)

set_target_properties(dlb_alps_native
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/${ANDROID_ABI}/libdlb_alps_native.so)

add_library(${CMAKE_PROJECT_NAME} SHARED
        alpsnative.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
        PRIVATE
        dlb_alps_native
        log)