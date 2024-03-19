//
// Created by Roj234 on 2024/4/25 0025.
//

#ifndef LZ_JNI_H
#define LZ_JNI_H

#include "../roj_NativeLibrary.h"

#ifdef __cplusplus
extern "C" {
#endif

#pragma pack(push, 1)
typedef struct {
    u1 compressionLevel;
    u4 dictSize;
    void* presetDict;
    u4 presetDictLength;
    u1 lc, lp, pb;
    u1 mode, mf;
    u4 niceLen;
    u4 depthLimit;
    u1 async;
} LZMA_OPTIONS_NATIVE;
#pragma pack(pop)

JNIEXPORT void JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_initNatives(JNIEnv *, jclass);
JNIEXPORT jlong JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_getMemoryUsage(JNIEnv *, jclass, LZMA_OPTIONS_NATIVE *opt);

JNIEXPORT jlong JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_nInit(JNIEnv *, jobject, LZMA_OPTIONS_NATIVE*);
JNIEXPORT jlong JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_nSetProperty(JNIEnv *, jobject, jint property_id, jlong property_value);

JNIEXPORT jlong JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_nWrite(JNIEnv *, jobject, jint);

JNIEXPORT jlong JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_nFlush(JNIEnv *, jobject);
JNIEXPORT jlong JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_nFinish(JNIEnv *, jobject);

JNIEXPORT void JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_nFree(JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif
#endif