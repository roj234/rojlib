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
package roj.net.http;

import roj.config.ParseException;
import roj.config.word.AbstLexer;
import roj.math.MathUtils;
import roj.net.SocketSequence;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since  2021/10/23 21:30
 */
class ChunkedInputStream extends InputStream {
    static final byte[] buffer = new byte[1024];
    static final int CHUNK_LENGTH = 0, CHUNK_DATA = 1, CHUNK_END = 2, EOF = 3;

    private final SocketInputStream in;
    private final Headers tailHeader;

    private int stage;

    private int chunkPos, chunkSize;

    public ChunkedInputStream(SocketInputStream in, Headers tailHeaderAppender) {
        this.in = in;
        this.tailHeader = tailHeaderAppender;
    }

    @Override
    public int available() throws IOException {
        return chunkSize - chunkPos;
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
        Object[] data = HttpClient.LOCAL_LEXER.get();

        SocketSequence plain = (SocketSequence)data[0];
        HttpLexer lexer = ((HttpLexer)data[1]).init(plain.init(in.socket, in.readTimeout, (int) in.dataRemain));

        lexer.index = in.pos;
        try {
            switch (stage) {
                case CHUNK_END:
                    endOfChunk(lexer);
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

                    endOfChunk(lexer);

                    // read tail header
                    stage = EOF;
                    if (tailHeader != null) {
                        try {
                            tailHeader.readFromLexer(lexer);
                        } catch (ParseException e) {
                            throw new IOException(e);
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

    private static void endOfChunk(HttpLexer lexer) throws EOFException {
        String eof;
        eof = lexer.readLine();
        if (!eof.isEmpty()) {
            throw new EOFException("Content overflow? got '" + AbstLexer.addSlashes(eof) + "'");
        }
    }

    @Override
    public void close() throws IOException {
        // 不用考虑多线程问题
        try {
            while (stage != EOF) {
                if (read(buffer) < 0) break;
            }
        } finally {
            stage = EOF;
            in.close();
        }
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