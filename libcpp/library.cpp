
#include "roj_NativeLibrary.h"
static jclass nativeException = nullptr;
void Error(JNIEnv *env, const char* msg) { env->ThrowNew(nativeException, msg); }

#include "bsdiff.cpp"
#include "lz/LZ_Jni.cpp"

JNIEXPORT jlong JNICALL Java_roj_RojLib_init(JNIEnv *env, jclass) {
    if (nativeException == nullptr)
        nativeException = (jclass) (env->NewGlobalRef(env->FindClass("roj/util/NativeException")));
    jlong bitset = ANSI_CONSOLE | BSDIFF | SHARED_MEMORY
#ifdef _WIN32
     |FUNC_WINDOWS
#endif
            ;
    if (LzJni_init(env)) bitset |= FAST_LZMA;
    return bitset;
}

const int MODE_GET = 0, MODE_SET = 1;

#ifdef _WIN32
#include <windows.h>

JNIEXPORT jint JNICALL Java_roj_RojLib_getLastError(JNIEnv *, jclass) {
    return static_cast<jint>(GetLastError());
}

static byte modHandle = 0;
static DWORD prevMode[3];
static void restoreHandle() {
    HANDLE h;
    if (modHandle & 1) {
        h = GetStdHandle(STD_INPUT_HANDLE);
        if (h != INVALID_HANDLE_VALUE) SetConsoleMode(h, prevMode[0]);
    }
    if (modHandle & 2) {
        h = GetStdHandle(STD_OUTPUT_HANDLE);
        if (h != INVALID_HANDLE_VALUE) SetConsoleMode(h, prevMode[1]);
    }
    if (modHandle & 4) {
        h = GetStdHandle(STD_ERROR_HANDLE);
        if (h != INVALID_HANDLE_VALUE) SetConsoleMode(h, prevMode[2]);
    }
}

JNIEXPORT jint JNICALL Java_roj_ui_NativeVT_setConsoleMode0(JNIEnv *env, jclass, jint handle, jint mode, jint flags) {
    DWORD dwHandle;
    switch (handle) {
        default: Error(env, "no such handle"); return 0;
        case 0: dwHandle = STD_INPUT_HANDLE; break;
        case 1: dwHandle = STD_OUTPUT_HANDLE; break;
        case 2: dwHandle = STD_ERROR_HANDLE; break;
    }

    HANDLE h = GetStdHandle(dwHandle);
    if (h == INVALID_HANDLE_VALUE) {
        Error(env, "no such handle");
        return 0;
    }

    DWORD lpMode = 0;
    if (!GetConsoleMode(h, &lpMode)) {
        Error(env, "GetConsoleMode() failed");
        return 0;
    }

    if (mode == MODE_SET) {
        if (!(modHandle & (1 << handle))) {
            if (modHandle == 0) atexit(restoreHandle);

            modHandle |= 1 << handle;
            prevMode[handle] = lpMode;
        }

        lpMode = flags;

        if (!SetConsoleMode(h, lpMode)) {
            Error(env, "SetConsoleMode() failed");
            return 0;
        }
    }

    return static_cast<jint>(lpMode);
}

struct NativePipeWin {
    HANDLE pHandle;
    LPVOID sharedMemory;
};

jlong doMap(JNIEnv *env, HANDLE mapping, DWORD mapMode) {
    LPVOID shared_memory = MapViewOfFile(mapping, mapMode, 0, 0, 0);
    if (shared_memory == nullptr) {
        CloseHandle(mapping);

        Error(env, "MapViewOfFile() failed");
        return 0;
    }

    auto *ptr = static_cast<NativePipeWin *>(malloc(sizeof(NativePipeWin)));
    ptr->pHandle = mapping;
    ptr->sharedMemory = shared_memory;

    return reinterpret_cast<jlong>(ptr);
}

JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nCreate(JNIEnv *env, jclass, jstring name, jlong size) {
    if (name == nullptr || size <= 0) {
        Error(env, "Invalid parameter");
        return 0;
    }

    auto pipeName = static_cast<LPCSTR>(env->GetStringUTFChars(name, nullptr));
    if (pipeName == nullptr) return 0;

    // 有这个必要吗，我到底在干啥，我检查过小于零了不是么
    auto mySize = static_cast<unsigned long long>(size);

    HANDLE mapping = OpenFileMappingA(FILE_MAP_READ, FALSE, pipeName);
    if (mapping != nullptr) {
        CloseHandle(mapping);
        Error(env, "SharedMemory already exist");
        return 0;
    }

    mapping = CreateFileMappingA(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE | SEC_COMMIT, static_cast<DWORD>(mySize >> 32), static_cast<DWORD>(mySize), pipeName);

    env->ReleaseStringUTFChars(name, pipeName);

    if (mapping == nullptr) {
        Error(env, "CreateFileMapping() failed");
        return 0;
    }

    return doMap(env, mapping, FILE_MAP_READ|FILE_MAP_WRITE);
}

JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nAttach(JNIEnv *env, jclass, jstring name, jboolean writable) {
    if (name == nullptr) {
        Error(env, "Invalid parameter");
        return 0;
    }

    auto pipeName = static_cast<LPCSTR>(env->GetStringUTFChars(name, nullptr));
    if (pipeName == nullptr) return 0;

    DWORD modeFlag = writable ? FILE_MAP_READ|FILE_MAP_WRITE : FILE_MAP_READ;

    HANDLE mapping = OpenFileMappingA(modeFlag, FALSE, pipeName);
    if (mapping == nullptr) return 0;

    env->ReleaseStringUTFChars(name, pipeName);

    return doMap(env, mapping, modeFlag);
}

JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nGetAddress(JNIEnv*, jclass, jlong pointer) {
    if (pointer == 0) return 0;

    auto *ptr = reinterpret_cast<NativePipeWin *>(pointer);
    return reinterpret_cast<jlong>(ptr->sharedMemory);
}

JNIEXPORT void JNICALL Java_roj_util_SharedMemory_nClose(JNIEnv*, jclass, jlong pointer) {
    if (pointer == 0) return;

    auto *ptr = reinterpret_cast<NativePipeWin *>(pointer);
    UnmapViewOfFile(ptr->sharedMemory);
    CloseHandle(ptr->pHandle);
    free(ptr);
}

const char* ENABLE = "11";
const char* DISABLE = "00";
JNIEXPORT jint JNICALL Java_roj_io_NIOUtil_windowsOnlyReuseAddr(JNIEnv *env, jclass, jint fd, jboolean on) {
    return setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, on ? ENABLE : DISABLE, sizeof(int));
}


JNIEXPORT jlong JNICALL Java_roj_ui_GuiUtil_nGetWindowLong(JNIEnv *, jclass, jlong hwnd, jint dwType) {
    return GetWindowLong((HWND) hwnd, dwType);
}

JNIEXPORT void JNICALL Java_roj_ui_GuiUtil_nSetWindowLong(JNIEnv *, jclass, jlong hwnd, jint dwType, jlong flags) {
    SetWindowLong((HWND) hwnd, dwType, flags);
}

JNIEXPORT jlong JNICALL Java_roj_ui_GuiUtil_nGetConsoleWindow(JNIEnv *, jclass) {
    //{
    //    HANDLE hStdOut = GetStdHandle(STD_OUTPUT_HANDLE);
    //    HANDLE hStdIn = GetStdHandle(STD_INPUT_HANDLE);
    //
    //    if (hStdIn == INVALID_HANDLE_VALUE ||
    //        hStdOut == INVALID_HANDLE_VALUE) {
    //        return JNI_FALSE;
    //    }
    //
    //    if (GetFileType(hStdIn) != FILE_TYPE_CHAR ||
    //        GetFileType(hStdOut) != FILE_TYPE_CHAR) {
    //        return JNI_FALSE;
    //    }
    //
    //    return JNI_TRUE;
    //}
    return reinterpret_cast<jlong>(GetConsoleWindow());
}

#else

#include <sys/types.h>
#include <sys/ipc.h>
#include <sys/shm.h>

JNIEXPORT jint JNICALL Java_roj_ui_NativeVT_setConsoleMode0(JNIEnv *env, jclass, jint handle, jint mode, jint flags) {
    Error(env, "Unsupported operation system");
    return 0;
}

JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nCreate(JNIEnv *env, jclass, jstring name, jlong size) {
    Error(env, "Unsupported operation system");
    return 0;
}

JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nAttach(JNIEnv *env, jclass, jstring name) {
    Error(env, "Unsupported operation system");
    return 0;
}

JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nGetAddress(JNIEnv *env, jclass, jlong pointer) {
    Error(env, "Unsupported operation system");
    return 0;
}

JNIEXPORT void JNICALL Java_roj_util_SharedMemory_nClose(JNIEnv *env, jclass, jlong pointer) {
    Error(env, "Unsupported operation system");
}

#endif


JNIEXPORT jint JNICALL Java_roj_crypt_AESNI_callNativeFastJNI(JNIEnv *, jclass, jint v) {
    return v * 233;
}
JNIEXPORT void JNICALL Java_roj_crypt_AESNI_infLoopFastJNI(JNIEnv *, jclass) {
    while (true);
}