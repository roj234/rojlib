//
// Created by Roj234 on 2025/5/20 0020.
//

#ifndef ROJLIB_JNI_EXPORTS_H
#define ROJLIB_JNI_EXPORTS_H

// 判断是否为 MinGW 编译器
#ifdef __MINGW32__
#define USING_MINGW32 1
#define USING_MINGW 1
#elif defined(__MINGW64__)
#define USING_MINGW64 1
    #define USING_MINGW 1
#else
    #define USING_MINGW 0
#endif

#ifdef _MSC_VER
#define FJNIEXPORT __declspec(dllexport)
#define FJNICALL __cdecl
#endif
#ifdef USING_MINGW
#define FJNIEXPORT __declspec(__dllexport__)
#define FJNICALL __cdecl
#endif

FJNIEXPORT void FJNICALL IL_aes_init(const uint8_t *key, int32_t nr, uint8_t *Ke);
FJNIEXPORT void FJNICALL IL_aes_encrypt(const uint8_t *key, uint8_t number_of_rounds, uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks);
FJNIEXPORT void FJNICALL IL_aes_decrypt(const uint8_t *key, uint8_t number_of_rounds, uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks);

FJNIEXPORT void FJNICALL IL_aes_CBC_encrypt(const uint8_t *key, uint8_t number_of_rounds, const uint8_t ivec[16], uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks);
FJNIEXPORT void FJNICALL IL_aes_CBC_decrypt(const uint8_t *key, uint8_t number_of_rounds, const uint8_t ivec[16], uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks);
FJNIEXPORT void FJNICALL IL_aes_CTR(const uint8_t *key, uint8_t number_of_rounds, const uint8_t ivec[16], const uint8_t nonce[4], uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks);

FJNIEXPORT int FJNICALL IL_bsdiff_init(uint8_t *left, int32_t *sfx, int32_t size);
FJNIEXPORT bsdiff* FJNICALL IL_bsdiff_newCtx();
FJNIEXPORT void FJNICALL IL_bsdiff_freeCtx(void *ptr);
FJNIEXPORT int FJNICALL IL_bsdiff_makePatch(const int32_t *sfx, const uint8_t *left, bsdiff *ctx, const uint8_t *right, uint64_t ip0, uint64_t ip1, int32_t outSize);
FJNIEXPORT int FJNICALL IL_bsdiff_getDiffLength(const int32_t *sfx, const uint8_t *left, const uint8_t *right, int32_t off, int32_t len, int32_t maxDiff);

FJNIEXPORT uint32_t FJNICALL IL_xxHash32(int32_t seed, uint8_t *buf, int32_t off, int32_t len);

JNIEXPORT jlong JNICALL Java_roj_RojLib_init(JNIEnv *, jclass);

JNIEXPORT jint JNICALL Java_roj_ui_NativeVT_SetConsoleMode(JNIEnv *, jclass, jint, jint, jint);
JNIEXPORT jlong JNICALL Java_roj_ui_NativeVT_GetConsoleWindow(JNIEnv *, jclass);
JNIEXPORT jint JNICALL Java_roj_ui_NativeVT_GetConsoleSize(JNIEnv *, jclass);

JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nCreate(JNIEnv *, jclass, jstring, jlong);
JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nAttach(JNIEnv *, jclass, jstring, jboolean);
JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nGetAddress(JNIEnv *, jclass, jlong);
JNIEXPORT void JNICALL Java_roj_util_SharedMemory_nClose(JNIEnv *, jclass, jlong);

JNIEXPORT jint JNICALL Java_roj_net_Net_SetSocketOpt(JNIEnv *, jclass, jint, jboolean);

JNIEXPORT jlong JNICALL Java_roj_gui_GuiUtil_GetWindowLong(JNIEnv *, jclass, jlong hwnd, jint dwType);
JNIEXPORT void JNICALL Java_roj_gui_GuiUtil_SetWindowLong(JNIEnv *, jclass, jlong hwnd, jint dwType, jlong flags);

JNIEXPORT void JNICALL Java_roj_ui_Taskbar_initNatives(JNIEnv *env, jclass);
JNIEXPORT void JNICALL Java_roj_ui_Taskbar_setProgressType(JNIEnv *, jclass, jlong hwnd, jint type);
JNIEXPORT void JNICALL Java_roj_ui_Taskbar_setProgressValue(JNIEnv *, jclass, jlong hwnd, jlong progress, jlong total);

JNIEXPORT jstring JNICALL Java_roj_io_IOUtil_getHardLinkUUID0(JNIEnv* env, jclass, jstring filePath);
JNIEXPORT jboolean JNICALL Java_roj_io_IOUtil_makeHardLink0(JNIEnv* env, jclass, jstring link, jstring existing);

#endif //ROJLIB_JNI_EXPORTS_H
