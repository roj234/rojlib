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
package roj.repackage.com_nothome_delta;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/8/12 22:52
 */
public class GDiffPatcher {
    private ByteBuffer buf = ByteBuffer.allocate(1024);
    private byte[] buf2;

    public GDiffPatcher() {
        this.buf2 = this.buf.array();
    }

    public void patch(byte[] source, InputStream patch, OutputStream output) throws IOException {
        this.patch(new ByteBufferSeekableSource(source), patch, output);
    }

    public byte[] patch(byte[] source, byte[] patch) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        this.patch(source, new ByteArrayInputStream(patch), os);
        return os.toByteArray();
    }

    public void patch(SeekableSource source, InputStream patch, OutputStream out) throws IOException {
        DataOutputStream outOS = new DataOutputStream(out);
        DataInputStream patchIS = new DataInputStream(patch);
        if (patchIS.readUnsignedByte() == 209 && patchIS.readUnsignedByte() == 255 && patchIS.readUnsignedByte() == 209 && patchIS.readUnsignedByte() == 255 && patchIS.readUnsignedByte() == 4) {
            while(true) {
                int command = patchIS.readUnsignedByte();
                if (command == 0) {
                    outOS.flush();
                    return;
                }

                if (command <= 246) {
                    this.append(command, patchIS, outOS);
                } else {
                    int length;
                    int offset;
                    switch(command) {
                        case 247:
                            length = patchIS.readUnsignedShort();
                            this.append(length, patchIS, outOS);
                            break;
                        case 248:
                            length = patchIS.readInt();
                            this.append(length, patchIS, outOS);
                            break;
                        case 249:
                            offset = patchIS.readUnsignedShort();
                            length = patchIS.readUnsignedByte();
                            this.copy(offset, length, source, outOS);
                            break;
                        case 250:
                            offset = patchIS.readUnsignedShort();
                            length = patchIS.readUnsignedShort();
                            this.copy(offset, length, source, outOS);
                            break;
                        case 251:
                            offset = patchIS.readUnsignedShort();
                            length = patchIS.readInt();
                            this.copy(offset, length, source, outOS);
                            break;
                        case 252:
                            offset = patchIS.readInt();
                            length = patchIS.readUnsignedByte();
                            this.copy(offset, length, source, outOS);
                            break;
                        case 253:
                            offset = patchIS.readInt();
                            length = patchIS.readUnsignedShort();
                            this.copy(offset, length, source, outOS);
                            break;
                        case 254:
                            offset = patchIS.readInt();
                            length = patchIS.readInt();
                            this.copy(offset, length, source, outOS);
                            break;
                        case 255:
                            long loffset = patchIS.readLong();
                            length = patchIS.readInt();
                            this.copy(loffset, length, source, outOS);
                            break;
                        default:
                            throw new IllegalStateException("command " + command);
                    }
                }
            }
        } else {
            throw new IOException("magic string not found, aborting!");
        }
    }

    private void copy(long offset, int length, SeekableSource source, OutputStream output) throws IOException {
        source.seek(offset);

        while(length > 0) {
            int len = Math.min(this.buf.capacity(), length);
            this.buf.clear().limit(len);
            int res = source.read(this.buf);
            if (res == -1) {
                throw new EOFException("in copy " + offset + " " + length);
            }

            output.write(this.buf.array(), 0, res);
            length -= res;
        }
    }

    private void append(int length, InputStream patch, OutputStream output) throws IOException {
        while(length > 0) {
            int len = Math.min(this.buf2.length, length);
            int res = patch.read(this.buf2, 0, len);
            if (res == -1) {
                throw new EOFException("cannot read " + length);
            }

            output.write(this.buf2, 0, res);
            length -= res;
        }

    }
}