//
// Created by Roj234 on 2024/4/24 0024.
//

#include "roj_NativeLibrary.h"
#include <cstdlib>
#include <atomic>

struct bsdiff {
    bsdiff *owner;
    std::atomic<int> refCount;

    int* sfx;

    const s1* left;
    int leftLen;

    const s1* right;
    int rightLen;

    int scan, lastScan, lastPos, len, lastOffset;

    void* ctx;
};


inline int getV(int V[], int pos, int vLen) { return pos < vLen ? V[pos] : -1; }
inline int toPositive(s1 b) { return b + 128; }
void split(int I[], int V[], int vLen, int start, int len, int h);

bsdiff* bsdiff_init(const s1* left, int size) {
    int bucket[256];
    for(int & i : bucket) i = 0;

    // count
    for (int i = 0; i < size; i++) bucket[toPositive(left[i])]++;
    // cumulative sum
    for (int i = 1; i < 256; i++) bucket[i] += bucket[i-1];
    // move
    for (int i = 255; i > 0; i--) bucket[i] = bucket[i-1];
    bucket[0] = 0;

    auto *ctx = static_cast<bsdiff *>(malloc(sizeof(bsdiff) + sizeof(int) * size));
    if (ctx == nullptr) return nullptr;

    int *sfx = reinterpret_cast<int *>(reinterpret_cast<unsigned long long>(ctx) + sizeof(bsdiff));
    for (int i = 0; i < size; i++) sfx[i] = 0;

    for (int i = 0; i < size; i++) {
        int n = toPositive(left[i]);
        int v = bucket[n]++;
        sfx[v] = i;
    }

    for (int i = 1; i < 256; ++i) {
        if (bucket[i] != bucket[i-1] + 1) continue;
        sfx[bucket[i] - 1] = -1;
    }

    if (bucket[0] == 1) sfx[0] = -1;

    int* V = static_cast<int *>(malloc(size * sizeof(int)));
    if (V == nullptr) {
        free(ctx);
        return nullptr;
    }

    for (int i = 0; i < size; ++i) V[i] = bucket[toPositive(left[i])] - 1;

    int h = 1;
    while (sfx[0] != -size) {
        int j = 0, len = 0;
        while (j < size) {
            if (sfx[j] < 0) {
                len -= sfx[j];
                j -= sfx[j];
                continue;
            }

            if (len > 0) sfx[j - len] = -len;

            int groupLen = V[sfx[j]] - j + 1;
            split(sfx, V, size, j, groupLen, h);

            j += groupLen;
            len = 0;
        }

        if (len > 0) sfx[size - len] = -len;

        h <<= 1;
        if (h < 0) break;
    }

    for (int i = 0; i < size; ++i) sfx[V[i]] = i;

    free(V);

    ctx->owner = nullptr;
    ctx->refCount = 1;
    ctx->sfx = sfx;
    ctx->left = left;
    ctx->leftLen = size;
    return ctx;
}
void split(int I[], int V[], int vLen, int start, int len, int h) {
    int temp;
    if (len < 16) {
        int i = start;
        int k;
        while (i < start + len) {
            int j;
            int X = getV(V, I[i] + h, vLen);
            k = i + 1;
            for (j = i + 1; j < start + len; ++j) {
                if (getV(V, I[j] + h, vLen) < X) {
                    X = getV(V, I[j] + h, vLen);
                    k = i;
                }
                if (getV(V, I[j] + h, vLen) != X) continue;
                temp = I[j];
                I[j] = I[k];
                I[k] = temp;
                ++k;
            }
            for (j = i; j < k; ++j) {
                V[I[j]] = k - 1;
            }
            if (k == i + 1) {
                I[i] = -1;
            }
            i = k;
        }
        return;
    }
    int X = getV(V, I[start + len / 2] + h, vLen);
    int smallCount = 0;
    int equalCount = 0;
    for (int i = 0; i < len; ++i) {
        if (getV(V, I[start + i] + h, vLen) < X) {
            ++smallCount;
            continue;
        }
        if (getV(V, I[start + i] + h, vLen) != X) continue;
        ++equalCount;
    }

    int smallPos = start + smallCount;
    int equalPos = smallPos + equalCount;
    int i = start;
    int j = i + smallCount;
    int k = j + equalCount;
    while (i < smallPos) {
        if (getV(V, I[i] + h, vLen) < X) {
            ++i;
            continue;
        }
        if (getV(V, I[i] + h, vLen) == X) {
            temp = I[i];
            I[i] = I[j];
            I[j] = temp;
            ++j;
            continue;
        }
        temp = I[i];
        I[i] = I[k];
        I[k] = temp;
        ++k;
    }
    while (j < equalPos) {
        if (getV(V, I[j] + h, vLen) == X) {
            ++j;
            continue;
        }
        temp = I[j];
        I[j] = I[k];
        I[k] = temp;
        ++k;
    }

    if (smallPos > start) split(I, V, vLen, start, smallPos - start, h);
    for (i = smallPos; i < equalPos; ++i) V[I[i]] = equalPos - 1;
    if (equalPos == smallPos + 1) I[smallPos] = -1;

    if (equalPos < start + len) split(I, V, vLen, equalPos, len - (equalPos - start), h);
}

void bsdiff_free(bsdiff* ctx) {
    if (ctx->owner != nullptr) bsdiff_free(ctx->owner);
    if (--ctx->refCount == 0) free(ctx);
}

int search(bsdiff* ctx, const s1 *right, int rightOff, const s1 *left, int leftOff, int leftEnd, int *len);

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

