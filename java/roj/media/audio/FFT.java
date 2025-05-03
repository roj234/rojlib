package roj.media.audio;

import roj.WillChange;
import roj.math.MathUtils;
import roj.math.Vec2d;

/**
 * Vec2d用作复数。 x为实数部分，y为复数部分
 * @author Roj234
 * @since 2025/2/8
 */
public class FFT {
    /**
     * 执行快速傅里叶变换.
     * <p>
     * 该方法执行原位运算，直接修改输入数组。<br>
     * 未对结果进行缩放，以方便IFFT调用。<br>
     * 如需物理正确的幅度值，需手动调用{@link #scale(Vec2d[], double)}进行缩放<br>
     * <br>
     * <h3>数学公式:</h3>
     * <pre>X[k] = Σ_{n=0}^{N-1} x[n] * e^(-2πi/N * kn)</pre>
     * <br>
     * <h3>典型用法:</h3>
     * <pre>{@code
     * Vec2d[] signal = ...; // 时域信号
     * fft(signal);         // 执行FFT
     * scale(signal, 1.0/signal.length); // 缩放得到正确幅度
     * }</pre>
     *
     * @param data 输入时域信号
     * @return 修改后的输入数组，包含频域数据
     * @throws IllegalArgumentException 如果数据长度不是2的幂
     * @see #ifft(Vec2d[])
     * @see #scale(Vec2d[], double)
     */
    public static Vec2d[] fft(@WillChange Vec2d[] data) {fft(data, - Math.PI);return data;}

    /**
     * 执行逆快速傅里叶变换
     * <p>
     * 等效于以下操作：
     * <ol>
     *   <li>对输入数据取共轭</li>
     *   <li>执行正向FFT</li>
     *   <li>再次取共轭</li>
     *   <li>缩放1/N倍</li>
     * </ol>
     *
     * @param data 复数频域数据
     * @return 修改后的输入数组，包含时域信号
     * @throws IllegalArgumentException 如果数据长度不是2的幂
     * @apiNote 自动缩放，结果直接为时域信号
     * @see #fft(Vec2d[])
     */
    public static Vec2d[] ifft(@WillChange Vec2d[] data) {
        // 使用反向旋转因子内联共轭操作
        fft(data, Math.PI);
        scale(data, 1.0 / data.length);
        return data;
    }

