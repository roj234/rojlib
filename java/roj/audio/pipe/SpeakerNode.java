package roj.audio.pipe;

import roj.audio.AudioSink;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2023/2/3 15:07
 */
public class SpeakerNode extends AudioNode {
    public enum ChannelStrategy {
        FILL_ZERO,      // 多出声道填零
        REPEAT_FIRST,   // 重复第一个声道
        MIX_TO_MONO     // 混合到单声道
    }

    private AudioSink sink;

    private final AudioFormat targetFormat;
    private final int targetChannels;
    private final ChannelStrategy strategy;
    private final int sampleBytes;
    private final boolean bigEndian;

    public SpeakerNode(AudioFormat format, ChannelStrategy strategy) {
        this.targetFormat = format;
        this.targetChannels = format.getChannels();
        this.strategy = strategy;
        this.sampleBytes = format.getSampleSizeInBits() / 8;
        this.bigEndian = format.isBigEndian();
    }

    @Override public int type() {return DRAIN;}

    public ByteBuffer convert(AudioBuffer buffer, int frames) {
        // 声道处理
        float[][] processedChannels = processChannels(buffer.data, buffer.channels, frames);

        // 创建目标ByteBuffer
        int byteSize = frames * targetChannels * sampleBytes;
        ByteBuffer output = ByteBuffer.allocate(byteSize).order(
            bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN
        );

        // 格式转换
        convertToPcm(processedChannels, output, frames);
        output.flip();
        return output;
    }

    private float[][] processChannels(float[][] src, int srcChannels, int frames) {
        if (srcChannels == targetChannels) return src;

        float[][] dest = new float[targetChannels][frames];
        int minChannels = Math.min(srcChannels, targetChannels);

        // 复制共有声道
        for (int ch = 0; ch < minChannels; ch++) {
            System.arraycopy(src[ch], 0, dest[ch], 0, frames);
        }

        // 处理多出声道
        if (targetChannels > srcChannels) {
            switch (strategy) {
                case FILL_ZERO:
                    for (int ch = minChannels; ch < targetChannels; ch++) {
                        Arrays.fill(dest[ch], 0, frames, 0.0f);
                    }
                    break;
                case REPEAT_FIRST:
                    float[] first = src[0];
                    for (int ch = minChannels; ch < targetChannels; ch++) {
                        System.arraycopy(first, 0, dest[ch], 0, frames);
                    }
                    break;
            }
        } else if (targetChannels == 1 && srcChannels > 1) {
            // 混合到单声道
            for (int i = 0; i < frames; i++) {
                float sum = 0;
                for (int ch = 0; ch < srcChannels; ch++) {
                    sum += src[ch][i];
                }
                dest[0][i] = sum / srcChannels;
            }
        }
        return dest;
    }

    private void convertToPcm(float[][] channels, ByteBuffer output, int frames) {
        String encoding = targetFormat.getEncoding().toString();
        float maxValue = getMaxValue();

        for (int i = 0; i < frames; i++) {
            for (int ch = 0; ch < targetChannels; ch++) {
                float sample = channels[ch][i];
                sample = Math.max(-1.0f, Math.min(1.0f, sample));

                switch (encoding) {
                    case "PCM_SIGNED":
                        writeSigned(sample, maxValue, output);
                        break;
                    case "PCM_UNSIGNED":
                        writeUnsigned(sample, maxValue, output);
                        break;
                    case "PCM_FLOAT":
                        writeFloat(sample, output);
                        break;
                    default:
                        throw new UnsupportedOperationException("Unsupported encoding: " + encoding);
                }
            }
        }
    }

    private float getMaxValue() {
        int bits = targetFormat.getSampleSizeInBits();
        return bits == 32 ? 1.0f : (float) (Math.pow(2, bits - 1) - 1);
    }

    private void writeSigned(float sample, float max, ByteBuffer out) {
        int value = (int) (sample * max);
        writeBytes(value, out);
    }

    private void writeUnsigned(float sample, float max, ByteBuffer out) {
        int mid = (int) (max + 1);
        int value = (int) ((sample + 1.0f) * mid);
        writeBytes(value, out);
    }

    private void writeFloat(float sample, ByteBuffer out) {
        out.putFloat(sample);
    }

    private void writeBytes(int value, ByteBuffer out) {
        switch (sampleBytes) {
            case 1:
                out.put((byte) (value & 0xFF));
                break;
            case 2:
                out.putShort((short) value);
                break;
            case 3:
                write24Bit(value, out);
                break;
            case 4:
                out.putInt(value);
                break;
            default:
                throw new IllegalArgumentException("Unsupported sample size: " + sampleBytes * 8);
        }
    }

    private void write24Bit(int value, ByteBuffer out) {
        if (bigEndian) {
            out.put((byte) ((value >> 16) & 0xFF));
            out.put((byte) ((value >> 8) & 0xFF));
            out.put((byte) (value & 0xFF));
        } else {
            out.put((byte) (value & 0xFF));
            out.put((byte) ((value >> 8) & 0xFF));
            out.put((byte) ((value >> 16) & 0xFF));
        }
    }
}