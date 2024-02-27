//
// Created by Roj234 on 2024/4/24 0024.
//

#include <divsufsort.h>

#include "roj_NativeLibrary.h"
#include <cstdlib>
#include <atomic>
#include <cstring>

struct bsdiff {
    bsdiff *owner;
    std::atomic<int32_t> refCount;

    int32_t* sfx;

    const uint8_t* left;
    int32_t leftLen;

    const uint8_t* right;
    int32_t rightLen;

    int32_t scan, lastScan, lastPos, len, lastOffset;

    void* ctx;
};


inline int32_t toPositive(uint8_t b) { return b + 128; }

bsdiff* bsdiff_init(const uint8_t* left, int32_t size) {
    auto *addr = reinterpret_cast<uint8_t *>(malloc(sizeof(bsdiff) + sizeof(int32_t) * size));
    if (addr == nullptr) return nullptr;

    auto *sfx = reinterpret_cast<int32_t *>(addr + sizeof(bsdiff));
    if (divsufsort(left, sfx, size) != 0) {
        free(addr);
        return nullptr;
    }

    auto *ctx = reinterpret_cast<bsdiff *>(addr);
    ctx->owner = nullptr;
    ctx->refCount = 1;
    ctx->sfx = sfx;
    ctx->left = left;
    ctx->leftLen = size;
    return ctx;
}

void bsdiff_free(bsdiff* ctx) {
    if (ctx->owner != nullptr) bsdiff_free(ctx->owner);
    if (--ctx->refCount == 0) free(ctx);
}

int32_t search(bsdiff* ctx, const uint8_t *right, int32_t rightLen, const uint8_t *left, int32_t leftStart, int32_t leftEnd, int32_t *len);

void bsdiff_ctx_init(bsdiff *ctx) {
    ctx->scan = 0;
    ctx->lastScan = 0;
    ctx->lastPos = 0;
    ctx->len = 0;
    ctx->lastOffset = 0;
}

bsdiff* bsdiff_ctx_copy(bsdiff* ctx) {
    auto *out = static_cast<bsdiff *>(malloc(sizeof(bsdiff)));
    if (out == nullptr) return nullptr;

    if (ctx->owner != nullptr) ctx = ctx->owner;
    ctx->refCount ++;

    out->owner = ctx;
    out->refCount = 1;
    out->sfx = ctx->sfx;
    out->left = ctx->left;
    out->leftLen = ctx->leftLen;
    return out;
}

