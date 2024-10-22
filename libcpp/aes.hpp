//
// Created by Roj234 on 2024/9/24 0024.
//

#include <cstdint>
#include <wmmintrin.h>
#include <emmintrin.h>
#include <smmintrin.h>

// 下列是Intel AES-NI指令集白皮书的参考代码
inline __m128i AES_128_ASSIST(__m128i temp1, __m128i temp2) {
    __m128i temp3;
    temp2 = _mm_shuffle_epi32 (temp2 ,0xff);
    temp3 = _mm_slli_si128 (temp1, 0x4);
    temp1 = _mm_xor_si128 (temp1, temp3);
    temp3 = _mm_slli_si128 (temp3, 0x4);
    temp1 = _mm_xor_si128 (temp1, temp3);
    temp3 = _mm_slli_si128 (temp3, 0x4);
    temp1 = _mm_xor_si128 (temp1, temp3);
    temp1 = _mm_xor_si128 (temp1, temp2);
    return temp1;
}
static void AES_128_Key_Expansion(const unsigned char *userkey,unsigned char *key) {
    __m128i temp1, temp2;
    __m128i *Key_Schedule = (__m128i*)key;
    temp1 = _mm_loadu_si128((__m128i*)userkey);
    Key_Schedule[0] = temp1;
    temp2 = _mm_aeskeygenassist_si128 (temp1 ,0x1);
    temp1 = AES_128_ASSIST(temp1, temp2);
    Key_Schedule[1] = temp1;
    temp2 = _mm_aeskeygenassist_si128 (temp1,0x2);
    temp1 = AES_128_ASSIST(temp1, temp2);
    Key_Schedule[2] = temp1;
    temp2 = _mm_aeskeygenassist_si128 (temp1,0x4);
    temp1 = AES_128_ASSIST(temp1, temp2);
    Key_Schedule[3] = temp1;
    temp2 = _mm_aeskeygenassist_si128 (temp1,0x8);
    temp1 = AES_128_ASSIST(temp1, temp2);
    Key_Schedule[4] = temp1;
    temp2 = _mm_aeskeygenassist_si128 (temp1,0x10);
    temp1 = AES_128_ASSIST(temp1, temp2);
    Key_Schedule[5] = temp1;
    temp2 = _mm_aeskeygenassist_si128 (temp1,0x20);
    temp1 = AES_128_ASSIST(temp1, temp2);
    Key_Schedule[6] = temp1;
    temp2 = _mm_aeskeygenassist_si128 (temp1,0x40);
    temp1 = AES_128_ASSIST(temp1, temp2);
    Key_Schedule[7] = temp1;
    temp2 = _mm_aeskeygenassist_si128 (temp1,0x80);
    temp1 = AES_128_ASSIST(temp1, temp2);
    Key_Schedule[8] = temp1;
    temp2 = _mm_aeskeygenassist_si128 (temp1,0x1b);
    temp1 = AES_128_ASSIST(temp1, temp2);
    Key_Schedule[9] = temp1;
    temp2 = _mm_aeskeygenassist_si128 (temp1,0x36);
    temp1 = AES_128_ASSIST(temp1, temp2);
    Key_Schedule[10] = temp1;
}

