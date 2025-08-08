
#ifdef _WIN32
#include <windows.h>
#else
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <errno.h>
#include <string.h>
#endif

struct SharedMemory {
#ifdef _WIN32
    HANDLE handle;
#else
    int handle; // 文件描述符
    size_t size;
#endif
    void* address;
};

#ifdef _WIN32
static jlong sharedMemory_map(JNIEnv *env, HANDLE mapping, DWORD mode) {
    LPVOID memory = MapViewOfFile(mapping, mode, 0, 0, 0);
    if (memory) {
        auto *ptr = static_cast<SharedMemory *>(malloc(sizeof(SharedMemory)));
        if (ptr) {
            ptr->handle = mapping;
            ptr->address = memory;

            return reinterpret_cast<jlong>(ptr);
        }
        Error(env, "malloc() failed");
        UnmapViewOfFile(memory);
    } else {
        Error(env, "MapViewOfFile() failed");
    }
    CloseHandle(mapping);
    return 0;
}
#else
static jlong sharedMemory_map(JNIEnv *env, int fd, int mode, size_t size) {
    void* memory = mmap(NULL, size, mode, MAP_SHARED, fd, 0);
    if (memory) {
        auto *ptr = static_cast<SharedMemory *>(malloc(sizeof(SharedMemory)));
        if (ptr) {
            ptr->handle = fd;
            ptr->size = size;
            ptr->address = memory;

            return reinterpret_cast<jlong>(ptr);
        }
        Error(env, "malloc() failed");
        free(memory);
    } else {
        Error(env, "mmap() failed");
    }
    close(fd);
    return 0;
}
#endif

JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nCreate(JNIEnv* env, jclass, jstring name, jlong size) {
    if (name == nullptr || size <= 0) {
        Error(env, "Invalid parameter");
        return 0;
    }

    auto c_string = env->GetStringUTFChars(name, nullptr);
    if (!c_string) return 0;

    jlong result = 0;

#ifdef _WIN32
    HANDLE mapping = CreateFileMapping(INVALID_HANDLE_VALUE, nullptr, PAGE_READWRITE | SEC_COMMIT,
                                       (DWORD)(size >> 32), (DWORD)size, c_string);
    if (!mapping) {
        Error(env, "CreateFileMapping() failed");
    } else if (GetLastError() == ERROR_ALREADY_EXISTS) {
        Error(env, "SharedMemory already exists");
    } else {
        result = sharedMemory_map(env, mapping, FILE_MAP_READ | FILE_MAP_WRITE);
    }
#else
    // Unix系统需要名称以/开头
    char fixedName[256] = "/";
    strncat(fixedName, c_string, sizeof(fixedName)-2);

    int fd = shm_open(fixedName, O_CREAT | O_EXCL | O_RDWR, 0666);
    if (fd == -1) {
        if (errno == EEXIST) {
            Error(env, "SharedMemory already exists");
        } else {
            Error(env, strerror(errno));
        }
    } else {
        if (ftruncate(fd, size) == -1) {
            close(fd);
            shm_unlink(fixedName);
            Error(env, "ftruncate failed");
        } else {
            result = sharedMemory_map(env, fd, PROT_READ | PROT_WRITE, size);
        }
    }
#endif

    env->ReleaseStringUTFChars(name, c_string);
    return result;
}

JNIEXPORT jlong JNICALL Java_roj_util_SharedMemory_nAttach(JNIEnv* env, jclass, jstring name, jboolean writable) {
    if (!name) {
        Error(env, "Invalid parameter");
        return 0;
    }

    auto* c_string = env->GetStringUTFChars(name, nullptr);
    if (!c_string) return 0;

    jlong result = 0;

#ifdef _WIN32
    DWORD mode = writable ? FILE_MAP_READ | FILE_MAP_WRITE : FILE_MAP_READ;
    HANDLE mapping = OpenFileMapping(mode, FALSE, c_string);
    if (mapping) {
        result = sharedMemory_map(env, mapping, mode);
    }
#else
    char fixedName[256] = "/";
    strncat(fixedName, c_string, sizeof(fixedName)-2);

    int flags = writable ? O_RDWR : O_RDONLY;
    int fd = shm_open(fixedName, flags, 0666);
    if (fd != -1) {
        struct stat st;
        fstat(fd, &st);
        size_t size = st.st_size;

        result = sharedMemory_map(env, fd, writable ? PROT_READ | PROT_WRITE : PROT_READ, size);
    }
#endif

    env->ReleaseStringUTFChars(name, c_string);
    return result;
}

// nGetAddress和nClose保持原样，但需要调整Unmap
JNIEXPORT void JNICALL Java_roj_util_SharedMemory_nClose(JNIEnv*, jclass, jlong pointer) {
    if (!pointer) return;

    auto* ptr = reinterpret_cast<SharedMemory*>(pointer);
#ifdef _WIN32
    UnmapViewOfFile(ptr->address);
    CloseHandle(ptr->handle);
#else
    munmap(ptr->address, ptr->size);
    close(ptr->handle);
#endif
    free(ptr);
}