//
// Created by Roj234 on 2024/10/27 0027.
//

#pragma comment(lib, "user32.lib")

JNIEXPORT jlong JNICALL Java_roj_gui_GuiUtil_GetWindowLong(JNIEnv *, jclass, jlong hwnd, jint dwType) {return GetWindowLong((HWND) hwnd, dwType);}
JNIEXPORT void JNICALL Java_roj_gui_GuiUtil_SetWindowLong(JNIEnv *, jclass, jlong hwnd, jint dwType, jlong flags) {SetWindowLong((HWND) hwnd, dwType, flags);}

#pragma comment(lib, "ws2_32.lib")

static const BOOL NIO_TRUE = TRUE, NIO_FALSE = FALSE;
JNIEXPORT jint JNICALL Java_roj_net_Net_SetSocketOpt(JNIEnv *env, jclass, jint fd, jboolean on) {return setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char *>(on ? &NIO_TRUE : &NIO_FALSE), sizeof(BOOL));}

#include "win64/wprogressbar.cpp"

ITaskbarList3 *taskbarInstance;

JNIEXPORT void JNICALL Java_roj_ui_Taskbar_initNatives(JNIEnv *env, jclass) {
    taskbarInstance = InitializeTaskbarProgress();
    if (!taskbarInstance)
        Error(env, "Failed to obtain ITaskbarList3 instance");
}
JNIEXPORT void JNICALL Java_roj_ui_Taskbar_setProgressType(JNIEnv *, jclass, jlong hwnd, jint type) {
    SetTaskbarState(taskbarInstance, (HWND)hwnd, (TBPFLAG)type);
}
JNIEXPORT void JNICALL Java_roj_ui_Taskbar_setProgressValue(JNIEnv *, jclass, jlong hwnd, jlong progress, jlong total) {
    SetTaskbarProgress(taskbarInstance, (HWND)hwnd, progress, total);
}

static inline void OnFreeLibrary() {
    ReleaseTaskbarProgress(taskbarInstance);
    taskbarInstance = nullptr;
}