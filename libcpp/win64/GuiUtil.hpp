//
// Created by Roj234 on 2024/10/27 0027.
//

JNIEXPORT jlong JNICALL Java_roj_ui_GuiUtil_GetWindowLong(JNIEnv *, jclass, jlong hwnd, jint dwType) {return GetWindowLong((HWND) hwnd, dwType);}
JNIEXPORT void JNICALL Java_roj_ui_GuiUtil_SetWindowLong(JNIEnv *, jclass, jlong hwnd, jint dwType, jlong flags) {SetWindowLong((HWND) hwnd, dwType, flags);}
