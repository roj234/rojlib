package roj.net.tcp.util;

import roj.collect.TimedHashMap;
import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.Response;
import roj.net.tcp.serv.response.StringResponse;
import roj.text.CharList;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/28 21:13
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
            null, // nonnull
            null
    });

    public static final byte[] END_OF_CHUNK = new byte[] {
            '0', '\r', '\n', '\r', '\n'
    };
}
