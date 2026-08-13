# Builds the DjVuLibre core decode/render library (libdjvu) as a static
# library for Android. Only libdjvu/ is compiled here - the Qt/X11 GUI
# tools (djview4, ddjvu CLI, etc.) from upstream are intentionally not
# vendored, since a reader app only needs the core decoding library.

add_library(djvulibre STATIC
    libdjvu/Arrays.cpp
    libdjvu/atomic.cpp
    libdjvu/BSByteStream.cpp
    libdjvu/BSEncodeByteStream.cpp
    libdjvu/ByteStream.cpp
    libdjvu/DataPool.cpp
    libdjvu/ddjvuapi.cpp
    libdjvu/debug.cpp
    libdjvu/DjVmDir.cpp
    libdjvu/DjVmDir0.cpp
    libdjvu/DjVmDoc.cpp
    libdjvu/DjVmNav.cpp
    libdjvu/DjVuAnno.cpp
    libdjvu/DjVuDocEditor.cpp
    libdjvu/DjVuDocument.cpp
    libdjvu/DjVuDumpHelper.cpp
    libdjvu/DjVuErrorList.cpp
    libdjvu/DjVuFile.cpp
    libdjvu/DjVuFileCache.cpp
    libdjvu/DjVuGlobal.cpp
    libdjvu/DjVuGlobalMemory.cpp
    libdjvu/DjVuImage.cpp
    libdjvu/DjVuInfo.cpp
    libdjvu/DjVuMessage.cpp
    libdjvu/DjVuMessageLite.cpp
    libdjvu/DjVuNavDir.cpp
    libdjvu/DjVuPalette.cpp
    libdjvu/DjVuPort.cpp
    libdjvu/DjVuText.cpp
    libdjvu/DjVuToPS.cpp
    libdjvu/GBitmap.cpp
    libdjvu/GContainer.cpp
    libdjvu/GException.cpp
    libdjvu/GIFFManager.cpp
    libdjvu/GMapAreas.cpp
    libdjvu/GOS.cpp
    libdjvu/GPixmap.cpp
    libdjvu/GRect.cpp
    libdjvu/GScaler.cpp
    libdjvu/GSmartPointer.cpp
    libdjvu/GString.cpp
    libdjvu/GThreads.cpp
    libdjvu/GURL.cpp
    libdjvu/GUnicode.cpp
    libdjvu/IFFByteStream.cpp
    libdjvu/IW44EncodeCodec.cpp
    libdjvu/IW44Image.cpp
    libdjvu/JB2EncodeCodec.cpp
    libdjvu/JB2Image.cpp
    libdjvu/JPEGDecoder.cpp
    libdjvu/miniexp.cpp
    libdjvu/MMRDecoder.cpp
    libdjvu/MMX.cpp
    libdjvu/UnicodeByteStream.cpp
    libdjvu/XMLParser.cpp
    libdjvu/XMLTags.cpp
    libdjvu/ZPCodec.cpp
)

target_include_directories(djvulibre PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/libdjvu
)

# Upstream still uses the storage-class specifier `register` (legal-but-
# deprecated through C++14, a hard error from C++17 onward) in a handful
# of spots (GBitmap.h, MMX.h, DjVuDocEditor.cpp). Build this vendored
# library at C++14 rather than patching those out; the rest of the app
# still targets C++17.
set_target_properties(djvulibre PROPERTIES
    CXX_STANDARD 14
    CXX_STANDARD_REQUIRED ON
    CXX_EXTENSIONS OFF
)

target_compile_definitions(djvulibre PRIVATE
    HAVE_CONFIG_H=1
    UNIX=1
)

target_compile_options(djvulibre PRIVATE
    -Wno-deprecated-declarations
    -Wno-unused-parameter
)

find_library(log-lib log)
target_link_libraries(djvulibre PUBLIC ${log-lib})
