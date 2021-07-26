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
package roj.net.tcp.util;

import roj.collect.TimedHashMap;
import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.Response;
import roj.net.tcp.serv.response.StringResponse;
import roj.text.CharList;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/11/28 21:13
 */
public final class SharedConfig {
    public static final String _SHOULD_EOF = new String();
    public static final String _ERROR = new String();

    public static final int
            STREAM_SEQ_INITIAL_CAPACITY = 100,
            THROTTLING_INTERVAL = 1000,
            MAX_CHAR_BUFFER_CAPACITY = 262144,
            READ_MAX = 4096,
            WRITE_MAX = 131072,
            DIRECT_CACHE_MAX = 1048576;

    public static final boolean THROTTLING_CHECK_ENABLED = false;

    public static final Response CONNECTION_THROTTLING = new Reply(ResponseCode.UNAVAILABLE, new StringResponse("压测防御" + THROTTLING_INTERVAL + "ms"));
    public static final TimedHashMap<String, AtomicInteger> CONNECTING_ADDRESSES = new TimedHashMap<>(THROTTLING_INTERVAL);

    public static final ThreadLocal<Object[]> SYNC_BUFFER = ThreadLocal.withInitial(() -> new Object[]{
            new StreamLikeSequence(STREAM_SEQ_INITIAL_CAPACITY, false),
            new HTTPHeaderLexer(),
            new CharList(),
            null
    });

    public static final byte[] END_OF_CHUNK = new byte[] {
            '0', '\r', '\n', '\r', '\n'
    };
}
