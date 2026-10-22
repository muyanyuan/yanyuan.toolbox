cmake_minimum_required(VERSION 3.22.1)
project("toolboxpty")

add_library(toolboxpty SHARED pty_bridge.cpp)

target_compile_features(toolboxpty PRIVATE cxx_std_17)
find_library(log-lib log)
target_link_libraries(toolboxpty ${log-lib})
