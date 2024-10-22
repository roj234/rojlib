//
// Created by Roj234 on 2024/10/27 0027.
//

const BOOL NIO_TRUE = TRUE, NIO_FALSE = FALSE;
JNIEXPORT jint JNICALL Java_roj_io_NIOUtil_SetSocketOpt(JNIEnv *env, jclass, jint fd, jboolean on) {return setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char *>(on ? &NIO_TRUE : &NIO_FALSE), sizeof(BOOL));}
