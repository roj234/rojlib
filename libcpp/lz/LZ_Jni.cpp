//
// Created by Roj234 on 2024/4/25 0025.
//

#ifndef LZ_JNI_CPP
#define LZ_JNI_CPP

#include "../roj_NativeLibrary.h"
#include "LZ_Jni.h"
#include "fast-lzma2.h"
#include <Windows.h>

#define FID(p0, p1) env->GetFieldID(klass, p0, p1)
#define GETCTX \
    auto *ctx = reinterpret_cast<FL2_CStream *>(env->GetLongField(self, fields[0]));\
    if (ctx == nullptr) {\
        Error(env, "stream closed");\
        return 0;\
    }

bool LzJni_init(JNIEnv *env) {
    HINSTANCE fastLzma = LoadLibrary("fast-lzma2");
    if (fastLzma == nullptr) return false;

    bool success = false;
    auto fn = reinterpret_cast<FL2_versionNumber>(GetProcAddress(fastLzma, "FL2_versionNumber"));
    if (fn != nullptr) {
        success = fn() == FL2_VERSION_NUMBER;
    }

    FreeLibrary(fastLzma);
/*
    size_t const CNBuffSize = 20 MB;
    void* const CNBuffer = malloc(CNBuffSize);
    size_t const compressedBufferSize = FL2_compressBound(CNBuffSize);
    void* const compressedBuffer = malloc(compressedBufferSize);
    FL2_CStream *const cstream = FL2_createCStreamMt(nbThreads, 1);

    FL2_outBuffer out = { compressedBuffer, compressedBufferSize, 0 };
    FL2_dictBuffer dict;
    FL2_cBuffer cbuf;
    size_t r;

    CHECK(FL2_initCStream(cstream, 2));
    FL2_getDictionaryBuffer(cstream, &dict);
    memcpy(dict.dst, CNBuffer, dict.size);
    CHECK_V(res, FL2_updateDictionary(cstream, dict.size));
    r = dict.size;
    FL2_getDictionaryBuffer(cstream, &dict);
    memcpy((BYTE*)dict.dst, (BYTE*)CNBuffer + r, r / 2);
    CHECK(FL2_updateDictionary(cstream, r / 2));
    r = FL2_endStream(cstream, NULL);
    if (r == 0) goto _output_error;
    while (FL2_getNextCompressedBuffer(cstream, &cbuf) != 0) {
        memcpy((BYTE*)out.dst + out.pos, cbuf.src, cbuf.size);
        out.pos += cbuf.size;
    }
    r = FL2_endStream(cstream, NULL);
    if (r == 0) goto _output_error;
    while (FL2_getNextCompressedBuffer(cstream, &cbuf) != 0) {
        memcpy((BYTE*)out.dst + out.pos, cbuf.src, cbuf.size);
        out.pos += cbuf.size;
    }
    r = FL2_endStream(cstream, NULL);
    if (r != 0) goto _output_error;*/

    return success;
}


#ifdef _WIN32
FARPROC funcptr[11];
#endif

jfieldID fields[6];

JNIEXPORT void JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_initNatives(JNIEnv *env, jclass klass) {
    env->SetStaticLongField(klass, env->GetStaticFieldID(klass, "NATIVE_STRUCT_SIZE", "J"), sizeof(FL2_inBuffer));
    static_assert(sizeof(FL2_inBuffer) == sizeof(FL2_outBuffer), "FL2_inBuffer != FL2_outBuffer");

    fields[0] = FID("pCtx", "J");

    fields[1] = FID("pIn", "J");
    fields[2] = FID("inOffset", "I");
    fields[3] = FID("inSize", "I");

    fields[4] = FID("pOut", "J");
    fields[5] = FID("outSize", "I");

    int i = 0;
    for(jfieldID id : fields) {
        if (id == nullptr) {
            Error(env, "LZMA2WriterN field %d not found");
            printf("LZMA2WriterN field %d not found", i);
            return;
        }
        i++;
    }

#ifdef _WIN32
    HINSTANCE fastLzma = LoadLibrary("fast-lzma2");
    if (fastLzma == nullptr) {
        Error(env, "Fast lzma dll not found");
        return;
    }

    funcptr[0] = GetProcAddress(fastLzma, "FL2_createCStream");
    funcptr[1] = GetProcAddress(fastLzma, "FL2_createCStreamMt");
    funcptr[2] = GetProcAddress(fastLzma, "FL2_freeCStream");

    funcptr[3] = GetProcAddress(fastLzma, "FL2_initCStream");
    funcptr[4] = GetProcAddress(fastLzma, "FL2_getDictionaryBuffer");
    funcptr[5] = GetProcAddress(fastLzma, "FL2_updateDictionary");

    funcptr[6] = GetProcAddress(fastLzma, "FL2_compressStream");
    funcptr[7] = GetProcAddress(fastLzma, "FL2_CStream_setParameter");

    funcptr[8] = GetProcAddress(fastLzma, "FL2_flushStream");
    funcptr[9] = GetProcAddress(fastLzma, "FL2_endStream");

    funcptr[10] = GetProcAddress(fastLzma, "FL2_estimateCStreamSize_byParams");

#else
    return false;
#endif

}

JNIEXPORT jlong JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_getMemoryUsage(JNIEnv *, jclass, LZMA_OPTIONS_NATIVE *opt) {
    FL2_compressionParameters param;
    return ((FL2_estimateCStreamSize_byParams)funcptr[10]) (&param, 0, 0);
}