inline void KEY_192_ASSIST(__m128i* temp1, __m128i * temp2, __m128i * temp3) {
    __m128i temp4;
    *temp2 = _mm_shuffle_epi32 (*temp2, 0x55);
    temp4 = _mm_slli_si128 (*temp1, 0x4);
    *temp1 = _mm_xor_si128 (*temp1, temp4);
    temp4 = _mm_slli_si128 (temp4, 0x4);
    *temp1 = _mm_xor_si128 (*temp1, temp4);
    temp4 = _mm_slli_si128 (temp4, 0x4);
    *temp1 = _mm_xor_si128 (*temp1, temp4);
    *temp1 = _mm_xor_si128 (*temp1, *temp2);
    *temp2 = _mm_shuffle_epi32(*temp1, 0xff);
    temp4 = _mm_slli_si128 (*temp3, 0x4);
    *temp3 = _mm_xor_si128 (*temp3, temp4);
    *temp3 = _mm_xor_si128 (*temp3, *temp2);
}
static void AES_192_Key_Expansion (const unsigned char *userkey, unsigned char *key) {
    __m128i temp1, temp2, temp3, temp4;
    __m128i *Key_Schedule = (__m128i*)key;
    temp1 = _mm_loadu_si128((__m128i*)userkey);
    temp3 = _mm_loadu_si128((__m128i*)(userkey+16));
    Key_Schedule[0]=temp1;
    Key_Schedule[1]=temp3;
    temp2=_mm_aeskeygenassist_si128 (temp3,0x1);
    KEY_192_ASSIST(&temp1, &temp2, &temp3);
    Key_Schedule[1] = (__m128i)_mm_shuffle_pd((__m128d)Key_Schedule[1],
                                              (__m128d)temp1,0);
    Key_Schedule[2] = (__m128i)_mm_shuffle_pd((__m128d)temp1,(__m128d)temp3,1);
    temp2=_mm_aeskeygenassist_si128 (temp3,0x2);
    KEY_192_ASSIST(&temp1, &temp2, &temp3);
    Key_Schedule[3]=temp1;
    Key_Schedule[4]=temp3;
    temp2=_mm_aeskeygenassist_si128 (temp3,0x4);
    KEY_192_ASSIST(&temp1, &temp2, &temp3);
    Key_Schedule[4] = (__m128i)_mm_shuffle_pd((__m128d)Key_Schedule[4],
                                              (__m128d)temp1,0);
    Key_Schedule[5] = (__m128i)_mm_shuffle_pd((__m128d)temp1,(__m128d)temp3,1);
    temp2=_mm_aeskeygenassist_si128 (temp3,0x8);
    KEY_192_ASSIST(&temp1, &temp2, &temp3);
    Key_Schedule[6]=temp1;
    Key_Schedule[7]=temp3;
    temp2=_mm_aeskeygenassist_si128 (temp3,0x10);
    KEY_192_ASSIST(&temp1, &temp2, &temp3);
    Key_Schedule[7] = (__m128i)_mm_shuffle_pd((__m128d)Key_Schedule[7],
                                              (__m128d)temp1,0);
    Key_Schedule[8] = (__m128i)_mm_shuffle_pd((__m128d)temp1,(__m128d)temp3,1);

    temp2=_mm_aeskeygenassist_si128 (temp3,0x20);
    KEY_192_ASSIST(&temp1, &temp2, &temp3);
    Key_Schedule[9]=temp1;
    Key_Schedule[10]=temp3;
    temp2=_mm_aeskeygenassist_si128 (temp3,0x40);
    KEY_192_ASSIST(&temp1, &temp2, &temp3);
    Key_Schedule[10] = (__m128i)_mm_shuffle_pd((__m128d)Key_Schedule[10],
                                               (__m128d)temp1,0);
    Key_Schedule[11] = (__m128i)_mm_shuffle_pd((__m128d)temp1,(__m128d)temp3,1);
    temp2=_mm_aeskeygenassist_si128 (temp3,0x80);
    KEY_192_ASSIST(&temp1, &temp2, &temp3);
    Key_Schedule[12]=temp1;
}

