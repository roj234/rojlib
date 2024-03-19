#include <jni.h>

#ifndef _Included_roj_NativeLibrary
#define _Included_roj_NativeLibrary

typedef unsigned long int u4;
typedef signed long int s4;
typedef unsigned char u1;
typedef signed char s1;

#define U32_MAX 2147483647
inline int min(int l, int r) {return l < r ? l : r;}

#define REUSE_PORT_WINDOWS 1
#define ANSI_CONSOLE       2
#define BSDIFF             4
#define SHARED_MEMORY      8
#define FAST_LZMA         16

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL Java_roj_NativeLibrary_init(JNIEnv *, jclass);
JNIEXPORT jint JNICALL Java_roj_NativeLibrary_getLastError(JNIEnv *, jclass);

JNIEXPORT jint JNICALL Java_roj_ui_CLIUtil_setConsoleMode0(JNIEnv *, jclass, jint, jint, jint);

JNIEXPORT jclass JNICALL Java_roj_reflect_Java9Compat_defineClass0(JNIEnv *, jclass, jstring, jobject, jbyteArray, jint);

JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nCreate(JNIEnv *, jclass, jstring, jlong);
JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nAttach(JNIEnv *, jclass, jstring, jboolean);
JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nGetAddress(JNIEnv *, jclass, jlong);
JNIEXPORT void JNICALL Java_roj_util_SharedMemory_nClose(JNIEnv *, jclass, jlong);

JNIEXPORT jlong JNICALL Java_roj_util_BsDiff_nCreate(JNIEnv *, jclass, jlong, jint);
JNIEXPORT jlong JNICALL Java_roj_util_BsDiff_nCopy(JNIEnv *, jclass, jlong);
JNIEXPORT jint JNICALL Java_roj_util_BsDiff_nGetDiffLength(JNIEnv *, jclass, jlong, jlong, jint, jint);
JNIEXPORT jint JNICALL Java_roj_util_BsDiff_nGenPatch(JNIEnv *, jclass, jlong, jlong, jint, jlong, jint);
JNIEXPORT void JNICALL Java_roj_util_BsDiff_nClose(JNIEnv *, jclass, jlong);

JNIEXPORT jint JNICALL Java_roj_io_NIOUtil_windowsOnlyReuseAddr(JNIEnv *, jclass, jint, jboolean);

#ifdef __cplusplus
}
#endif
#endif