JNIEXPORT jlong JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_nInit(JNIEnv *env, jobject self, LZMA_OPTIONS_NATIVE *opt) {
    auto *ctx = reinterpret_cast<FL2_CStream *>(env->GetLongField(self, fields[0]));
    if (ctx == nullptr) {
        size_t buffer_size = 1048576;

        auto* in_buffer = static_cast<FL2_inBuffer *>(malloc(buffer_size + sizeof(FL2_inBuffer)));
        if (in_buffer == nullptr) {
            Error(env, "out of memory");
            return 0;
        }

        auto* out_buffer = static_cast<FL2_outBuffer *>(malloc(buffer_size + sizeof(FL2_outBuffer)));
        if (out_buffer == nullptr) {
            free(in_buffer);
            Error(env, "out of memory");
            return 0;
        }

        ctx = opt->async > 0 ? ((FL2_createCStream)funcptr[0]) () : ((FL2_createCStreamMt)funcptr[1]) (opt->async, 1);
        if (ctx == nullptr) {
            free(in_buffer);
            free(out_buffer);

            Error(env, "out of memory");
            return 0;
        }

        in_buffer->src = in_buffer + 1;
        in_buffer->size = 0;
        in_buffer->pos = 0;

        out_buffer->dst = out_buffer + 1;
        out_buffer->size = buffer_size;
        //out_buffer->pos = buffer_size;

        env->SetLongField(self, fields[0], reinterpret_cast<jlong>(ctx));

        env->SetLongField(self, fields[1], reinterpret_cast<jlong>(in_buffer));
        env->SetIntField(self, fields[2], 0);
        env->SetIntField(self, fields[3], static_cast<jint>(buffer_size));

        env->SetLongField(self, fields[4], reinterpret_cast<jlong>(out_buffer));
        env->SetIntField(self, fields[5], 0);
    }

    ((FL2_initCStream)funcptr[3]) (ctx, opt->compressionLevel);
    ((FL2_CStream_setParameter) funcptr[7]) (ctx, FL2_p_literalCtxBits, opt->lc);
    ((FL2_CStream_setParameter) funcptr[7]) (ctx, FL2_p_literalPosBits, opt->lp);
    ((FL2_CStream_setParameter) funcptr[7]) (ctx, FL2_p_posBits, opt->pb);
    ((FL2_CStream_setParameter) funcptr[7]) (ctx, FL2_p_fastLength, opt->niceLen);
    ((FL2_CStream_setParameter) funcptr[7]) (ctx, FL2_p_searchDepth, opt->depthLimit);
    ((FL2_CStream_setParameter) funcptr[7]) (ctx, FL2_p_dictionarySize, opt->dictSize);
    ((FL2_CStream_setParameter) funcptr[7]) (ctx, FL2_p_omitProperties, 1);
    ((FL2_CStream_setParameter) funcptr[7]) (ctx, FL2_p_doXXHash, 0);

   if (opt->presetDictLength != 0) {
        FL2_dictBuffer buffer;
        ((FL2_getDictionaryBuffer)funcptr[4]) (ctx, &buffer);

        size_t len = opt->presetDictLength;
        if (len > buffer.size) len = buffer.size;

        memcpy(buffer.dst, opt->presetDict, len);
        ((FL2_updateDictionary)funcptr[5]) (ctx, len);
    }

    return 1;
}
JNIEXPORT jlong JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_nSetProperty(JNIEnv *env, jobject self, jint property_id, jlong property_value) {
    GETCTX

    return ((FL2_CStream_setParameter) funcptr[7]) (ctx, static_cast<FL2_cParameter>(property_id), property_value);
}
JNIEXPORT void JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_nFree(JNIEnv *env, jobject self) {
    auto *ctx = reinterpret_cast<FL2_CStream *>(env->GetLongField(self, fields[0]));
    if (ctx != nullptr) {
        free(reinterpret_cast<void *>(env->GetLongField(self, fields[4])));
        free(reinterpret_cast<void *>(env->GetLongField(self, fields[1])));
        ((FL2_freeCStream)funcptr[2]) (ctx);

        env->SetLongField(self, fields[0], 0);
        env->SetLongField(self, fields[1], 0);
        env->SetLongField(self, fields[4], 0);
    }
}

JNIEXPORT jlong JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_nWrite(JNIEnv *env, jobject self, jint len) {
    GETCTX

    auto *ib = reinterpret_cast<FL2_inBuffer *>(env->GetLongField(self, fields[1]));
    ib->pos = env->GetIntField(self, fields[2]);
    ib->size += len;

    auto *ob = reinterpret_cast<FL2_outBuffer *>(env->GetLongField(self, fields[4]));
    ob->pos = 0;

    size_t retVal = ((FL2_compressStream) funcptr[6]) (ctx, ob, ib);

    if (ib->pos == ib->size) ib->pos = ib->size = 0;
    env->SetIntField(self, fields[2], static_cast<jint>(ib->pos));

    env->SetIntField(self, fields[5], static_cast<jint>(ob->pos));
    return retVal;
}
JNIEXPORT jlong JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_nFlush(JNIEnv *env, jobject self) {
    GETCTX

    auto *ob = reinterpret_cast<FL2_outBuffer *>(env->GetLongField(self, fields[4]));
    ob->pos = 0;
    size_t retVal = ((FL2_flushStream)funcptr[8]) (ctx, ob);
    env->SetIntField(self, fields[5], static_cast<jint>(ob->pos));
    return retVal;
}
JNIEXPORT jlong JNICALL Java_roj_archive_qz_xz_LZMA2WriterN_nFinish(JNIEnv *env, jobject self) {
    GETCTX

    auto *ob = reinterpret_cast<FL2_outBuffer *>(env->GetLongField(self, fields[4]));
    ob->pos = 0;
    size_t retVal = ((FL2_endStream)funcptr[9]) (ctx, ob);
    env->SetIntField(self, fields[5], static_cast<jint>(ob->pos));
    return retVal;
}

#endif