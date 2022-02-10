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
package roj.net.http.serv;

import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.concurrent.OperationDone;
import roj.config.ParseException;
import roj.math.MathUtils;
import roj.math.MutableInt;
import roj.math.Version;
import roj.net.SocketSequence;
import roj.net.WrappedSocket;
import roj.net.http.*;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Request {
    private final int action;
    private final String path, version;
    private final Headers headers;

    private Object postFields, getFields;
    private Map<String, String> cookie;

    private Request(int action, String version, String path) {
        this.action = action;
        this.version = version;

        int qo = path.indexOf('?');
        if (qo >= 0) {
            this.path = path.substring(0, qo);
            this.getFields = path.substring(qo+1);
        } else {
            this.path = path;
            this.getFields = Collections.emptyMap();
        }
        this.headers = new Headers();
    }

    public int action() {
        return action;
    }

    public Version version() {
        return new Version(version);
    }

    public String host() {
        return headers.get("Host");
    }

    public String path() {
        return path;
    }

    public Headers headers() {
        return headers;
    }

    @Nullable
    public Map<String, String> postFields() {
        if (postFields instanceof CharSequence) {
            CharSequence pf = (CharSequence) postFields;
            postFields = getQueries(pf, "&");
        }
        return Helpers.cast(postFields);
    }

    @Nullable
    public String postFieldsRaw() {
        if (postFields == null) {
            return null;
        } else if (postFields instanceof String) {
            return (String) (postFields = postFields.toString());
        }
        throw new IllegalStateException("Parsed");
    }

    public Map<String, String> getFields() {
        if (getFields instanceof CharSequence) {
            getFields = getQueries((CharSequence) getFields, "&");
        }
        return Helpers.cast(getFields);
    }

    public Map<String, String> cookie() {
        if (cookie == null) {
            String cookie = headers.get("Cookie");
            this.cookie = cookie == null ? Collections.emptyMap() : getQueries(cookie, "; ");
        }
        return cookie;
    }

    private static Map<String, String> getQueries(CharSequence query, String delimiter) {
        Map<String, String> map = new LinkedMyHashMap<>();
        List<String> queries = TextUtil.split(new ArrayList<>(), query, delimiter);
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

    public String header(String s) {
        return headers.get(s);
    }

    public Map<String, String> fields() {
        MyHashMap<String, String> map = new MyHashMap<>(getFields());
        Map<String, String> map1 = postFields();
        if (map1 != null) map.putAll(map1);
        return map;
    }

    public String toString() {
        return action + ' ' + host() + path;
    }

    byte state;

    public static Request parse(WrappedSocket ch, Router router, Object[] holder) throws IllegalRequestException, IOException {
        HttpLexer lexer = (HttpLexer) holder[0];

        if(lexer == null) {
            SocketSequence seq = new SocketSequence(true);
            lexer = new HttpLexer().init(seq.init(ch, router.readTimeout(), router.maxLength()));
            holder[0] = lexer;
        }

        try {
            Request req;
            if (holder[1] == null) {
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

                holder[1] = req = new Request(act, version.substring(version.indexOf('/') + 1), path);

                compact(lexer, ch.buffer());
            } else {
                req = (Request) holder[1];
            }

            Headers headers = req.headers;
            if (req.state < 1) {
                headers.clear();
                try {
                    headers.readFromLexer(lexer);
                } catch (ParseException e) {
                    throw new IllegalRequestException(Code.BAD_REQUEST, e);
                }

                compact(lexer, ch.buffer());
                req.state = 1;
            }

            if (req.action == Action.POST) {
                if (req.state < 2) {
                    UTFCoder uc = new UTFCoder();
                    holder[2] = uc;
                    uc.charBuf.list = lexer.getBuf();
                    uc.keep = true;

                    String cl = headers.get("Content-Length");
                    if (cl != null) {
                        int len = MathUtils.parseInt(cl);
                        if (len > router.postMaxLength()) throw new IllegalRequestException(Code.ENTITY_TOO_LARGE);
                        req.postFields = new MutableInt(len);
                        uc.charBuf.ensureCapacity(len);
                    }
                    req.state = 2;
                }

                UTFCoder uc = (UTFCoder) holder[2];
                if (req.postFields == null) {
                    // no content length
                    if (!"close".equalsIgnoreCase(headers.get("Connection")))
                        throw new IllegalRequestException(411);
                    int r;
                    while ((r = ch.read()) > 0) {
                        ByteBuffer rb = ch.buffer();
                        rb.flip();
                        uc.decode(rb, true);
                        rb.clear();
                    }
                    if (r == 0) return null;
                } else {
                    MutableInt remain = (MutableInt) req.postFields;
                    if (ch.read(remain.getValue()) < 0) throw new IOException("Unexpected EOF");

                    ByteBuffer rb = ch.buffer();
                    int r = rb.position();
                    rb.flip();
                    uc.decode(rb, true);
                    rb.clear();

                    if (remain.addAndGet(-r) > 0) return null;
                }
                req.postFields = uc.charBuf;
            }

            compact(lexer, ch.buffer());
            holder[0] = holder[1] = holder[2] = null;
            return req;
        } catch (OperationDone noDataAvailable) {
            lexer.index = 0;
            return null;
        }
    }

    private static void compact(HttpLexer lexer, ByteBuffer buf) {
        if(buf != null)  {
            buf.flip().position(lexer.index);
            lexer.index = 0;
            buf.compact();
        }
    }
}