void bsdiff_search(bsdiff* ctx, bool (*callback)(bsdiff* ctx, int32_t scan, int32_t lastScan, int32_t pos, int32_t lastPos, int32_t lenB, int32_t lenF, bool hasOverlap)) {
    const uint8_t *left = ctx->left, *right = ctx->right;
    int32_t leftLen = ctx->leftLen, rightLen = ctx->rightLen;

    int32_t scan = ctx->scan, lastScan = ctx->lastScan;
    int32_t pos = 0, lastPos = ctx->lastPos;
    int32_t len = ctx->len, lastOffset = ctx->lastOffset;
    int32_t prevScan;
    while (scan < rightLen) {
        prevScan = scan;

        int32_t match = 0;
        int32_t scsc = scan += len;
        while (scan < rightLen) {
            pos = search(ctx, right+scan, rightLen-scan, left, 0, leftLen, &len);
            while (scsc < scan + len) {
                if (scsc + lastOffset < leftLen && left[scsc + lastOffset] == right[scsc]) {
                    ++match;
                }
                ++scsc;
            }
            if (len == match && len != 0 || len > match + 8) break;
            if (scan + lastOffset < leftLen && left[scan + lastOffset] == right[scan]) {
                --match;
            }
            ++scan;
        }
        if (len == match && scan != rightLen) continue;
        int32_t f = 0;
        int32_t F2 = 0;
        int32_t lenF = 0;
        int32_t i2 = 0;
        while (i2 < scan - lastScan && i2 < leftLen - lastPos) {
            if (right[lastScan + i2] == left[lastPos + i2]) {
                ++f;
            }
            if (2 * f - ++i2 <= 2 * F2 - lenF) continue;
            F2 = f;
            lenF = i2;
        }
        int32_t b = 0;
        int32_t B = 0;
        int32_t lenB = 0;
        if (scan < rightLen) {
            for (int32_t i3 = 1; i3 < scan - lastScan + 1 && i3 < pos + 1; ++i3) {
                if (right[scan - i3] == left[pos - i3]) {
                    ++b;
                }
                if (2 * b - i3 <= 2 * B - lenB) continue;
                B = b;
                lenB = i3;
            }
        }
        int32_t overlap = -1;
        if (lenF + lenB > scan - lastScan) {
            overlap = lastScan + lenF - (scan - lenB);
            int32_t s = 0;
            int32_t S = 0;
            int32_t lenS = 0;
            for (int32_t i4 = 0; i4 < overlap; ++i4) {
                if (left[lastPos + lenF - overlap + i4] == right[lastScan + lenF - overlap + i4]) {
                    ++s;
                }
                if (left[pos - lenB + i4] == right[scan - lenB + i4]) {
                    --s;
                }
                if (s <= S) continue;
                S = s;
                lenS = i4;
            }
            lenF = lenF - overlap + lenS;
            lenB -= lenS;
        }

        if (callback(ctx, scan, lastScan, pos, lastPos, lenB, lenF, overlap != -1)) {
            scan = prevScan;
            break;
        }

        lastPos = pos-lenB;
        lastScan = scan-lenB;
        lastOffset = pos-scan;
    }


    ctx->scan = scan;
    ctx->lastScan = lastScan;
    ctx->lastPos = lastPos;
    ctx->len = len;
    ctx->lastOffset = lastOffset;
}

bool bsdiff_callback_getLength(bsdiff* ctx, int32_t scan, int32_t lastScan, int32_t pos, int32_t lastPos, int32_t lenB, int32_t lenF, bool hasOverlap) {
    auto* arr = reinterpret_cast<int32_t*>(ctx->ctx);
    int32_t diffBytes = arr[0];

    for (int32_t i = 0; i < lenF; ++i) {
        if (ctx->left[lastPos+i] != ctx->right[lastScan+i]) {
            diffBytes++;
        }
    }

    if (!hasOverlap) diffBytes += scan - lastScan - lenF - lenB;

    if (diffBytes > arr[1]) {
        arr[0] = -1;
        return true;
    }
    arr[0] = diffBytes;

    return false;
}

inline void writeU4LE(uint8_t* ptr, int32_t v) {
    *ptr++ = v >>  0;
    *ptr++ = v >>  8;
    *ptr++ = v >> 16;
    *ptr++ = v >> 24;
}

bool bsdiff_callback_genPatch(bsdiff* ctx, int32_t scan, int32_t lastScan, int32_t pos, int32_t lastPos, int32_t lenB, int32_t lenF, bool hasOverlap) {
    auto ref = reinterpret_cast<int64_t*>(ctx->ctx);
    int64_t offset = ref[0];
    int64_t length = ref[1];

    auto* p = reinterpret_cast<uint8_t*>(offset);

    int32_t copyLen = hasOverlap ? 0 : scan - lastScan - lenF - lenB;

    length -= 12 + lenF + copyLen;

    if (length < 0) {
        ref[0] = -1;
        return true;
    }

    p += offset;

    writeU4LE(p, lenF);
    p += 4;
    writeU4LE(p, scan - lastScan - lenF - lenB);
    p += 4;
    writeU4LE(p, pos - lastPos - lenF - lenB);
    p += 4;

    for (int32_t i = 0; i < lenF; ++i)
        * p++ = toPositive(ctx->left[lastPos + i]) - toPositive(ctx->right[lastScan + i]);

    if (copyLen) {
        int32_t copyOff = lastScan + lenF;

        for(int32_t i = 0; i < copyLen; i++) {
            * p++ = ctx->right[copyOff++];
        }
    }

    ref[0] = reinterpret_cast<int64_t>(p);
    ref[1] = length;

    return false;
}

