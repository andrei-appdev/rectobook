cmake_minimum_required(VERSION 3.22.1)
project(cartavu)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(djvulibre)

add_library(cartavu SHARED
    djvu_jni.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(cartavu
    djvulibre
    ${log-lib}
    ${jnigraphics-lib}
)
