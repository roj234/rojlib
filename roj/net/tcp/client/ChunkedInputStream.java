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
package roj.net.tcp.client;

import roj.math.MathUtils;
import roj.net.tcp.util.HTTPHeaderLexer;
import roj.net.tcp.util.Headers;
import roj.net.tcp.util.Shared;
import roj.net.tcp.util.StreamLikeSequence;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * 傻逼GIT搞丢了我可怜的这个class，这是反编译出来的
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/10/23 21:30
 */
public class ChunkedInputStream extends InputStream {
    static final int CHUNK_LENGTH = 0, CHUNK_DATA = 1, CHUNK_END = 2, EOF = 3;

    private final SocketInputStream in;
    private final Headers tailHeader;

    private int stage;

    private int chunkPos, chunkSize;

    public ChunkedInputStream(SocketInputStream in, Headers tailHeaderAppender) {
        this.in = in;
        //stage = CHUNK_LENGTH;
        this.tailHeader = tailHeaderAppender;
    }

    @Override
    public int available() throws IOException {
        return Math.min(in.available(), chunkSize - chunkPos);
    }

    @Override
    public int read() throws IOException {
        if (stage == EOF) return -1;
        if (stage != CHUNK_DATA) {
            checkChunk();
            if (stage == EOF) return -1;
        }

        int b = in.read();
        if (b != -1) {
            assert chunkPos <= chunkSize;
            if (++chunkPos == chunkSize)
                stage = CHUNK_END;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (stage == EOF) return -1;
        if (stage != CHUNK_DATA) {
            checkChunk();
            if (stage == EOF) return -1;
        }

        int read = in.read(b, off, Math.min(len, available()));
        if (read < 0) {
            stage = EOF;
            throw new EOFException("未预料的EOF, Chunk: " + chunkSize + " Got: " + chunkPos);
        }

        chunkPos += read;
        assert chunkPos <= chunkSize;
        if (chunkPos == chunkSize)
            stage = CHUNK_END;
        return read;
    }

    @SuppressWarnings("fallthrough")
    private void checkChunk() throws IOException {
        Object[] data = Shared.SYNC_BUFFER.get();

        StreamLikeSequence plain = (StreamLikeSequence)data[0];
        HTTPHeaderLexer lexer = ((HTTPHeaderLexer)data[1]).init(plain.init(in.socket, in.readTimeout, (int) in.dataRemain));

        lexer.index = in.bufPos;
        try {
            switch (stage) {
                case CHUNK_END:
                    String eof = lexer.readLine();
                    if (!eof.isEmpty()) {
                        throw new EOFException("Content overflow? got " + eof);
                    }
                case CHUNK_LENGTH: {
                    String size = lexer.readLine();
                    try {
                        chunkSize = MathUtils.parseInt(size, 16);
                        if (chunkSize < 0) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException e) {
                        throw new IOException("Bad chunk length");
                    }

                    if (chunkSize > 0) {
                        stage = CHUNK_DATA;
                        chunkPos = 0;
                        break;
                    }

                    // read tail header
                    stage = EOF;
                    while (true) {
                        String t = lexer.readHttpWord();
                        if (t == Shared._ERROR) {
                            throw new IOException("Unexpected trail header " + t);
                        } else if (t == Shared._SHOULD_EOF) {
                            break;
                        } else if (t == null) {
                            break;
                        } else {
                            tailHeader.add(t, lexer.readHttpWord());
                        }
                    }
                    break;
                }
            }
        } finally {
            plain.release();
            in.pass(lexer.index);
        }
    }

    @Override
    public void close() throws IOException {
        stage = EOF;
        in.close();
    }

    @Override
    public boolean markSupported() {
        return stage == CHUNK_DATA;
    }

    @Override
    public synchronized void mark(int limit) {
        if (stage != CHUNK_DATA) return;
        super.mark(Math.min(limit, chunkSize - chunkPos));
    }

    @Override
    public synchronized void reset() throws IOException {
        in.reset();
    }
}