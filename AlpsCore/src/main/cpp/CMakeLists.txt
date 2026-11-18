cmake_minimum_required(VERSION 3.22.1)

project("alpsnative")

set(DLB_ALPS_NATIVE_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../repos/dlb_alps_native)

# Consume the alps-native project through its own top-level script
set(BUILD_CLI OFF)
set(BUILD_UNIT_TESTS OFF)
set(BUILD_DOCUMENTATION OFF)
# Link the supporting modules statically into the single shared library we ship.
set(BUILD_SHARED_LIBS OFF)
set(BUILD_SHARED_ALPS_LIB ON)
add_subdirectory(${DLB_ALPS_NATIVE_ROOT} ${CMAKE_CURRENT_BINARY_DIR}/dlb_alps_native)

add_library(${CMAKE_PROJECT_NAME} SHARED
        alpsnative.cpp
)

target_compile_options(${CMAKE_PROJECT_NAME}
        PRIVATE
        -Wall -Wextra -Wno-unused-parameter
)

target_link_libraries(${CMAKE_PROJECT_NAME}
        PRIVATE
        dlb_alps_native
        log)
