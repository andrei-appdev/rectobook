cmake_minimum_required(VERSION 3.22.1)
project(djvureader)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(djvulibre)

add_library(djvureader SHARED
    djvu_jni.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(djvureader
    djvulibre
    ${log-lib}
    ${jnigraphics-lib}
)
