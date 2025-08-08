#include <jni.h>
#include <windows.h>

JNIEXPORT jstring JNICALL Java_roj_io_IOUtil_getHardLinkUUID0(JNIEnv* env, jclass, jstring filePath) {
    const jchar* widePath = env->GetStringChars(filePath, 0);
    HANDLE hFile = CreateFileW(
        reinterpret_cast<LPCWSTR>(widePath),  // 使用宽字符路径
        FILE_READ_ATTRIBUTES,
        FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
        NULL,
        OPEN_EXISTING,
        FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT,
        NULL
    );

    env->ReleaseStringChars(filePath, widePath);

    if (hFile == INVALID_HANDLE_VALUE) return nullptr;

    BOOL ok = TRUE;

    FILE_STANDARD_INFO fileInfo;
    FILE_ID_INFO fileIdInfo;

    ok &= GetFileInformationByHandleEx(hFile, FileStandardInfo, &fileInfo, sizeof(fileInfo));
    ok &= GetFileInformationByHandleEx(hFile, FileIdInfo, &fileIdInfo, sizeof(fileIdInfo));

    CloseHandle(hFile);

    if (fileInfo.NumberOfLinks <= 1) return nullptr;

    const jchar* idChars = reinterpret_cast<const jchar*>(fileIdInfo.FileId.Identifier);
    return env->NewString(idChars, 8);
}

JNIEXPORT jboolean JNICALL Java_roj_io_IOUtil_makeHardLink0(JNIEnv* env, jclass, jstring link, jstring existing) {
    BOOL result = FALSE;

    const jchar* src = env->GetStringChars(existing, nullptr);
    if (!src) return JNI_FALSE;

    const jchar* dst = env->GetStringChars(link, nullptr);
    if (!dst) goto releaseSource;

    result = CreateHardLinkW(reinterpret_cast<LPCWSTR>(dst), reinterpret_cast<LPCWSTR>(src), NULL);

    // 释放字符串资源
    env->ReleaseStringChars(link, dst);
    releaseSource:
    env->ReleaseStringChars(existing, src);
    
    return result ? JNI_TRUE : JNI_FALSE;
}