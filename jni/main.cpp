#include <jni.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

struct bsdiff {
    const int32_t* sfx;

    const uint8_t* left;
    int32_t leftLen;

    const uint8_t* right;
    int32_t rightLen;

    int32_t scan, lastScan, lastPos, len, lastOffset;

    void* ctx;
};

static jclass nativeException;
static inline void Error(JNIEnv *env, const char* msg) { env->ThrowNew(nativeException, msg); }
static inline void OnFreeLibrary();

#include "exports.h"

#define FUNC_GENERIC       1
#define FUNC_WINDOWS       2
#define ANSI_CONSOLE       4
#define SHARED_MEMORY      8
#define FAST_LZMA         16
#define AESNI             32

#ifdef _WIN32

#include <windows.h>
#include "WindowsOnly.hpp"
#include "IOUtil.hpp"

#endif

#include "aes.hpp"
#include "bsdiff.hpp"
#include "lz/LZ_Jni.cpp"
#include "xxhash.hpp"
#include "SharedMemory.hpp"
#include "Terminal.hpp"

#define cpuid(func,ax,bx,cx,dx)\
__asm__ __volatile__ ("cpuid":\
"=a" (ax), "=b" (bx), "=c" (cx), "=d" (dx) : "a" (func));

JNIEXPORT jlong JNICALL Java_roj_RojLib_init(JNIEnv *env, jclass) {
    nativeException = (jclass) (env->NewGlobalRef(env->FindClass("roj/util/NativeException")));

    unsigned int a,b,c,d;
    cpuid(1,a,b,c,d);

    jlong bits = FUNC_GENERIC | ANSI_CONSOLE | SHARED_MEMORY
#ifdef _WIN32
       | FUNC_WINDOWS
#else
       | 0
#endif
            ;
    if (c & 0x2000000) bits |= AESNI;

    return bits;
}


// 全局变量存储 JavaVM 指针
static JavaVM* g_jvm = NULL;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6; // 返回您使用的 JNI 版本
}

JNIEnv* get_jni_env() {
    JNIEnv* env = NULL;
    if (g_jvm) {
        jint result = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
        if (result == JNI_EDETACHED) {
            // 线程未附加，附加它
            //result = g_jvm->AttachCurrentThread((void**)&env, NULL);
            //if (result != JNI_OK) {
                // 处理附加失败
                return NULL;
            //}
        } else if (result != JNI_OK) {
            // 处理其他错误
            return NULL;
        }
    }
    return env;
}

JNIEXPORT bool IL_SafePoint() {
    JNIEnv *env = get_jni_env();

    if (env != NULL) {
        jmethodID gcMethod = env->GetStaticMethodID(nativeException, "safepoint", "()V");
        env->CallStaticVoidMethod(nativeException, gcMethod);
        return true;
    }

    return false;
}

FJNIEXPORT void FJNICALL IL_WhileTrue(uint32_t x) {
    while (--x) {
        if (!(x & 4096)) {
            bool n = IL_SafePoint();
        }
    }
}

#ifdef _WIN32

BOOL WINAPI DllMain(HINSTANCE hinstDLL, DWORD fdwReason, LPVOID lpvReserved) {
    switch(fdwReason) {
        case DLL_PROCESS_ATTACH:
            // Initialize once for each new process.
            // Return FALSE to fail DLL load.
        break;
        case DLL_PROCESS_DETACH:
            restoreHandle();
            //if (lpReserved == NULL) {
            //    OnFreeLibrary();
            //}
        break;
    }

    return TRUE;
}

#else



#endif

#ifdef __cplusplus
}
#endif
