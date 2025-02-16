package roj.math;

import roj.WillChange;
import roj.io.source.FileSource;
import roj.media.audio.MemoryAudioOutput;
import roj.media.audio.mp3.MP3Decoder;
import roj.util.DynByteBuf;

import java.io.File;
import java.util.Arrays;

/**
 * Vec2d用作复数。 x为实数部分，y为复数部分
 * @author Roj234
 * @since 2025/2/8
 */
public class FFT {
    // 快速傅里叶变换
    public static Vec2d[] fft(@WillChange Vec2d[] data) {
        int n = data.length;
        int numBits = 31 - Integer.numberOfLeadingZeros(n);

        // 位反转函数，用于重新排列输入数组
        for (int i = 0; i < n; i++) {
            int rev = 0;
            for (int j = 0; j < numBits; j++) {
                rev = (rev << 1) | ((i >> j) & 1);
            }
            if (i < rev) {
                Vec2d temp = data[i];
                data[i] = data[rev];
                data[rev] = temp;
            }
        }

        for (int s = 1; s <= numBits; s++) {
            int m = 1 << s;
            double wmRe = Math.cos(-2 * Math.PI / m);
            double wmIm = Math.sin(-2 * Math.PI / m);

            int halfm = m >> 1;
            int mask = halfm - 1;

            double wRe = 1;
            double wIm = 0;

            for (int i = 0; i < n;) {
                var B = data[i + halfm];
                double bRe = wRe * B.x - wIm * B.y,
                       bIm = wRe * B.y + wIm * B.x;

                var A = data[i];
                double aRe = A.x, aIm = A.y;

                A.x = aRe + bRe;
                A.y = aIm + bIm;

                B.x = aRe - bRe;
                B.y = aIm - bIm;

                if ((++i & mask) == 0) {
                    i += halfm;
                    wRe = 1;
                    wIm = 0;
                } else {
                    // w *= wm
                    double tempRe = wRe * wmRe - wIm * wmIm;
                    wIm = wRe * wmIm + wIm * wmRe;
                    wRe = tempRe;
                }
            }
        }

        return data;
    }

    // 逆快速傅里叶变换
    public static Vec2d[] ifft(@WillChange Vec2d[] data) {
        int n = data.length;
        for (Vec2d conjugate : data) {
            conjugate.y = -conjugate.y;
        }
        fft(data);
        for (Vec2d result : data) {
            result.x /= n;
            result.y = -result.y / n;
        }
        return data;
    }

    // 将 16 位有符号整数的 PCM 数据转换为 Vec2d 数组
    public static Vec2d[] pcmToComplex(short[] pcmData) {
        Vec2d[] Vec2dData = new Vec2d[pcmData.length];
        for (int i = 0; i < pcmData.length; i++) {
            Vec2dData[i] = new Vec2d(pcmData[i], 0);
        }
        return Vec2dData;
    }

    // 将 Vec2d 数组转换为 16 位有符号整数的 PCM 数据
    public static short[] complexToPcm(Vec2d[] complexData) {
        short[] pcmData = new short[complexData.length];
        for (int i = 0; i < complexData.length; i++) {
            double value = complexData[i].x;
            // 限制值在 short 范围内
            if (value > Short.MAX_VALUE) {
                value = Short.MAX_VALUE;
            } else if (value < Short.MIN_VALUE) {
                value = Short.MIN_VALUE;
            }
            pcmData[i] = (short) value;
        }
        return pcmData;
    }

    // 调整特定频率的强度
    public static Vec2d[] adjustFrequency(Vec2d[] fftResult, double sampleRate, double increaseFreq, double decreaseFreq, double increaseFactor, double decreaseFactor) {
        int n = fftResult.length;
        // 计算 2000Hz 和 3000Hz 对应的频率索引
        int increaseIndex = (int) (increaseFreq * n / sampleRate);
        int decreaseIndex = (int) (decreaseFreq * n / sampleRate);

        // 增加 2000Hz 频率的强度
        fftResult[increaseIndex] = fftResult[increaseIndex].mul(increaseFactor);
        // 降低 3000Hz 频率的强度
        fftResult[decreaseIndex] = fftResult[decreaseIndex].mul(decreaseFactor);

        return fftResult;
    }

