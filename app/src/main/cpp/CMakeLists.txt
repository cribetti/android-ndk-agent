cmake_minimum_required(VERSION 3.22.1)
project(llama_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(llama_jni SHARED llama_jni.cpp)

find_library(log-lib log)
find_library(android-lib android)

# If llama.cpp is cloned/submoduled at app/src/main/cpp/third_party/llama.cpp,
# link it automatically; otherwise compile in offline stub mode so the app and
# unit tests build cleanly out of the box.
set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp")
if(EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    add_compile_options(-O3)
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
    set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
    add_subdirectory("${LLAMA_CPP_DIR}" llama_cpp_build)
    target_compile_definitions(llama_jni PRIVATE USE_LLAMA_CPP=1)
    target_link_libraries(llama_jni PRIVATE llama ${log-lib} ${android-lib})
else()
    target_compile_definitions(llama_jni PRIVATE USE_LLAMA_CPP=0)
    target_link_libraries(llama_jni PRIVATE ${log-lib} ${android-lib})
endif()
