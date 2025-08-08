cmake_minimum_required(VERSION 3.23)
project(OmniJni)

include_directories("$ENV{JAVA_HOME}\\include")
include_directories("$ENV{JAVA_HOME}\\include\\win32")
include_directories("libdivsufsort/include")

if(MSVC)
    add_compile_options("/O2" "/arch:SSE4" "/D__SSE4_1__" "/D__AES__")
else()
    add_compile_options("-O3" "-maes" "-msse4" "-pipe" "-nodefaultlibs" "-flto=full" "-ffunction-sections" "-fdata-sections")
    add_link_options("-s" "-O3")
endif()

add_library(OmniJni SHARED main.cpp)
target_link_libraries(OmniJni PRIVATE ${CMAKE_SOURCE_DIR}/libdivsufsort/lib/libdivsufsort.a PRIVATE ws2_32 PRIVATE ole32)

install(
        TARGETS OmniJni
        RUNTIME DESTINATION ${PROJECT_BINARY_DIR}/bin
        LIBRARY DESTINATION ${PROJECT_BINARY_DIR}/bin
)