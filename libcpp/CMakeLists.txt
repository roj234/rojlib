cmake_minimum_required(VERSION 3.23)
project(cpp LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 14)

include_directories("$ENV{JAVA_HOME}\\include")
include_directories("$ENV{JAVA_HOME}\\include\\win32")

link_libraries(ws2_32)
link_libraries(bcrypt)

add_library(cpp SHARED library.cpp)
target_link_libraries (cpp -static gcc stdc++ winpthread -dynamic)

install(
        TARGETS cpp
        RUNTIME DESTINATION ${PROJECT_BINARY_DIR}/bin
        LIBRARY DESTINATION ${PROJECT_BINARY_DIR}/bin
)