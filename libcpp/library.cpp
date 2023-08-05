
#include "roj_NativeLibrary.h"
#include <windows.h>

static jclass nativeException = nullptr;
JNIEXPORT void JNICALL Java_roj_NativeLibrary_init(JNIEnv *env, jclass) {
    if (nativeException == nullptr)
        nativeException = (jclass) (env->NewGlobalRef(env->FindClass("roj/util/NativeException")));
}
void Error(JNIEnv *env, const char* msg) { env->ThrowNew(nativeException, msg); }

const int MODE_GET = 0, MODE_SET = 1, MODE_ADD = 2, MODE_REMOVE = 3, MODE_XOR = 4;

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

JNIEXPORT jint JNICALL Java_roj_ui_CLIUtil_setConsoleMode0(JNIEnv *env, jclass, jint handle, jint mode, jint flags) {
    DWORD dwHandle;
    switch (handle) {
        default: Error(env, "INVALID_HANDLE_VALUE"); return 0;
        case 0: dwHandle = STD_INPUT_HANDLE; break;
        case 1: dwHandle = STD_OUTPUT_HANDLE; break;
        case 2: dwHandle = STD_ERROR_HANDLE; break;
    }

    HANDLE h = GetStdHandle(dwHandle);
    if (h == INVALID_HANDLE_VALUE) {
        Error(env, "INVALID_HANDLE_VALUE");
        return 0;
    }

    DWORD lpMode = 0;
    if (!GetConsoleMode(h, &lpMode)) {
        Error(env, "GetConsoleMode() failed");
        return 0;
    }

    if (mode != MODE_GET && !(modHandle & (1 << handle))) {
        if (modHandle == 0) atexit(restoreHandle);

        modHandle |= 1 << handle;
        prevMode[handle] = lpMode;
    }

    switch (mode) {
        default:
        case MODE_GET: return static_cast<jint>(lpMode);
        case MODE_REMOVE: lpMode &= ~flags; break;
        case MODE_ADD: lpMode |= flags; break;
        case MODE_SET: lpMode = flags; break;
        case MODE_XOR: lpMode ^= flags; break;
    }

    if (!SetConsoleMode(h, lpMode)) {
        Error(env, "SetConsoleMode() failed");
        return 0;
    }

    return static_cast<jint>(lpMode);
}

JNIEXPORT jclass JNICALL Java_roj_reflect_Java9Compat_defineClass0(JNIEnv *env, jclass, jstring name, jobject cl, jbyteArray b, jint len) {
    auto len1 = env->GetArrayLength(b);
    if (len > len1) {
        Error(env, "array index out of bounds");
        return nullptr;
    }

    auto realName = env->GetStringUTFChars(name, JNI_FALSE);
    auto realData = env->GetByteArrayElements(b, JNI_FALSE);
    return env->DefineClass(realName, cl, realData, len);
}

JNIEXPORT jlong JNICALL Java_roj_util_NativeMemory_hpAlloc0(JNIEnv *env, jclass, jlong size) {
    if (size < 0) {
        Error(env, "invalid size");
        return 0;
    }

    void *address = malloc(size);
    if (!address) {
        Error(env, "malloc() failed");
        return 0;
    }
    return reinterpret_cast<jlong>(address);
}

JNIEXPORT void JNICALL Java_roj_util_NativeMemory_hpFree0(JNIEnv *, jclass, jlong address) {
    free(reinterpret_cast<void *>(address));
}