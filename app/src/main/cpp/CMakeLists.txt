cmake_minimum_required(VERSION 3.22.1)
project("turnit_engine")

# We will write the PRoot execution logic in engine.cpp later
add_library(turnit_engine SHARED engine.cpp)

# Link Android's native logging library so our C++ can print to Logcat
find_library(log-lib log)
target_link_libraries(turnit_engine ${log-lib})
