cmake_minimum_required(VERSION 3.22.1)
project(djiman_native LANGUAGES C CXX)

set(LAME_DIR ${CMAKE_CURRENT_SOURCE_DIR}/lame/libmp3lame)
set(LAME_SOURCES
    ${LAME_DIR}/bitstream.c
    ${LAME_DIR}/encoder.c
    ${LAME_DIR}/fft.c
    ${LAME_DIR}/gain_analysis.c
    ${LAME_DIR}/id3tag.c
    ${LAME_DIR}/lame.c
    ${LAME_DIR}/newmdct.c
    ${LAME_DIR}/presets.c
    ${LAME_DIR}/psymodel.c
    ${LAME_DIR}/quantize.c
    ${LAME_DIR}/quantize_pvt.c
    ${LAME_DIR}/reservoir.c
    ${LAME_DIR}/set_get.c
    ${LAME_DIR}/tables.c
    ${LAME_DIR}/takehiro.c
    ${LAME_DIR}/util.c
    ${LAME_DIR}/vbrquantize.c
    ${LAME_DIR}/VbrTag.c
    ${LAME_DIR}/version.c
)

add_library(djiman_native SHARED native_engine.cpp ${LAME_SOURCES})
target_include_directories(djiman_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/lame/include
    ${LAME_DIR}
)
target_compile_definitions(djiman_native PRIVATE HAVE_CONFIG_H=1)
target_compile_options(djiman_native PRIVATE -O3 -ffast-math)
find_library(AAUDIO_LIB aaudio)
find_library(LOG_LIB log)
find_library(ANDROID_LIB android)
target_link_libraries(djiman_native ${AAUDIO_LIB} ${LOG_LIB} ${ANDROID_LIB} m)