inline void KEY_256_ASSIST_1(__m128i* temp1, __m128i * temp2)
{
    __m128i temp4;
    *temp2 = _mm_shuffle_epi32(*temp2, 0xff);
    temp4 = _mm_slli_si128 (*temp1, 0x4);
    *temp1 = _mm_xor_si128 (*temp1, temp4);
    temp4 = _mm_slli_si128 (temp4, 0x4);
    *temp1 = _mm_xor_si128 (*temp1, temp4);
    temp4 = _mm_slli_si128 (temp4, 0x4);
    *temp1 = _mm_xor_si128 (*temp1, temp4);
    *temp1 = _mm_xor_si128 (*temp1, *temp2);
}
inline void KEY_256_ASSIST_2(__m128i* temp1, __m128i * temp3) {
    __m128i temp2,temp4;
    temp4 = _mm_aeskeygenassist_si128 (*temp1, 0x0);
    temp2 = _mm_shuffle_epi32(temp4, 0xaa);
    temp4 = _mm_slli_si128 (*temp3, 0x4);
    *temp3 = _mm_xor_si128 (*temp3, temp4);
    temp4 = _mm_slli_si128 (temp4, 0x4);
    *temp3 = _mm_xor_si128 (*temp3, temp4);
    temp4 = _mm_slli_si128 (temp4, 0x4);
    *temp3 = _mm_xor_si128 (*temp3, temp4);
    *temp3 = _mm_xor_si128 (*temp3, temp2);
}
static void AES_256_Key_Expansion (const unsigned char *userkey,unsigned char *key) {
    __m128i temp1, temp2, temp3;
    __m128i *Key_Schedule = (__m128i*)key;
    temp1 = _mm_loadu_si128((__m128i*)userkey);
    temp3 = _mm_loadu_si128((__m128i*)(userkey+16));
    Key_Schedule[0] = temp1;
    Key_Schedule[1] = temp3;
    temp2 = _mm_aeskeygenassist_si128 (temp3,0x01);
    KEY_256_ASSIST_1(&temp1, &temp2);
    Key_Schedule[2]=temp1;
    KEY_256_ASSIST_2(&temp1, &temp3);
    Key_Schedule[3]=temp3;
    temp2 = _mm_aeskeygenassist_si128 (temp3,0x02);
    KEY_256_ASSIST_1(&temp1, &temp2);
    Key_Schedule[4]=temp1;
    KEY_256_ASSIST_2(&temp1, &temp3);
    Key_Schedule[5]=temp3;
    temp2 = _mm_aeskeygenassist_si128 (temp3,0x04);
    KEY_256_ASSIST_1(&temp1, &temp2);
    Key_Schedule[6]=temp1;
    KEY_256_ASSIST_2(&temp1, &temp3);
    Key_Schedule[7]=temp3;
    temp2 = _mm_aeskeygenassist_si128 (temp3,0x08);
    KEY_256_ASSIST_1(&temp1, &temp2);
    Key_Schedule[8]=temp1;
    KEY_256_ASSIST_2(&temp1, &temp3);
    Key_Schedule[9]=temp3;
    temp2 = _mm_aeskeygenassist_si128 (temp3,0x10);
    KEY_256_ASSIST_1(&temp1, &temp2);
    Key_Schedule[10]=temp1;
    KEY_256_ASSIST_2(&temp1, &temp3);
    Key_Schedule[11]=temp3;
    temp2 = _mm_aeskeygenassist_si128 (temp3,0x20);
    KEY_256_ASSIST_1(&temp1, &temp2);
    Key_Schedule[12]=temp1;
    KEY_256_ASSIST_2(&temp1, &temp3);
    Key_Schedule[13]=temp3;
    temp2 = _mm_aeskeygenassist_si128 (temp3,0x40);
    KEY_256_ASSIST_1(&temp1, &temp2);
    Key_Schedule[14]=temp1;
}

#if !defined (ALIGN16)
# if defined (__GNUC__)
# define ALIGN16 __attribute__ ( (aligned (16)))
# else
# define ALIGN16 __declspec (align (16))
# endif
#endif

