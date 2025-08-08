//
// Created by Roj234 on 2024/4/24 0024.
//

#include <divsufsort.h>
#include <string.h>

static inline int32_t min(int32_t l, int32_t r) {return l < r ? l : r;}
static inline int32_t toPositive(uint8_t b) { return b + 128; }

static int32_t search(bsdiff* ctx, const uint8_t *right, int32_t rightLen, const uint8_t *left, int32_t leftStart, int32_t leftEnd, int32_t *len);

static void bsdiff_ctx_init(bsdiff *ctx) {
    ctx->scan = 0;
    ctx->lastScan = 0;
    ctx->lastPos = 0;
    ctx->len = 0;
    ctx->lastOffset = 0;
}

static void bsdiff_search(bsdiff* ctx, bool (*callback)(bsdiff* ctx, int32_t scan, int32_t lastScan, int32_t pos, int32_t lastPos, int32_t lenB, int32_t lenF, bool hasOverlap)) {
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

static bool bsdiff_callback_getLength(bsdiff* ctx, int32_t scan, int32_t lastScan, int32_t pos, int32_t lastPos, int32_t lenB, int32_t lenF, bool hasOverlap) {
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

static inline void writeU4LE(uint8_t* ptr, int32_t v) {
    *ptr++ = v >>  0;
    *ptr++ = v >>  8;
    *ptr++ = v >> 16;
    *ptr++ = v >> 24;
}

static bool bsdiff_callback_genPatch(bsdiff* ctx, int32_t scan, int32_t lastScan, int32_t pos, int32_t lastPos, int32_t lenB, int32_t lenF, bool hasOverlap) {
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

static int32_t matchLen(const uint8_t* a, int32_t a_size, const uint8_t* b, int32_t b_size) {
    int32_t i = 0;
    int32_t len = min(a_size, b_size);
    while (i < len && a[i] == b[i]) i++;
    return i;
}

static int32_t search(bsdiff* ctx, const uint8_t *right, int32_t rightLen, const uint8_t *left, int32_t leftStart, int32_t leftEnd, int32_t *len) {
    const int32_t* sfx = ctx->sfx;

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

static inline int32_t ArrayLength(void *array) {return * (int32_t *)((uint64_t)array - 4);}

FJNIEXPORT int FJNICALL IL_bsdiff_init(uint8_t *left, int32_t *sfx, int32_t size) {return divsufsort(left, sfx, size);}

FJNIEXPORT bsdiff* FJNICALL IL_bsdiff_newCtx() {
    auto ptr = malloc(sizeof(bsdiff));
    memset(ptr, 0, sizeof(bsdiff));
    return static_cast<bsdiff *>(ptr);
}
FJNIEXPORT void FJNICALL IL_bsdiff_freeCtx(void *ptr) {free(ptr);}

FJNIEXPORT int FJNICALL IL_bsdiff_makePatch(const int32_t *sfx, const uint8_t *left, bsdiff *ctx, const uint8_t *right, uint64_t ip0, uint64_t ip1, int32_t outSize) {
    ctx->sfx = sfx;
    ctx->left = left;
    ctx->leftLen = ArrayLength((void *) left);
    ctx->right = right;
    ctx->rightLen = ArrayLength((void *) right);

    int64_t ref[2];
    ref[0] = ip0 + ip1;
    ref[1] = outSize;
    ctx->ctx = &ref;

    bsdiff_search(ctx, bsdiff_callback_genPatch);
    return ref[1];
}

FJNIEXPORT int FJNICALL IL_bsdiff_getDiffLength(const int32_t *sfx, const uint8_t *left, const uint8_t *right, int32_t off, int32_t len, int32_t maxDiff) {
    int32_t ref[2] = {0, maxDiff};

    bsdiff ctx {
            sfx,
            left,
            ArrayLength((void *) left),

            right,
            len,

            off,0,0,0,0,

            &ref
    };

    bsdiff_search(&ctx, bsdiff_callback_getLength);

    return ref[0];
}