    /**
     * 执行快速傅里叶变换
     *
     * @param data 时域或频域数据,原位修改
     * @param angle 旋转角度
     *              <ul>
     *                <li>{@link Math#PI} 用于逆变换</li>
     *                <li>-{@link Math#PI} 用于正变换</li>
     *              </ul>
     * @throws IllegalArgumentException 如果数据长度不是2的幂
     * @implNote 迭代Cooley-Tukey算法, O(N * log N)
     */
    public static void fft(@WillChange Vec2d[] data, double angle) {
        int n = data.length;
        if ((n & (n - 1)) != 0) throw new IllegalArgumentException("数据长度必须是2的幂");
        int numBits = 31 - Integer.numberOfLeadingZeros(n);

        for (int i = 0; i < n; i++) {
            //int rev = Integer.reverse(i << (32 - numBits));
            int rev = Integer.reverse(i) >>> (32 - numBits);
            if (i < rev) {
                Vec2d temp = data[i];
                data[i] = data[rev];
                data[rev] = temp;
            }
        }

        for (int s = 0; s < numBits; s++) {
            int halfm = 1 << s;
            int mask = halfm - 1;

            double wmRe = Math.cos(angle / halfm);
            double wmIm = Math.sin(angle / halfm);

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
    }

    /**
     * 对复数数组执行标量缩放操作
     * <p>
     * 用于FFT/IFFT后的幅度修正，满足能量守恒：
     * <pre>scale(fft(data), 1.0/N) → 物理正确幅度</pre>
     *
     * @param data 待缩放数组
     * @param factor 缩放因子，典型值：1.0/N
     * @see #fft(Vec2d[])
     * @see #ifft(Vec2d[])
     */
    public static void scale(@WillChange Vec2d[] data, double factor) {
        for (Vec2d v : data) {
            v.x *= factor;
            v.y *= factor;
        }
    }

    public static Vec2d[] newComplexArray(int size) {
        Vec2d[] complex = new Vec2d[size];
        for (int i = 0; i < size; i++) {
            complex[i] = new Vec2d();
        }
        return complex;
    }

    // 将 16 位有符号整数的 PCM 数据转换为 Vec2d 数组
    public static Vec2d[] pcmToComplex(short[] pcmData, Vec2d[] complexData) {
        for (int i = 0; i < pcmData.length; i++) {
            complexData[i].set(pcmData[i], 0);
        }
        return complexData;
    }

    // 将 Vec2d 数组转换为 16 位有符号整数的 PCM 数据
    public static short[] complexToPcm(Vec2d[] complexData, short[] pcmData) {
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

    public static double[] complexToArray(Vec2d[] complexData) {
        double[] array = new double[complexData.length << 1];
        for (int i = 0; i < complexData.length; i++) {
            array[i<<1] = complexData[i].x;
            array[(i<<1) + 1] = complexData[i].y;
        }
        return array;
    }

    public static Vec2d[] arrayToComplex(double[] array) {
        Vec2d[] complexData = new Vec2d[array.length >> 1];
        for (int i = 0; i < array.length; i += 2) {
            complexData[i>>1] = new Vec2d(array[i], array[i+1]);
        }
        return complexData;
    }

    public static void displaySpectrum(Vec2d[] fftResult, int width, int height) {
        int validLength = fftResult.length / 2;
        double[] magnitudes = new double[validLength];

        for (int i = 0; i < validLength; i++) {
            magnitudes[i] = fftResult[i].length();
        }

        // 将频谱数据分组到指定宽度
        int samplesPerBand = validLength / width;
        int remainder = validLength % width;
        double[] bands = new double[width];
        int currentIndex = 0;

        for (int i = 0; i < width; i++) {
            int bandSize = samplesPerBand + (i < remainder ? 1 : 0);
            double max = 0;

            for (int j = 0; j < bandSize; j++) {
                if (currentIndex + j >= magnitudes.length) break;
                max = Math.max(max, magnitudes[currentIndex + j]);
            }

            bands[i] = max;
            currentIndex += bandSize;
        }

        // 转换为分贝并归一化
        final double dynamicRange = 90.0; // 动态范围
        double minDB = Double.MAX_VALUE, maxDB = Double.MIN_VALUE;
        for (int i = 0; i < width; i++) {
            // 幅度转分贝（20*log10），并确保最小值
            double db = 20 * Math.log10(bands[i] + 1e-10);
            bands[i] = db;
            if (minDB > db) minDB = db;
            if (maxDB < db) maxDB = db;
        }

        if (minDB < maxDB - dynamicRange) {
            minDB = maxDB - dynamicRange;
        }

        var LADDER = " ▁▂▃▄▅▆▇█";
        int[] heightValues = new int[width];
        for (int i = 0; i < width; i++) {
            double clampedDB = MathUtils.clamp(bands[i], minDB, maxDB);
            // 归一化到0-1范围
            double normalizedDB = (clampedDB - minDB) / (maxDB - minDB);
            // 换算成ASCII高度
            heightValues[i] = (int) Math.round(normalizedDB * height * 8);
        }

        // 构建ASCII频谱图
        StringBuilder spectrum = new StringBuilder();
        for (int y = height - 1; y >= 0; y--) {
            int threshold = y * 8;

            for (int i = 0; i < width; i++) {
                int index = heightValues[i] - threshold;
                spectrum.append(LADDER.charAt(MathUtils.clamp(index, 0, 8)));
            }

            spectrum.append('\n');
        }

        spectrum.setLength(spectrum.length()-1);
        System.out.println(spectrum);
    }
}

