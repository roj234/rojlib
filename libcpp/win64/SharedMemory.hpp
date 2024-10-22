//
// Created by Roj234 on 2024/10/27 0027.
//

struct SharedMemory {
    HANDLE pHandle;
    LPVOID sharedMemory;
};

static jlong sharedMemory_map(JNIEnv *env, HANDLE mapping, DWORD mapMode) {
    LPVOID shared_memory = MapViewOfFile(mapping, mapMode, 0, 0, 0);
    if (shared_memory == nullptr) {
        CloseHandle(mapping);

        Error(env, "MapViewOfFile() failed");
        return 0;
    }

    auto *ptr = static_cast<SharedMemory *>(malloc(sizeof(SharedMemory)));
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

    return sharedMemory_map(env, mapping, FILE_MAP_READ|FILE_MAP_WRITE);
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

    return sharedMemory_map(env, mapping, modeFlag);
}

JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nGetAddress(JNIEnv*, jclass, jlong pointer) {
    if (pointer == 0) return 0;

    auto *ptr = reinterpret_cast<SharedMemory *>(pointer);
    return reinterpret_cast<jlong>(ptr->sharedMemory);
}

JNIEXPORT void JNICALL Java_roj_util_SharedMemory_nClose(JNIEnv*, jclass, jlong pointer) {
    if (pointer == 0) return;

    auto *ptr = reinterpret_cast<SharedMemory *>(pointer);
    UnmapViewOfFile(ptr->sharedMemory);
    CloseHandle(ptr->pHandle);
    free(ptr);
}