int32_t matchLen(const uint8_t* a, int32_t a_size, const uint8_t* b, int32_t b_size) {
    int32_t i = 0;
    int32_t len = min(a_size, b_size);
    while (i < len && a[i] == b[i]) i++;
    return i;
}

int32_t search(bsdiff* ctx, const uint8_t *right, int32_t rightLen, const uint8_t *left, int32_t leftStart, int32_t leftEnd, int32_t *len) {
    int32_t* sfx = ctx->sfx;

    while (true) {
        int32_t leftLen = leftEnd - leftStart;
        if (leftLen < 2) {
            int32_t len1 = matchLen(left+sfx[leftStart], ctx->leftLen - sfx[leftStart], right, rightLen);
            int32_t len2 = matchLen(left+sfx[leftEnd], ctx->leftLen - sfx[leftEnd], right, rightLen);

            if (len1 > len2) {
                *len = len1;
                return sfx[leftStart];
            } else {
                *len = len2;
                return sfx[leftEnd];
            }
        }

        int32_t mid = leftLen/2 + leftStart;
        if (memcmp(left+sfx[mid], right, min(ctx->leftLen - sfx[mid], rightLen)) < 0) {
            leftStart = mid;
        } else {
            leftEnd = mid;
        }
    }
}





JNIEXPORT jlong JNICALL Java_roj_util_BsDiff_nCreate(JNIEnv *env, jclass, jlong left, jint leftLen) {
    bsdiff* ctx = bsdiff_init(reinterpret_cast<const uint8_t *>(left), leftLen);
    if (ctx == nullptr) {
        Error(env, "out of memory");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}
JNIEXPORT jlong JNICALL Java_roj_util_BsDiff_nCopy(JNIEnv *env, jclass, jlong ptr) {
    bsdiff* ctx = bsdiff_ctx_copy(reinterpret_cast<bsdiff *>(ptr));
    if (ctx == nullptr) {
        Error(env, "out of memory");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}
JNIEXPORT jint JNICALL Java_roj_util_BsDiff_nGetDiffLength(JNIEnv *, jclass, jlong ptr, jlong right, jint rightLen, jint maxDiff) {
    auto ctx = reinterpret_cast<bsdiff *>(ptr);
    ctx->right = reinterpret_cast<uint8_t *>(right);
    ctx->rightLen = rightLen;
    bsdiff_ctx_init(ctx);

    int32_t ref[2];
    ref[0] = 0;
    ref[1] = maxDiff;
    ctx->ctx = &ref;

    bsdiff_search(ctx, bsdiff_callback_getLength);

    return ref[0];
}
JNIEXPORT jint JNICALL Java_roj_util_BsDiff_nGenPatch(JNIEnv *env, jclass, jlong ptr, jlong right, jint rightLen, jlong out, jint outLen) {
    auto ctx = reinterpret_cast<bsdiff *>(ptr);
    ctx->right = reinterpret_cast<uint8_t *>(right);
    ctx->rightLen = rightLen;
    bsdiff_ctx_init(ctx);

    int64_t ref[2];
    ref[0] = out;
    ref[1] = outLen;
    ctx->ctx = &ref;

    bsdiff_search(ctx, bsdiff_callback_genPatch);

    if (ref[0] == -1) {
        Error(env, "buffer overflow");
        return -1;
    }

    return static_cast<jint>(outLen - ref[1]);
}
JNIEXPORT void JNICALL Java_roj_util_BsDiff_nClose(JNIEnv *env, jclass, jlong ptr) {
    bsdiff_free(reinterpret_cast<bsdiff *>(ptr));
}