void bsdiff_search(bsdiff* ctx, bool (*callback)(bsdiff* ctx, int scan, int lastScan, int pos, int lastPos, int lenB, int lenF, bool hasOverlap)) {
    const s1 *left = ctx->left, *right = ctx->right;
    int leftLen = ctx->leftLen, rightLen = ctx->rightLen;

    int scan = ctx->scan, lastScan = ctx->lastScan;
    int pos = 0, lastPos = ctx->lastPos;
    int len = ctx->len, lastOffset = ctx->lastOffset;
    int prevScan;
    while (scan < rightLen) {
        prevScan = scan;

        int match = 0;
        int scsc = scan += len;
        while (scan < rightLen) {
            pos = search(ctx, right, scan, left, 0, leftLen, &len);
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
        int f = 0;
        int F2 = 0;
        int lenF = 0;
        int i2 = 0;
        while (i2 < scan - lastScan && i2 < leftLen - lastPos) {
            if (right[lastScan + i2] == left[lastPos + i2]) {
                ++f;
            }
            if (2 * f - ++i2 <= 2 * F2 - lenF) continue;
            F2 = f;
            lenF = i2;
        }
        int b = 0;
        int B = 0;
        int lenB = 0;
        if (scan < rightLen) {
            for (int i3 = 1; i3 < scan - lastScan + 1 && i3 < pos + 1; ++i3) {
                if (right[scan - i3] == left[pos - i3]) {
                    ++b;
                }
                if (2 * b - i3 <= 2 * B - lenB) continue;
                B = b;
                lenB = i3;
            }
        }
        int overlap = -1;
        if (lenF + lenB > scan - lastScan) {
            overlap = lastScan + lenF - (scan - lenB);
            int s = 0;
            int S = 0;
            int lenS = 0;
            for (int i4 = 0; i4 < overlap; ++i4) {
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

bool bsdiff_callback_getLength(bsdiff* ctx, int scan, int lastScan, int pos, int lastPos, int lenB, int lenF, bool hasOverlap) {
    int* arr = reinterpret_cast<int*>(ctx->ctx);
    int diffBytes = arr[0];

    for (int i = 0; i < lenF; ++i) {
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

inline void writeU4LE(u1* ptr, int v) {
    *ptr++ = v >>  0;
    *ptr++ = v >>  8;
    *ptr++ = v >> 16;
    *ptr++ = v >> 24;
}

bool bsdiff_callback_genPatch(bsdiff* ctx, int scan, int lastScan, int pos, int lastPos, int lenB, int lenF, bool hasOverlap) {
    auto ref = reinterpret_cast<long long*>(ctx->ctx);
    long long int offset = ref[0];
    long long int length = ref[1];

    u1* p = reinterpret_cast<u1*>(offset);

    int copyLen = hasOverlap ? 0 : scan - lastScan - lenF - lenB;

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

    for (int i = 0; i < lenF; ++i)
        * p++ = toPositive(ctx->left[lastPos + i]) - toPositive(ctx->right[lastScan + i]);

    if (copyLen) {
        int copyOff = lastScan + lenF;

        for(int i = 0; i < copyLen; i++) {
            * p++ = ctx->right[copyOff++];
        }
    }

    ref[0] = reinterpret_cast<long long int>(p);
    ref[1] = length;

    return false;
}

int matchLen(const s1* lData, int lStart, int lLen, const s1* rData, int rStart, int rLen) {
    int i = lStart;
    while (i < lLen && rStart < rLen && lData[i] == rData[rStart]) {
        i++;
        rStart++;
    }
    return i - lStart;
}

int search(bsdiff* ctx, const s1 *right, int rightOff, const s1 *left, int leftOff, int leftEnd, int *len) {
    int* sfx = ctx->sfx;

    loop:
    while (true) {
        int leftLen = leftEnd - leftOff;
        if (leftLen < 2) {
            int len1 = matchLen(left, sfx[leftOff], ctx->leftLen, right, rightOff, ctx->rightLen);
            if (leftLen > 0 && leftEnd < ctx->leftLen) {
                int len2 = matchLen(left, sfx[leftEnd], ctx->leftLen, right, rightOff, ctx->rightLen);
                if (len2 >= len1) {
                    *len = len2;
                    return sfx[leftEnd];
                }
            }

            *len = len1;
            return sfx[leftOff];
        }

        // 二分查找 log2(n)
        int mid = leftLen/2 + leftOff;

        int i = sfx[mid], j = rightOff;
        int max = i + min(ctx->leftLen - i, ctx->rightLen - rightOff);

        while (i < max) {
            //__asm inline ("cmp ext,#1");
            if (left[i] < right[j]) {
                // 小于
                leftOff = mid;
                goto loop;
            }
            if (left[i] > right[j]) break;

            i++;
            j++;
        }

        // 大于和等于
        leftEnd = mid;
    }
}





JNIEXPORT jlong JNICALL Java_roj_util_BsDiff_nCreate(JNIEnv *env, jclass, jlong left, jint leftLen) {
    bsdiff* ctx = bsdiff_init(reinterpret_cast<const s1 *>(left), leftLen);
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
    ctx->right = reinterpret_cast<s1 *>(right);
    ctx->rightLen = rightLen;
    bsdiff_ctx_init(ctx);

    int ref[2];
    ref[0] = 0;
    ref[1] = maxDiff;
    ctx->ctx = &ref;

    bsdiff_search(ctx, bsdiff_callback_getLength);

    return ref[0];
}
JNIEXPORT jint JNICALL Java_roj_util_BsDiff_nGenPatch(JNIEnv *env, jclass, jlong ptr, jlong right, jint rightLen, jlong out, jint outLen) {
    auto ctx = reinterpret_cast<bsdiff *>(ptr);
    ctx->right = reinterpret_cast<s1 *>(right);
    ctx->rightLen = rightLen;
    bsdiff_ctx_init(ctx);

    long long ref[2];
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