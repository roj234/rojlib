cmake_minimum_required(VERSION 3.23)
project(cpp LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_EXE_LINKER_FLAGS "-static")
include_directories("C:\\Program Files\\Java\\jdk1.8.0_201\\include")
include_directories("C:\\Program Files\\Java\\jdk1.8.0_201\\include\\win32")
add_library(cpp SHARED library.cpp)

install(
        TARGETS cpp
        RUNTIME DESTINATION ${PROJECT_BINARY_DIR}/bin
        LIBRARY DESTINATION ${PROJECT_BINARY_DIR}/bin
)