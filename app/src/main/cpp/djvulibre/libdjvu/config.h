/* Hand-written config.h for building libdjvu core on Android (NDK/Clang/Bionic).
 *
 * Upstream DjVuLibre generates this file via autoconf's `configure` script,
 * which isn't run when cross-compiling for Android. The values below were
 * chosen by inspecting how each HAVE_ and HAS_ macro actually gates code in
 * libdjvu (not by re-running autoconf), matched against what the NDK's
 * Bionic sysroot provides for minSdkVersion 24+.
 */
#ifndef CONFIG_H
#define CONFIG_H

#define DJVULIBRE_VERSION "3.5.28"
#define PACKAGE "djvulibre"
#define PACKAGE_NAME "DjVuLibre"
#define PACKAGE_VERSION DJVULIBRE_VERSION
#define PACKAGE_STRING "DjVuLibre " DJVULIBRE_VERSION
#define VERSION DJVULIBRE_VERSION

/* Compiling as C++, bool is a native type */
#define HAVE_BOOL 1
#define HAVE_NAMESPACES 1
#define HAVE_TYPENAME 1
#define HAVE_MEMBER_TEMPLATES 1
#define HAVE_EXCEPTIONS 1
#define HAVE_STDINCLUDES 1

/* Standard/Bionic headers present in the NDK unified sysroot */
#define HAVE_STDDEF_H 1
#define HAVE_STDINT_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_UNISTD_H 1
#define HAVE_DIRENT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TIME_H 1
#define HAVE_SYS_WAIT_H 1
#define HAVE_SYS_MMAN_H 1
#define HAVE_MMAP 1
#define HAVE_WCHAR_H 1
#define HAVE_WCHAR_T 1
#define HAVE_WCTYPE_H 1
#define HAVE_ISWSPACE 1
#define HAVE_MBSTATE_T 1
#define HAVE_WCRTOMB 1
#define STDC_HEADERS 1

/* Do NOT define HAVE_ICONV / HAVE_ICONV_H: iconv() only landed in Bionic
 * at API 28, but this project targets minSdk 24. libdjvu core does not
 * require it (falls back to its own Unicode handling, HAS_ICONV=0). */

/* Threading: Bionic provides full pthreads, no Qt in this build */
#define HAVE_PTHREAD 1

/* GCC/Clang builtins available on all Android ABIs we target
 * (arm64-v8a, armeabi-v7a, x86_64) - avoids the x86-only inline-asm
 * fallback path in atomic.h */
#define HAVE_INTEL_ATOMIC_BUILTINS 1
#define HAVE_GCCTLS 1
#define HAVE_LONG_LONG_INT 1

/* pwd.h / getpwuid are present in Bionic (used only as an optional
 * $HOME fallback in DjVuMessage.cpp) */
#define HAVE_GETPWUID 1

#define HAVE_STRERROR 1
#define HAVE_VSNPRINTF 1
#define HAVE_SETENV 1
#define HAVE_MKSTEMP 1
#define HAVE_SIGACTION 1
#define HAVE_STRFTIME 1

/* --- Derived HAS_* flags -----------------------------------------------
 * Upstream's autoconf-generated config.h.in computes these from the
 * HAVE_* checks above (see config/config.h.in in the DjVuLibre source).
 * Headers like GString.h branch on HAS_WCHAR/HAS_MBSTATE/HAS_ICONV
 * directly, NOT on the underlying HAVE_* macros, so this derivation is
 * required - without it, an unguarded `#if HAS_WCHAR` silently evaluates
 * to false (undefined -> 0) and declarations in GString.h stop matching
 * their out-of-line definitions in GString.cpp.
 */
#if !defined(HAVE_WCHAR_T)
# define HAS_WCHAR 0
# define HAS_WCTYPE 0
# define HAS_MBSTATE 0
#else
# define HAS_WCHAR 1
# if defined(HAVE_WCTYPE_H) && defined(HAVE_ISWSPACE)
#  define HAS_WCTYPE 1
# endif
# if defined(HAVE_MBSTATE_T) && defined(HAVE_WCRTOMB)
#  define HAS_MBSTATE 1
# endif
#endif

#if defined(HAVE_ICONV_H) && defined(HAVE_ICONV)
# define HAS_ICONV 1
#else
# define HAS_ICONV 0
#endif

#define HAS_CTRL_C_IN_ERR_MSG 1

#ifndef HAVE_MEMBER_TEMPLATES
# define GCONTAINER_NO_MEMBER_TEMPLATES 1
#endif
#ifndef HAVE_TYPENAME
# define GCONTAINER_NO_TYPENAME 1
#endif

#ifdef HAVE_JPEG
# define NEED_JPEG_DECODER 1
#endif

#endif /* CONFIG_H */
