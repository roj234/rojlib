/*
 * This file is a part of MoreItems
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
package roj.opengl.vertex;

import roj.io.IOUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static roj.opengl.vertex.VertexFormat.*;

/**
 * Vertex Builder
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/18 13:15
 */
public class VertexBuilder {
    private ByteBuffer buf;
    private int vertexCount;

    private VertexFormat       format;
    private VertexFormat.Entry entry;
    private int                formatIndex;
    private int                offset;

    public boolean noColor;
    public double xOffset, yOffset, zOffset;
    public double xScale, yScale, zScale;

    static final int MAX_BUFFER_CAPACITY = 1024 * 1024 * 8;

    public VertexBuilder(int initialCapacity) {
        buf = ByteBuffer.allocateDirect(initialCapacity).order(ByteOrder.nativeOrder());
    }

    public void grow(int plus) {
        if(buf.capacity() - getBufferSize() - offset - plus < 0 && buf.capacity() < MAX_BUFFER_CAPACITY) {
            int newCap = buf.capacity() + roundUp(plus, 262144);
            int pos = buf.position();
            ByteBuffer newBuf = ByteBuffer.allocateDirect(newCap).order(ByteOrder.nativeOrder());
            buf.position(0);
            newBuf.put(buf).position(pos);
            IOUtil.clean(buf);
            buf = newBuf;
        }
    }

    private static int roundUp(int num, int align) {
        return ((num % align > 0 ? 1 : 0) + num / align) * align;
    }

    public int getBufferSize() {
        return this.vertexCount * this.format.getSize();
    }

    public void reset() {
        this.vertexCount = 0;
        this.entry = format.getEntry(this.formatIndex = 0);
        this.offset = 0;
        this.noColor = false;
    }

    public void begin(VertexFormat format) {
        this.format = format;
        reset();
        buf.position(0).limit(buf.capacity());
    }

    public void end() {
        buf.position(0);
        buf.limit(this.getBufferSize());
        this.noColor = false;
        this.xOffset = yOffset = zOffset = 0;
    }

    public void putVertexes(byte[] data) {
        if(data.length % this.format.getSize() != 0) {
            throw new IllegalArgumentException("Non padded");
        }
        this.grow(data.length + this.format.getSize());
        this.buf.position(this.getBufferSize());
        this.buf.put(data);
        this.vertexCount += data.length / this.format.getSize();
    }

    public void putVertexes(ByteBuffer buffer) {
        if(buffer.limit() % this.format.getSize() != 0) {
            throw new IllegalArgumentException("Non padded");
        }
        this.grow(buffer.limit() + this.format.getSize());
        buf.position(this.getBufferSize());
        buf.put(buffer);
        this.vertexCount += buffer.limit() / this.format.getSize();
    }

    public VertexBuilder pos(double x, double y, double z) {
        int i = this.vertexCount * this.format.getSize() + offset;
        switch (this.entry.type()) {
            case FLOAT:
                buf.putFloat(i, (float) (x + this.xOffset));
                buf.putFloat(i + 4, (float) (y + this.yOffset));
                buf.putFloat(i + 8, (float) (z + this.zOffset));
                break;
            case UINT:
            case INT:
                buf.putInt(i, (int) (x + this.xOffset));
                buf.putInt(i + 4, (int) (y + this.yOffset));
                buf.putInt(i + 8, (int) (z + this.zOffset));
                break;
            case USHORT:
            case SHORT:
                buf.putShort(i, (short) (x + this.xOffset));
                buf.putShort(i + 2, (short) (y + this.yOffset));
                buf.putShort(i + 4, (short) (z + this.zOffset));
                break;
            case UBYTE:
            case BYTE:
                buf.put(i, (byte) (x + this.xOffset));
                buf.put(i + 1, (byte) (y + this.yOffset));
                buf.put(i + 2, (byte) (z + this.zOffset));
                break;
        }

        this.next();
        return this;
    }

