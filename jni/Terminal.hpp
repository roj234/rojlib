//
// Created by Roj234 on 2024/10/27 0027.
//

#define MODE_GET 0
#define MODE_SET 1

static byte modHandle = 0;
static DWORD initialMode[3];
static inline void restoreHandle() {
    HANDLE h;
    if (modHandle & 1) {
        h = GetStdHandle(STD_INPUT_HANDLE);
        if (h != INVALID_HANDLE_VALUE) SetConsoleMode(h, initialMode[0]);
    }
    if (modHandle & 2) {
        h = GetStdHandle(STD_OUTPUT_HANDLE);
        if (h != INVALID_HANDLE_VALUE) SetConsoleMode(h, initialMode[1]);
    }
    if (modHandle & 4) {
        h = GetStdHandle(STD_ERROR_HANDLE);
        if (h != INVALID_HANDLE_VALUE) SetConsoleMode(h, initialMode[2]);
    }
}

#ifdef _WIN32
#include <windows.h>
#else
#include <sys/ioctl.h>
#include <unistd.h>
#endif

static inline int get_terminal_size(uint32_t *cols, uint32_t *rows) {
#ifdef _WIN32
    CONSOLE_SCREEN_BUFFER_INFO csbi;
    if (!GetConsoleScreenBufferInfo(GetStdHandle(STD_OUTPUT_HANDLE), &csbi)) {
        return -1;
    }
    *cols = csbi.srWindow.Right - csbi.srWindow.Left + 1;
    *rows = csbi.srWindow.Bottom - csbi.srWindow.Top + 1;
    return 0;
#else
    struct winsize w;
    if (ioctl(STDOUT_FILENO, TIOCGWINSZ, &w) == -1) {
        return -1;
    }
    *cols = w.ws_col;
    *rows = w.ws_row;
    return 0;
#endif
}

JNIEXPORT jint JNICALL Java_roj_ui_NativeVT_GetConsoleSize(JNIEnv *, jclass) {
    uint32_t cols, rows;
    if (get_terminal_size(&cols, &rows)) {
        return 0;
    } else {
        return (cols << 16) | rows;
    }
}

JNIEXPORT jlong JNICALL Java_roj_ui_NativeVT_GetConsoleWindow(JNIEnv *, jclass) {return reinterpret_cast<jlong>(GetConsoleWindow());}
JNIEXPORT jint JNICALL Java_roj_ui_NativeVT_SetConsoleMode(JNIEnv *env, jclass, jint handle, jint mode, jint flags) {
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

    DWORD lpMode;
    if (!GetConsoleMode(h, &lpMode)) {
        Error(env, "GetConsoleMode() failed");
        return 0;
    }

    if (mode == MODE_SET) {
        if (!(modHandle & (1 << handle))) {
            modHandle |= 1 << handle;
            initialMode[handle] = lpMode;
        }

        lpMode = flags;

        if (!SetConsoleMode(h, lpMode)) {
            Error(env, "SetConsoleMode() failed");
            return 0;
        }
    }

    return static_cast<jint>(lpMode);
}

#undef MODE_GET
#undef MODE_SET
