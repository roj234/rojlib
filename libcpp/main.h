#include <jni.h>
#include <cstddef>
#include <cstdint>

#ifndef _Included_roj_NativeLibrary
#define _Included_roj_NativeLibrary

#define FJNIEXPORT __declspec(__dllexport__)
#define FJNICALL __cdecl

inline int min(int l, int r) {return l < r ? l : r;}

#define FUNC_GENERIC       1
#define FUNC_WINDOWS       2
#define ANSI_CONSOLE       5
#define SHARED_MEMORY      8
#define FAST_LZMA         16
#define AESNI             32

struct bsdiff {
    int32_t* sfx;

    const uint8_t* left;
    int32_t leftLen;

    const uint8_t* right;
    int32_t rightLen;

    int32_t scan, lastScan, lastPos, len, lastOffset;

    void* ctx;
};

#ifdef __cplusplus
extern "C" {
#endif

FJNIEXPORT void FJNICALL IL_aes_init(const uint8_t *key, int32_t nr, uint8_t *Ke);
FJNIEXPORT void FJNICALL IL_aes_encrypt(const uint8_t *key, uint8_t number_of_rounds, uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks);
FJNIEXPORT void FJNICALL IL_aes_decrypt(const uint8_t *key, uint8_t number_of_rounds, uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks);

FJNIEXPORT void FJNICALL IL_aes_CBC_encrypt(const uint8_t *key, uint8_t number_of_rounds, const uint8_t ivec[16], uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks);
FJNIEXPORT void FJNICALL IL_aes_CBC_decrypt(const uint8_t *key, uint8_t number_of_rounds, const uint8_t ivec[16], uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks);
FJNIEXPORT void FJNICALL IL_aes_CTR(const uint8_t *key, uint8_t number_of_rounds, const uint8_t ivec[16], const uint8_t nonce[4], uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks);

FJNIEXPORT int FJNICALL IL_bsdiff_init(void *_this, uint8_t *left, int32_t *sfx, int32_t size);
FJNIEXPORT bsdiff* FJNICALL IL_bsdiff_newCtx();
FJNIEXPORT void FJNICALL IL_bsdiff_freeCtx(void *ptr);
FJNIEXPORT int FJNICALL IL_bsdiff_makePatch(void *_this, bsdiff *ctx, const uint8_t *right, uint64_t ip0, uint64_t ip1, int32_t outSize);
FJNIEXPORT int FJNICALL IL_bsdiff_getDiffLength(void *_this, const uint8_t *right, int32_t off, int32_t len, int32_t maxDiff);

FJNIEXPORT uint32_t FJNICALL IL_xxHash32(int32_t seed, uint8_t *buf, int32_t off, int32_t len);

JNIEXPORT jlong JNICALL Java_roj_RojLib_init(JNIEnv *, jclass);

JNIEXPORT jint JNICALL Java_roj_ui_NativeVT_SetConsoleMode(JNIEnv *, jclass, jint, jint, jint);
JNIEXPORT jlong JNICALL Java_roj_ui_NativeVT_GetConsoleWindow(JNIEnv *, jclass);

JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nCreate(JNIEnv *, jclass, jstring, jlong);
JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nAttach(JNIEnv *, jclass, jstring, jboolean);
JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nGetAddress(JNIEnv *, jclass, jlong);
JNIEXPORT void JNICALL Java_roj_util_SharedMemory_nClose(JNIEnv *, jclass, jlong);

JNIEXPORT jint JNICALL Java_roj_io_NIOUtil_SetSocketOpt(JNIEnv *, jclass, jint, jboolean);

JNIEXPORT jlong JNICALL Java_roj_ui_GuiUtil_GetWindowLong(JNIEnv *, jclass, jlong hwnd, jint dwType);
JNIEXPORT void JNICALL Java_roj_ui_GuiUtil_SetWindowLong(JNIEnv *, jclass, jlong hwnd, jint dwType, jlong flags);

#ifdef __cplusplus
}
#endif
#endif