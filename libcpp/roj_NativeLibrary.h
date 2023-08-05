#include <jni.h>

#ifndef _Included_roj_NativeLibrary
#define _Included_roj_NativeLibrary
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL Java_roj_NativeLibrary_init (JNIEnv *, jclass);
JNIEXPORT jint JNICALL Java_roj_ui_CLIUtil_setConsoleMode0 (JNIEnv *, jclass, jint, jint, jint);
JNIEXPORT jclass JNICALL Java_roj_reflect_Java9Compat_defineClass0 (JNIEnv *, jclass, jstring, jobject, jbyteArray, jint);
JNIEXPORT jlong JNICALL Java_roj_util_NativeMemory_hpAlloc0 (JNIEnv *, jclass, jlong);
JNIEXPORT void JNICALL Java_roj_util_NativeMemory_hpFree0 (JNIEnv *, jclass, jlong);

#ifdef __cplusplus
}
#endif
#endif
