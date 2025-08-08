//
// Created by Roj234 on 2024/4/25 0025.
//

#ifndef LZ_JNI_CPP
#define LZ_JNI_CPP

#include "LZ_Jni.h"
#include "fast-lzma2.h"
#include <Windows.h>

#define FID(p0, p1) env->GetFieldID(klass, p0, p1)
#define GETCTX \
    auto *ctx = reinterpret_cast<FL2_CStream *>(env->GetLongField(self, lz_fieldid[0]));\
    if (ctx == nullptr) {\
        Error(env, "stream closed");\
        return 0;\
    }

/*static inline bool LzJni_init(JNIEnv *env) {
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
    if (r != 0) goto _output_error;
}*/

#ifdef _WIN32
FARPROC lz_funcptr[11];
#endif

jfieldID lz_fieldid[6];

JNIEXPORT jboolean JNICALL Java_roj_archive_xz_LZMA2WriterN_initNatives(JNIEnv *env, jclass klass) {
    env->SetStaticLongField(klass, env->GetStaticFieldID(klass, "NATIVE_STRUCT_SIZE", "J"), sizeof(FL2_inBuffer));
    static_assert(sizeof(FL2_inBuffer) == sizeof(FL2_outBuffer), "FL2_inBuffer != FL2_outBuffer");

    lz_fieldid[0] = FID("pCtx", "J");

    lz_fieldid[1] = FID("pIn", "J");
    lz_fieldid[2] = FID("inOffset", "I");
    lz_fieldid[3] = FID("inSize", "I");

    lz_fieldid[4] = FID("pOut", "J");
    lz_fieldid[5] = FID("outSize", "I");

    for (size_t i = 0; i < 6; i++) {
        if (lz_fieldid[i] == nullptr) {
            Error(env, "Incompatible Class");
            return false;
        }
    }

#ifdef _WIN32
    HINSTANCE fastLzma = LoadLibrary("fast-lzma2");
    if (fastLzma == nullptr) return false;

    auto getVersion = reinterpret_cast<FL2_versionNumber>(GetProcAddress(fastLzma, "FL2_versionNumber"));
    if (getVersion == nullptr || getVersion() != FL2_VERSION_NUMBER) {
        Error(env, "Incompatible Version");
        return false;
    }

    lz_funcptr[0] = GetProcAddress(fastLzma, "FL2_createCStream");
    lz_funcptr[1] = GetProcAddress(fastLzma, "FL2_createCStreamMt");
    lz_funcptr[2] = GetProcAddress(fastLzma, "FL2_freeCStream");

    lz_funcptr[3] = GetProcAddress(fastLzma, "FL2_initCStream");
    lz_funcptr[4] = GetProcAddress(fastLzma, "FL2_getDictionaryBuffer");
    lz_funcptr[5] = GetProcAddress(fastLzma, "FL2_updateDictionary");

    lz_funcptr[6] = GetProcAddress(fastLzma, "FL2_compressStream");
    lz_funcptr[7] = GetProcAddress(fastLzma, "FL2_CStream_setParameter");

    lz_funcptr[8] = GetProcAddress(fastLzma, "FL2_flushStream");
    lz_funcptr[9] = GetProcAddress(fastLzma, "FL2_endStream");

    lz_funcptr[10] = GetProcAddress(fastLzma, "FL2_estimateCStreamSize_byParams");

#else
    return false;
#endif

    return true;
}

JNIEXPORT jlong JNICALL Java_roj_archive_xz_LZMA2WriterN_getMemoryUsage(JNIEnv *, jclass, LZMA_OPTIONS_NATIVE *opt) {
    FL2_compressionParameters param;
    return ((FL2_estimateCStreamSize_byParams)lz_funcptr[10]) (&param, 0, 0);
}

