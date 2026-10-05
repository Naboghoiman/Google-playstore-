#ifndef LAME_CONFIG_H
#define LAME_CONFIG_H
#include <stdint.h>
#define STDC_HEADERS 1
#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_STDINT_H 1
#define HAVE_LIMITS_H 1
#define HAVE_MEMORY_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_UNISTD_H 1
#define HAVE_GETTIMEOFDAY 1
#define HAVE_STRTOL 1
#define HAVE_INT8_T 1
#define HAVE_INT16_T 1
#define HAVE_INT32_T 1
#define HAVE_INT64_T 1
#define HAVE_UINT8_T 1
#define HAVE_UINT16_T 1
#define HAVE_UINT32_T 1
#define HAVE_UINT64_T 1
typedef float ieee754_float32_t;
typedef double ieee754_float64_t;
typedef long double ieee854_float80_t;
#define HAVE_LONG_DOUBLE 1
#define LAME_LIBRARY_BUILD 1
#define PACKAGE "lame"
#define PACKAGE_NAME "lame"
#define PACKAGE_VERSION "3.100"
#define VERSION "3.100"
#define SIZEOF_DOUBLE 8
#define SIZEOF_FLOAT 4
#define SIZEOF_INT 4
#define SIZEOF_SHORT 2
#define SIZEOF_LONG_LONG 8
#define SIZEOF_UNSIGNED_INT 4
#define SIZEOF_UNSIGNED_SHORT 2
#define SIZEOF_UNSIGNED_LONG_LONG 8
#if defined(__LP64__)
#define SIZEOF_LONG 8
#define SIZEOF_UNSIGNED_LONG 8
#else
#define SIZEOF_LONG 4
#define SIZEOF_UNSIGNED_LONG 4
#endif
#define USE_FAST_LOG 1
#define TAKEHIRO_IEEE754_HACK 1
#endif