FJNIEXPORT void FJNICALL IL_aes_init(const uint8_t *key, int32_t nr, uint8_t *Ke) {
    if (((uint64_t)Ke) & 15) Ke += 8;

    if (nr == 10) {
        AES_128_Key_Expansion(key, Ke);
    } else if (nr == 12) {
        AES_192_Key_Expansion(key, Ke);
    } else if (nr == 14) {
        AES_256_Key_Expansion(key, Ke);
    }

    auto *EncryptKey = (__m128i*)Ke;
    auto *DecryptKey = (__m128i*)((uintptr_t)Ke + ((nr+1) << 4));

    DecryptKey[nr] = EncryptKey[0];
    DecryptKey[nr - 1] = _mm_aesimc_si128(EncryptKey[1]);
    DecryptKey[nr - 2] = _mm_aesimc_si128(EncryptKey[2]);
    DecryptKey[nr - 3] = _mm_aesimc_si128(EncryptKey[3]);
    DecryptKey[nr - 4] = _mm_aesimc_si128(EncryptKey[4]);
    DecryptKey[nr - 5] = _mm_aesimc_si128(EncryptKey[5]);
    DecryptKey[nr - 6] = _mm_aesimc_si128(EncryptKey[6]);
    DecryptKey[nr - 7] = _mm_aesimc_si128(EncryptKey[7]);
    DecryptKey[nr - 8] = _mm_aesimc_si128(EncryptKey[8]);
    DecryptKey[nr - 9] = _mm_aesimc_si128(EncryptKey[9]);
    if(nr>10){
        DecryptKey[nr - 10] = _mm_aesimc_si128(EncryptKey[10]);
        DecryptKey[nr - 11] = _mm_aesimc_si128(EncryptKey[11]);
    }
    if(nr>12){
        DecryptKey[nr - 12] = _mm_aesimc_si128(EncryptKey[12]);
        DecryptKey[nr - 13] = _mm_aesimc_si128(EncryptKey[13]);
    }
    DecryptKey[0] = EncryptKey[nr];
}
FJNIEXPORT void FJNICALL IL_aes_encrypt(const uint8_t *key, uint8_t number_of_rounds, uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks) {
    if (((uint64_t)key) & 15) key += 8;

    auto *in = reinterpret_cast<uint8_t *>(ip0 + ip1);
    auto *out = reinterpret_cast<uint8_t *>(op0 + op1);

    __m128i tmp;
    int i,j;
    for(i=0; i < blocks; i++){
        tmp = _mm_loadu_si128 (&((__m128i*)in)[i]);
        tmp = _mm_xor_si128 (tmp,((__m128i*)key)[0]);
        for(j=1; j <number_of_rounds; j++){
            tmp = _mm_aesenc_si128 (tmp,((__m128i*)key)[j]);
        }
        tmp = _mm_aesenclast_si128 (tmp,((__m128i*)key)[j]);
        _mm_storeu_si128 (&((__m128i*)out)[i],tmp);
    }
}
FJNIEXPORT void FJNICALL IL_aes_decrypt(const uint8_t *key, uint8_t number_of_rounds, uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks) {
    if (((uint64_t)key) & 15) key += 8;
    key += (number_of_rounds+1) << 4;

    auto *in = reinterpret_cast<uint8_t *>(ip0 + ip1);
    auto *out = reinterpret_cast<uint8_t *>(op0 + op1);

    __m128i tmp;
    int i,j;
    for(i=0; i < blocks; i++){
        tmp = _mm_loadu_si128 (&((__m128i*)in)[i]);
        tmp = _mm_xor_si128 (tmp,((__m128i*)key)[0]);
        for(j=1; j <number_of_rounds; j++){
            tmp = _mm_aesdec_si128 (tmp,((__m128i*)key)[j]);
        }
        tmp = _mm_aesdeclast_si128 (tmp,((__m128i*)key)[j]);
        _mm_storeu_si128 (&((__m128i*)out)[i],tmp);
    }
}

FJNIEXPORT void FJNICALL IL_aes_CBC_encrypt(const uint8_t *key, uint8_t number_of_rounds, const uint8_t ivec[16], uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks) {
    if (((uint64_t)key) & 15) key += 8;

    auto *in = reinterpret_cast<uint8_t *>(ip0 + ip1);
    auto *out = reinterpret_cast<uint8_t *>(op0 + op1);

    __m128i feedback,data;
    int i,j;

    feedback = _mm_loadu_si128 ((__m128i*)ivec);
    for(i=0; i < blocks; i++) {
        data = _mm_loadu_si128 (&((__m128i*)in)[i]);
        feedback = _mm_xor_si128 (data,feedback);
        feedback = _mm_xor_si128 (feedback,((__m128i*)key)[0]);
        for(j=1; j <number_of_rounds; j++)
            feedback = _mm_aesenc_si128 (feedback,((__m128i*)key)[j]);
        feedback = _mm_aesenclast_si128 (feedback,((__m128i*)key)[j]);
        _mm_storeu_si128 (&((__m128i*)out)[i],feedback);
    }
}

FJNIEXPORT void FJNICALL IL_aes_CBC_decrypt(const uint8_t *key, uint8_t number_of_rounds, const uint8_t ivec[16], uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks) {
    if (((uint64_t)key) & 15) key += 8;
    key += (number_of_rounds+1) << 4;

    auto *in = reinterpret_cast<uint8_t *>(ip0 + ip1);
    auto *out = reinterpret_cast<uint8_t *>(op0 + op1);

    __m128i data,feedback,last_in;
    int i,j;

    feedback=_mm_loadu_si128 ((__m128i*)ivec);
    for(i=0; i < blocks; i++){
        last_in=_mm_loadu_si128 (&((__m128i*)in)[i]);
        data = _mm_xor_si128 (last_in,((__m128i*)key)[0]);
        for(j=1; j <number_of_rounds; j++){
            data = _mm_aesdec_si128 (data,((__m128i*)key)[j]);
        }
        data = _mm_aesdeclast_si128 (data,((__m128i*)key)[j]);
        data = _mm_xor_si128 (data,feedback);
        _mm_storeu_si128 (&((__m128i*)out)[i],data);
        feedback=last_in;
    }
}

FJNIEXPORT void FJNICALL IL_aes_CTR(const uint8_t *key, uint8_t number_of_rounds, const uint8_t ivec[16], const uint8_t nonce[4], uint64_t ip0, uint64_t ip1, uint64_t op0, uint64_t op1, int32_t blocks) {
    if (((uint64_t)key) & 15) key += 8;

    auto *in = reinterpret_cast<uint8_t *>(ip0 + ip1);
    auto *out = reinterpret_cast<uint8_t *>(op0 + op1);

    __m128i ctr_block, tmp, ONE, BSWAP_EPI64;
    int i,j;

    ONE = _mm_set_epi32(0,1,0,0);
    BSWAP_EPI64 = _mm_setr_epi8(7,6,5,4,3,2,1,0,15,14,13,12,11,10,9,8);
    ctr_block = _mm_insert_epi64(ctr_block, *(long long*)ivec, 1);
    ctr_block = _mm_insert_epi32(ctr_block, *(long*)nonce, 1);
    ctr_block = _mm_srli_si128(ctr_block, 4);
    ctr_block = _mm_shuffle_epi8(ctr_block, BSWAP_EPI64);
    ctr_block = _mm_add_epi64(ctr_block, ONE);
    for(i=0; i < blocks; i++){
        tmp = _mm_shuffle_epi8(ctr_block, BSWAP_EPI64);
        ctr_block = _mm_add_epi64(ctr_block, ONE);
        tmp = _mm_xor_si128(tmp, ((__m128i*)key)[0]);
        for(j=1; j <number_of_rounds; j++) {
            tmp = _mm_aesenc_si128 (tmp, ((__m128i*)key)[j]);
        };
        tmp = _mm_aesenclast_si128 (tmp, ((__m128i*)key)[j]);
        tmp = _mm_xor_si128(tmp,_mm_loadu_si128(&((__m128i*)in)[i]));
        _mm_storeu_si128 (&((__m128i*)out)[i],tmp);
    }
}