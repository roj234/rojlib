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
package roj.net.tcp.serv.util;

import roj.collect.MyHashMap;
import roj.concurrent.OperationDone;
import roj.config.ParseException;
import roj.math.Version;
import roj.net.tcp.serv.Router;
import roj.net.tcp.util.*;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Request {
    private final int action;
    private final String path, version;
    private final Headers headers;
    private final InetSocketAddress remote;

    private Object postFields;
    private Map<String, String> getFields;

    private Request(int action, String version, String path, Headers headers, String postFields, InetSocketAddress remote) {
        this.action = action;
        this.version = version;
        this.path = path;
        this.headers = headers;
        this.postFields = postFields;
        this.remote = remote;
    }

    public InetSocketAddress remote() {
        return remote;
    }

    public int action() {
        return action;
    }

    public Version version() {
        return new Version(version);
    }

    public String host() {
        return headers.get("host");
    }

    public String path() {
        int qo = path.indexOf('?');
        if (qo != -1) {
            return path.substring(0, qo);
        } else {
            return path;
        }
    }

    public Headers headers() {
        return headers;
    }

    public Map<String, String> postFields() {
        if (postFields instanceof String) {
            String pf = (String) postFields;
            postFields = getQueries(pf);
        }
        return Helpers.cast(postFields);
    }

    public Map<String, String> getFields() {
        if (getFields == null) {
            int qo = path.indexOf('?');
            if (qo != -1) {
                getFields = getQueries(path.substring(qo + 1));
            } else {
                getFields = Collections.emptyMap();
            }
        }
        return getFields;
    }

    private static Map<String, String> getQueries(String query) {
        Map<String, String> map = new MyHashMap<>();
        List<String> queries = TextUtil.split(new ArrayList<>(), query, '&');
        for (int i = 0; i < queries.size(); i++) {
            String member = queries.get(i);
            int po = member.indexOf('=');
            if (po == -1) {
                map.put(member, "");
            } else {
                map.put(member.substring(0, po), member.substring(po + 1));
            }
        }
        return map.isEmpty() ? Collections.emptyMap() : map;
    }

    public String toString() {
        return remote + ": " + action + ' ' + host() + path;
    }

    public static Request parse(WrappedSocket socket, Router router) throws IllegalRequestException {
        Object[] data = Shared.SYNC_BUFFER.get();

        StreamLikeSequence plain = (StreamLikeSequence) data[0];
        HTTPHeaderLexer lexer = ((HTTPHeaderLexer) data[1]).init(plain.init(socket, router));

        try {
            return getRequest(lexer, router, socket);
        } catch (Notify notifyException) {
            Code code = Code.INTERNAL_ERROR;
            switch (notifyException.code) {
                case -127:
                    code = Code.ENTITY_TOO_LARGE;
                    break;
                case -128:
                    code = Code.TIMEOUT;
                    break;
            }
            throw new IllegalRequestException(code, notifyException);
        } finally {
            lexer.init(null);
            plain.release();
        }
    }

    public static Request getRequest(HTTPHeaderLexer lexer, Router router, WrappedSocket socket) throws IllegalRequestException {
        String method = lexer.readHttpWord(),
                path = lexer.readHttpWord(),
                version = lexer.readHttpWord();

        if (version == null || !version.startsWith("HTTP/"))
            throw new IllegalRequestException(Code.BAD_REQUEST, "Illegal header " + version);

        if (path.length() > 1024) {
            throw new IllegalRequestException(Code.URI_TOO_LONG);
        }

        int act = Action.valueOf(method);
        if (!router.checkAction(act))
            throw new IllegalRequestException(Code.METHOD_NOT_ALLOWED, "Illegal action " + method);

        String postFields = null;

        Headers headers = new Headers();
        while (true) {
            String t = lexer.readHttpWord();
            if (t == Shared._ERROR) {
                throw new IllegalRequestException(Code.BAD_REQUEST, lexer.err("Unexpected " + t));
            } else if (t == Shared._SHOULD_EOF) {
                break;
            } else if (t == null) {
                break;
            } else {
                headers.add(t, lexer.readHttpWord());
            }
        }

        if (act == Action.POST) {
            try {
                postFields = lexer.content(headers.get("Content-Length"), router.postMaxLength());
            } catch (ParseException e) {
                throw new IllegalRequestException(Code.BAD_REQUEST, e);
            }
        }

        version = version.substring(version.indexOf('/') + 1);

        final ByteList buffer = socket.buffer();
        if(buffer != null)
            buffer.clear();

        return new Request(act, version, path, headers, postFields, (InetSocketAddress) socket.socket().getRemoteSocketAddress());
    }

    public static Request parseAsync(WrappedSocket socket, Router router, Object[] holder) throws IllegalRequestException {
        Object obj = holder[0];

        HTTPHeaderLexer lexer;
        if(obj == null) {
            StreamLikeSequence seq = new StreamLikeSequence(true);
            lexer = new HTTPHeaderLexer().init(seq.init(socket, router));
            holder[0] = lexer;
        } else {
            lexer = (HTTPHeaderLexer) holder[0];
        }

        try {
            Request request = getRequest(lexer, router, socket);
            holder[0] = null;
            return request;
        } catch (OperationDone asyncReadRequest) {
            lexer.index = 0;
            return null;
        } catch (Notify notifyException) {
            Code code = Code.INTERNAL_ERROR;
            switch (notifyException.code) {
                case -127:
                    code = Code.ENTITY_TOO_LARGE;
                    break;
                case -128:
                    code = Code.TIMEOUT;
                    break;
            }
            throw new IllegalRequestException(code, notifyException);
        }
    }

    public String headers(String s) {
        return headers.get(s.toLowerCase());
    }
}