    public VertexBuilder tex(double u, double v) {
        int i = this.vertexCount * this.format.getSize() + offset;
        switch (this.entry.type()) {
            case FLOAT:
                buf.putFloat(i, (float) u);
                buf.putFloat(i + 4, (float) v);
                break;
            case UINT:
            case INT:
                buf.putInt(i, (int) u);
                buf.putInt(i + 4, (int) v);
                break;
            case USHORT:
            case SHORT:
                buf.putShort(i, (short) v);
                buf.putShort(i + 2, (short) u);
                break;
            case UBYTE:
            case BYTE:
                buf.put(i, (byte) v);
                buf.put(i + 1, (byte) u);
                break;
        }

        this.next();
        return this;
    }

    public VertexBuilder color(float red, float green, float blue, float alpha) {
        return this.color((int) (red * 255.0F), (int) (green * 255.0F), (int) (blue * 255.0F), (int) (alpha * 255.0F));
    }

    public VertexBuilder color(int red, int green, int blue, int alpha) {
        if (!this.noColor) {
            int i = this.vertexCount * this.format.getSize() + offset;
            switch (this.entry.type()) {
                case FLOAT:
                    buf.putFloat(i, (float) red / 255.0F);
                    buf.putFloat(i + 4, (float) green / 255.0F);
                    buf.putFloat(i + 8, (float) blue / 255.0F);
                    buf.putFloat(i + 12, (float) alpha / 255.0F);
                    break;
                case UINT:
                case INT:
                    buf.putInt(i, red);
                    buf.putInt(i + 4, green);
                    buf.putInt(i + 8, blue);
                    buf.putInt(i + 12, alpha);
                    break;
                case USHORT:
                case SHORT:
                    buf.putShort(i, (short) red);
                    buf.putShort(i + 2, (short) green);
                    buf.putShort(i + 4, (short) blue);
                    buf.putShort(i + 6, (short) alpha);
                    break;
                case UBYTE:
                case BYTE:
                    if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                        buf.put(i, (byte) red);
                        buf.put(i + 1, (byte) green);
                        buf.put(i + 2, (byte) blue);
                        buf.put(i + 3, (byte) alpha);
                    } else {
                        buf.put(i, (byte) alpha);
                        buf.put(i + 1, (byte) blue);
                        buf.put(i + 2, (byte) green);
                        buf.put(i + 3, (byte) red);
                    }
                    break;
            }
        }
        this.next();
        return this;
    }

    public VertexBuilder normal(float x, float y, float z) {
        int i = this.vertexCount * this.format.getSize() + offset;
        switch (this.entry.type()) {
            case FLOAT:
                buf.putFloat(i, x);
                buf.putFloat(i + 4, y);
                buf.putFloat(i + 8, z);
                break;
            case UINT:
            case INT:
                buf.putInt(i, (int) x);
                buf.putInt(i + 4, (int) y);
                buf.putInt(i + 8, (int) z);
                break;
            case USHORT:
            case SHORT:
                buf.putShort(i, (short) ((int) (x * 32767.0F) & 0xFFFF));
                buf.putShort(i + 2, (short) ((int) (y * 32767.0F) & 0xFFFF));
                buf.putShort(i + 4, (short) ((int) (z * 32767.0F) & 0xFFFF));
                break;
            case UBYTE:
            case BYTE:
                buf.put(i, (byte) ((int) (x * 127.0F) & 0xFF));
                buf.put(i + 1, (byte) ((int) (y * 127.0F) & 0xFF));
                buf.put(i + 2, (byte) ((int) (z * 127.0F) & 0xFF));
                break;
        }

        this.next();
        return this;
    }

    public void endVertex() {
        this.offset = 0;
        this.vertexCount++;
        this.entry = this.format.getEntry(this.formatIndex = 0);
        this.grow(this.format.getSize());
    }

    public void next() {
        do {
            if(++this.formatIndex == this.format.entryCount()) {
                this.offset = 0;
                this.formatIndex = 0;
            } else {
                this.offset += this.entry.totalSize();
            }
            this.entry = this.format.getEntry(this.formatIndex);
        } while (this.entry.usage() == PADDING);
    }

    public VertexBuilder translate(double x, double y, double z) {
        this.xOffset = x;
        this.yOffset = y;
        this.zOffset = z;
        return this;
    }

    public ByteBuffer getBuffer() {
        return buf;
    }

    public VertexFormat getVertexFormat() {
        return this.format;
    }

    public int getVertexCount() {
        return this.vertexCount;
    }

    public void free() {
        if(buf != null) {
            IOUtil.clean(buf);
            buf = null;
        }
    }
}

