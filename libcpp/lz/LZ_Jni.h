//
// Created by Roj234 on 2024/4/25 0025.
//

#ifndef LZ_JNI_H
#define LZ_JNI_H

#include "../main.h"

#ifdef __cplusplus
extern "C" {
#endif

#pragma pack(push, 1)
typedef struct {
    uint8_t compressionLevel;
    uint32_t dictSize;
    uint8_t* presetDict;
    uint32_t presetDictLength;
    uint8_t lc, lp, pb;
    uint8_t mode, mf;
    uint32_t niceLen;
    uint32_t depthLimit;
    uint8_t async;
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