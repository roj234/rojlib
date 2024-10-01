#include <jni.h>
#include <stddef.h>

#ifndef _Included_roj_NativeLibrary
#define _Included_roj_NativeLibrary

inline int min(int l, int r) {return l < r ? l : r;}

#define FUNC_WINDOWS       1
#define ANSI_CONSOLE       2
#define BSDIFF             4
#define SHARED_MEMORY      8
#define FAST_LZMA         16

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL Java_roj_RojLib_init(JNIEnv *, jclass);
JNIEXPORT jint JNICALL Java_roj_RojLib_getLastError(JNIEnv *, jclass);

JNIEXPORT jint JNICALL Java_roj_ui_NativeVT_setConsoleMode0(JNIEnv *, jclass, jint, jint, jint);

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

JNIEXPORT jlong JNICALL Java_roj_ui_GuiUtil_nGetWindowLong(JNIEnv *, jclass, jlong hwnd, jint dwType);
JNIEXPORT void JNICALL Java_roj_ui_GuiUtil_nSetWindowLong(JNIEnv *, jclass, jlong hwnd, jint dwType, jlong flags);
JNIEXPORT jlong JNICALL Java_roj_ui_GuiUtil_nGetConsoleWindow(JNIEnv *, jclass);

JNIEXPORT jint JNICALL Java_roj_crypt_AESNI_callNativeFastJNI(JNIEnv *, jclass, jint v);
JNIEXPORT void JNICALL Java_roj_crypt_AESNI_infLoopFastJNI(JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif