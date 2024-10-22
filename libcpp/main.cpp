
#include "main.h"

static jclass nativeException;
static inline void Error(JNIEnv *env, const char* msg) { env->ThrowNew(nativeException, msg); }

#include "aes.hpp"
#include "bsdiff.hpp"
#include "lz/LZ_Jni.cpp"
#include "xxhash.hpp"

#define cpuid(func,ax,bx,cx,dx)\
__asm__ __volatile__ ("cpuid":\
"=a" (ax), "=b" (bx), "=c" (cx), "=d" (dx) : "a" (func));

JNIEXPORT jlong JNICALL Java_roj_RojLib_init(JNIEnv *env, jclass) {
    nativeException = (jclass) (env->NewGlobalRef(env->FindClass("roj/util/NativeException")));

    unsigned int a,b,c,d;
    cpuid(1,a,b,c,d);

    jlong bits = FUNC_GENERIC
#ifdef _WIN32
       | FUNC_WINDOWS | SHARED_MEMORY | ANSI_CONSOLE
#else
       | 0
#endif
            ;
    if (c & 0x2000000) bits |= AESNI;
    if (LzJni_init(env)) bits |= FAST_LZMA;

    return bits;
}

#ifdef _WIN32

#include <windows.h>
#include "win64/GuiUtil.hpp"
#include "win64/NativeVT.hpp"
#include "win64/SharedMemory.hpp"
#include "win64/NIOUtil.hpp"

BOOL WINAPI DllMain(HINSTANCE hinstDLL, DWORD fdwReason, LPVOID lpvReserved) {
    switch(fdwReason) {
        case DLL_PROCESS_ATTACH:
            // Initialize once for each new process.
            // Return FALSE to fail DLL load.
        break;

        case DLL_THREAD_ATTACH:
            // Do thread-specific initialization.
        break;

        case DLL_THREAD_DETACH:
            // Do thread-specific cleanup.
        break;

        case DLL_PROCESS_DETACH:
            restoreHandle();
            if (lpvReserved != nullptr) {

                break; // do not do cleanup if process termination scenario
            }

        // Perform any necessary cleanup.
        break;
    }

    return TRUE;
}

#else

#include <sys/types.h>
#include <sys/ipc.h>
#include <sys/shm.h>

#include "linux/GuiUtil.hpp"
#include "linux/NativeVT.hpp"
#include "linux/SharedMemory.hpp"

#endif