JNIEXPORT jlong JNICALL Java_roj_archive_xz_LZMA2WriterN_nInit(JNIEnv *env, jobject self, LZMA_OPTIONS_NATIVE *opt) {
    auto *ctx = reinterpret_cast<FL2_CStream *>(env->GetLongField(self, lz_fieldid[0]));
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

        ctx = opt->async > 0 ? ((FL2_createCStream)lz_funcptr[0]) () : ((FL2_createCStreamMt)lz_funcptr[1]) (opt->async, 1);
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

        env->SetLongField(self, lz_fieldid[0], reinterpret_cast<jlong>(ctx));

        env->SetLongField(self, lz_fieldid[1], reinterpret_cast<jlong>(in_buffer));
        env->SetIntField(self, lz_fieldid[2], 0);
        env->SetIntField(self, lz_fieldid[3], static_cast<jint>(buffer_size));

        env->SetLongField(self, lz_fieldid[4], reinterpret_cast<jlong>(out_buffer));
        env->SetIntField(self, lz_fieldid[5], 0);
    }

    ((FL2_initCStream)lz_funcptr[3]) (ctx, opt->compressionLevel);
    ((FL2_CStream_setParameter) lz_funcptr[7]) (ctx, FL2_p_literalCtxBits, opt->lc);
    ((FL2_CStream_setParameter) lz_funcptr[7]) (ctx, FL2_p_literalPosBits, opt->lp);
    ((FL2_CStream_setParameter) lz_funcptr[7]) (ctx, FL2_p_posBits, opt->pb);
    ((FL2_CStream_setParameter) lz_funcptr[7]) (ctx, FL2_p_fastLength, opt->niceLen);
    ((FL2_CStream_setParameter) lz_funcptr[7]) (ctx, FL2_p_searchDepth, opt->depthLimit);
    ((FL2_CStream_setParameter) lz_funcptr[7]) (ctx, FL2_p_dictionarySize, opt->dictSize);
    ((FL2_CStream_setParameter) lz_funcptr[7]) (ctx, FL2_p_omitProperties, 1);
    ((FL2_CStream_setParameter) lz_funcptr[7]) (ctx, FL2_p_doXXHash, 0);

   if (opt->presetDictLength != 0) {
        FL2_dictBuffer buffer;
        ((FL2_getDictionaryBuffer)lz_funcptr[4]) (ctx, &buffer);

        size_t len = opt->presetDictLength;
        if (len > buffer.size) len = buffer.size;

        memcpy(buffer.dst, opt->presetDict, len);
        ((FL2_updateDictionary)lz_funcptr[5]) (ctx, len);
    }

    return 1;
}
JNIEXPORT jlong JNICALL Java_roj_archive_xz_LZMA2WriterN_nSetProperty(JNIEnv *env, jobject self, jint property_id, jlong property_value) {
    GETCTX

    return ((FL2_CStream_setParameter) lz_funcptr[7]) (ctx, static_cast<FL2_cParameter>(property_id), property_value);
}
JNIEXPORT void JNICALL Java_roj_archive_xz_LZMA2WriterN_nFree(JNIEnv *env, jobject self) {
    auto *ctx = reinterpret_cast<FL2_CStream *>(env->GetLongField(self, lz_fieldid[0]));
    if (ctx != nullptr) {
        free(reinterpret_cast<void *>(env->GetLongField(self, lz_fieldid[4])));
        free(reinterpret_cast<void *>(env->GetLongField(self, lz_fieldid[1])));
        ((FL2_freeCStream)lz_funcptr[2]) (ctx);

        env->SetLongField(self, lz_fieldid[0], 0);
        env->SetLongField(self, lz_fieldid[1], 0);
        env->SetLongField(self, lz_fieldid[4], 0);
    }
}

JNIEXPORT jlong JNICALL Java_roj_archive_xz_LZMA2WriterN_nWrite(JNIEnv *env, jobject self, jint len) {
    GETCTX

    auto *ib = reinterpret_cast<FL2_inBuffer *>(env->GetLongField(self, lz_fieldid[1]));
    ib->pos = env->GetIntField(self, lz_fieldid[2]);
    ib->size += len;

    auto *ob = reinterpret_cast<FL2_outBuffer *>(env->GetLongField(self, lz_fieldid[4]));
    ob->pos = 0;

    size_t retVal = ((FL2_compressStream) lz_funcptr[6]) (ctx, ob, ib);

    if (ib->pos == ib->size) ib->pos = ib->size = 0;
    env->SetIntField(self, lz_fieldid[2], static_cast<jint>(ib->pos));

    env->SetIntField(self, lz_fieldid[5], static_cast<jint>(ob->pos));
    return retVal;
}
JNIEXPORT jlong JNICALL Java_roj_archive_xz_LZMA2WriterN_nFlush(JNIEnv *env, jobject self) {
    GETCTX

    auto *ob = reinterpret_cast<FL2_outBuffer *>(env->GetLongField(self, lz_fieldid[4]));
    ob->pos = 0;
    size_t retVal = ((FL2_flushStream)lz_funcptr[8]) (ctx, ob);
    env->SetIntField(self, lz_fieldid[5], static_cast<jint>(ob->pos));
    return retVal;
}
JNIEXPORT jlong JNICALL Java_roj_archive_xz_LZMA2WriterN_nFinish(JNIEnv *env, jobject self) {
    GETCTX

    auto *ob = reinterpret_cast<FL2_outBuffer *>(env->GetLongField(self, lz_fieldid[4]));
    ob->pos = 0;
    size_t retVal = ((FL2_endStream)lz_funcptr[9]) (ctx, ob);
    env->SetIntField(self, lz_fieldid[5], static_cast<jint>(ob->pos));
    return retVal;
}

#endif