/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
/*
 * Player.java -- 播放一个文件
 * Copyright (C) 2011
 */
package roj.sound.mp3;

import roj.io.source.Source;
import roj.sound.util.AudioBuffer;
import roj.sound.util.IAudio;

import java.io.Closeable;
import java.io.IOException;

public class Player implements Closeable, AutoCloseable {
    private final byte[] buf;
    private final int BUFFER_LEN = 4096;
    private boolean eof, paused;

    private Source in;

    private final ID3Tag tag;
    private final boolean checkID3V1;
    private int off, maxOff;
    private long cutPos;
    private final Header header;

    public final IAudio audio;

    /**
     * 用指定的音频输出对象构造一个PlayBack对象。
     *
     * @param audio 指定的音频输出 若指定为 <b>null</b> 则只解码不播放输出。
     */
    public Player(IAudio audio) {
        this(audio, false);
    }

    public Player(IAudio audio, boolean checkID3V1) {
        this.audio = audio;
        this.checkID3V1 = checkID3V1;
        header = new Header();
        tag = new ID3Tag();
        buf = new byte[BUFFER_LEN];
        cutPos = -1;
    }

    /**
     * 暂停或继续此文件播放。这相当于一个单稳态的触发开关，第一次调用该方法暂停播放，第二次调用继续播放，以此类推。
     *
     * @return 返回当前状态。处于暂停状态返回true，否则返回false。
     */
    public boolean pause() {
        audio.start(paused);

        if (paused) {
            synchronized (this) {
                notifyAll();
            }
        }
        paused = !paused;

        return paused;
    }

    /**
     * 终止此文件播放。
     */
    public void stop() {
        eof = true;
        synchronized (this) {
            notify();
        }
    }

    /**
     * 关闭此文件播放并清除关联的资源。
     */
    public void close() throws IOException {
        tag.clear();
        header.reset();
        if (audio != null)
            audio.close();

        if (in != null)
            in.close();
        in = null;
    }

    public boolean closed() {
        return in == null;
    }

    public boolean paused() {
        return paused;
    }

    /**
     * 打开文件并解析文件信息。
     */
    public boolean open(Source in) throws IOException {
        maxOff = off = 0;
        paused = eof = false;
        cutPos = -1;
        tag.clear();

        this.in = in;
        if (parseTag() == -1)
            return false;

        header.reset();

        // 定位到第一帧并完成帧头解码
        nextHeader();
        if (eof)
            return false;

        // 成功解析帧头后初始化音频输出
        return audio == null || audio.open(header);
    }

    private int parseTag() throws IOException {
        int tagSize = 0;

        if(checkID3V1) {
            // ID3 v1
            int length = (int) in.length();
            in.seek(length - 128 - 32);
            if (in.read(buf, 0, 128 + 32) == 128 + 32) {
                if (tag.parseID3V1(buf, 32)) {
                    tagSize = 128;
                }
                tagSize += tag.checkAPEtagFooter(buf, 0); // APE tag footer
            } else return -1; // no much data
        }
        in.seek(0);

        if ((maxOff = in.read(buf, 0, BUFFER_LEN)) <= 10) {
            eof = true;
            return -1;
        }

        // ID3 v2
        int count = tag.checkID3V2(buf, 0);
        tagSize += count;
        if (count > maxOff) {
            byte[] b = new byte[count];
            System.arraycopy(buf, 0, b, 0, maxOff);
            count -= maxOff;
            if ((maxOff = in.read(b, maxOff, count)) < count) {
                eof = true;
                return -1;
            }
            tag.parseID3V2(b, 0, b.length);
            if ((maxOff = in.read(buf, 0, BUFFER_LEN)) <= 4)
                eof = true;
        } else if (count > 10) {
            tag.parseID3V2(buf, 0, count);
            off = count;
        }

        return tagSize;
    }

    /**
     * 获取帧头信息。
     *
     * @return 取帧 {@link Header} 对象。
     * @see Header
     */
    public Header getHeader() {
        return header;
    }

    /**
     * 获取文件的标签信息。
     *
     * @return 文件的标签信息
     */
    public ID3Tag getID3Tag() {
        return tag;
    }

    /**
     * 解码已打开的文件。
     *
     * @return 成功播放指定的文件返回true，否则返回false。
     */
    public boolean decode() {
        if(paused) {
            audio.start(true);
            paused = false;
        }

        AudioBuffer ac = new AudioBuffer(audio, header.getPcmSize());

        Layer layer;
        switch (header.getLayer()) {
            case 1:
                layer = new Layer1(header, ac);
                break;
            case 2:
                layer = new Layer2(header, ac);
                break;
            case 3:
                layer = new Layer3(header, ac);
                break;
            default:
                return false;
        }

        try {
            while (!eof) {
                // 1. 解码一帧并输出
                this.off = layer.decodeFrame(buf, off);

                // 2. 定位到下一帧并解码帧头
                nextHeader();

                // 3. 检测并处理暂停
                if (paused) {
                    synchronized (this) {
                        while (paused && !eof)
                            wait();
                    }
                }

                if(cutPos != -1) {
                    in.seek(cutPos);
                    audio.drain();
                    cutPos = -1;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException ignored) {
        } finally {
            layer.close();
        }

        return true;
    }

    private void nextHeader() throws IOException {
        int len, chunk = 0;

        int maxOff = this.maxOff, off = this.off;
        byte[] buf = this.buf;
        Header header = this.header;

        while (!header.isSyncFrame(buf, off, maxOff)) {
            // buf内帧同步失败或数据不足一帧，刷新缓冲区buf
            off = header.offset();
            len = maxOff - off;

            if(len > 0)
                System.arraycopy(buf, off, buf, 0, len); // 把off的buf移到起始位置

            maxOff = len + in.read(buf, len, off);
            off = 0;

            if (maxOff <= len || (chunk += BUFFER_LEN) > 0x10000) {
                eof = true;
                break;
            }
        }
        this.off = header.offset();
        this.maxOff = maxOff;
    }

    /**
     * 跳转到指定位置, 单位秒
     */
    public void cut(double timestamp) throws IOException {
        long targetFrame = Math.round(timestamp / header.getFrameDuration());
        cutPos = header.getFrameSize() * targetFrame;
        if(cutPos < 0)
            cutPos = 0;
        else {
            long l = in.length();
            if (cutPos > l)
                cutPos = l;
        }
    }
}