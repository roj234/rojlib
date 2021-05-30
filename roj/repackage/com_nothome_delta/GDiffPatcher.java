package roj.repackage.com_nothome_delta;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: GDiffPatcher.java
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