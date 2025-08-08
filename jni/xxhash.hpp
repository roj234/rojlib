//
// Created by Roj234 on 2024/10/21 0021.
//

#define BLOCK_SIZE 16
#define P1 0x9e3779b1
#define P2 0x85ebca7
#define P3 0xc2b2ae3d
#define P4 0x27d4eb2f
#define P5 0x165667b1

static inline uint32_t rotateLeft(uint32_t x, int n) {return (x << n) | (x >> (0x1F & (32 + ~n + 1))) & ~(0xFFFFFFFF << n);}
static inline uint32_t getIntLE(const uint8_t *b, int i) {return *(uint32_t *)((uintptr_t)b+i);}

//非常抱歉这其实只是从Java搬运过来而已，但是应该有些性能提升，没办法XXHash的C语言项目完全看不懂，哈哈，自己做项目的风格还是好一点
FJNIEXPORT uint32_t FJNICALL IL_xxHash32(int32_t seed, uint8_t *buf, int32_t off, int32_t len) {
    uint32_t a,b,c,d;
    // INIT STATE
    a = seed + P1 + P2;
    b = seed + P2;
    c = seed;
    d = seed - P1;
    // INIT STATE
    int32_t end = off + len;
    // BLOCK
    while (end-off >= BLOCK_SIZE) {
        a = rotateLeft(a + getIntLE(buf, off) * P2, 13) * P1;
        b = rotateLeft(b + getIntLE(buf, off + 4) * P2, 13) * P1;
        c = rotateLeft(c + getIntLE(buf, off + 8) * P2, 13) * P1;
        d = rotateLeft(d + getIntLE(buf, off + 12) * P2, 13) * P1;

        off += BLOCK_SIZE;
    }
    // BLOCK

    uint32_t hash = len > BLOCK_SIZE
               ? rotateLeft(a, 1) + rotateLeft(b, 7) + rotateLeft(c, 12) + rotateLeft(d, 18)
               : c + P5;

    hash += len;

    while (end-off >= 4) {
        hash = rotateLeft(hash + getIntLE(buf, off) * P3, 17) * P4;

        off += 4;
    }

    while (end-off > 0) {
        hash = rotateLeft(hash + (buf[off] & 255) * P5, 11) * P1;

        off++;
    }

    hash ^= hash >> 15;
    hash *= P2;
    hash ^= hash >> 13;
    hash *= P3;
    hash ^= hash >> 16;
    return hash;
}