    // 生成频谱
    public static void generateSpectrum(short[] pcmData, double sampleRate) {
        // 将 PCM 数据转换为 Vec2d 数组
        Vec2d[] complexData = pcmToComplex(pcmData);

        // 进行 FFT
        Vec2d[] fftResult = fft(complexData);

        int n = fftResult.length;
        double[] magnitudeSpectrum = new double[n / 2];
        double[] frequencyAxis = new double[n / 2];

        // 计算幅度谱
        for (int i = 0; i < n / 2; i++) {
            magnitudeSpectrum[i] = fftResult[i].length();
            // 计算频率轴
            frequencyAxis[i] = i * sampleRate / n;
        }

        // 输出频谱信息
        System.out.println("频率 (Hz)\t幅度");
        for (int i = 0; i < n / 2; i++) {
            System.out.printf("%.2f\t\t%.2f\n", frequencyAxis[i], magnitudeSpectrum[i]);
        }
    }

    public static short[] continuousProcessWithHanning(short[] pcmData, double sampleRate, int blockSize, int overlap, double increaseFreq, double decreaseFreq, double increaseFactor, double decreaseFactor) {
        int stepSize = blockSize - overlap;
        int numBlocks = (pcmData.length - overlap + stepSize - 1) / stepSize;
        short[] processedData = new short[pcmData.length];
        double[] hanningWindow = new double[blockSize];
        for (int i = 0; i < blockSize; i++) {
            hanningWindow[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (blockSize - 1));
        }

        for (int i = 0; i < numBlocks; i++) {
            int startIndex = i * stepSize;
            int endIndex = Math.min(startIndex + blockSize, pcmData.length);
            short[] block = Arrays.copyOfRange(pcmData, startIndex, endIndex);

            if (block.length < blockSize) {
                short[] paddedBlock = new short[blockSize];
                System.arraycopy(block, 0, paddedBlock, 0, block.length);
                block = paddedBlock;
            }

            Vec2d[] complexBlock = pcmToComplex(block);
            fft(complexBlock);
            complexBlock = adjustFrequency(complexBlock, sampleRate, increaseFreq, decreaseFreq, increaseFactor, decreaseFactor);
            ifft(complexBlock);
            short[] processedBlock = complexToPcm(complexBlock);

            for (int j = 0; j < endIndex - startIndex; j++) {
                int outputIndex = startIndex + j;
                if (outputIndex < processedData.length) {
                    double weight = hanningWindow[j];
                    processedData[outputIndex] = (short) (processedData[outputIndex] * (1 - weight) + processedBlock[j] * weight);
                }
            }
        }
        return processedData;
    }

    public static void main(String[] args) throws Exception {
        var decoder = new MP3Decoder();
        MemoryAudioOutput pcm = new MemoryAudioOutput(DynByteBuf.allocate(65536,65536));
        decoder.open(new FileSource(new File(args[0])), pcm, false);
        try {
            decoder.decodeLoop();
        } catch (Exception e) {
            e.printStackTrace();
        }

        double sampleRate = pcm.format.getSampleRate();
        short[] pcmData = new short[256];
        pcm.buf.rIndex = 16384;
        for (int i = 0; i < 256; i++) {
            pcmData[i] = pcm.buf.readShort();
        }

        // 转换为 Vec2d 数组
        Vec2d[] complexData = pcmToComplex(pcmData);

        // 进行 FFT
        Vec2d[] fftResult = fft(complexData);
        System.out.println("FFT 结果:");
        for (Vec2d c : fftResult) {
            //System.out.println(c.re + " + " + c.im + "i");
        }

        // 进行 IFFT
        Vec2d[] ifftResult = ifft(fftResult);
        System.out.println("\nIFFT 结果:");
        for (Vec2d c : ifftResult) {
            //System.out.println(c.re + " + " + c.im + "i");
        }

        // 生成频谱
        generateSpectrum(pcmData, sampleRate);
    }
}

