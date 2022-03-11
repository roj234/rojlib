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

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.io.IOUtil;
import roj.math.Version;
import roj.net.http.Action;
import roj.net.http.Headers;
import roj.net.http.HttpLexer;
import roj.security.SipHashMap;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Request {
    public static final String CTX_POST_HANDLER = "PH";

    private final int action;
    private final String path, version;
    final Headers headers;

    Object postFields, getFields;
    private Map<String, String> cookie;

    private Map<String, Object> ctx;
    RequestHandler.Local local;
    RequestHandler handler;

    public Map<String, Object> ctx() {
        if (ctx == null) ctx = new MyHashMap<>(4);
        return ctx;
    }

    public Map<String, Object> threadLocalCtx() {
        return local.ctx;
    }

    public RequestHandler.Local threadLocal() {
        return local;
    }

    public RequestHandler handler() {
        return handler;
    }

    Request(int action, String version, String path) {
        this.action = action;
        this.version = version;

        int qo = path.indexOf('?');
        if (qo >= 0) {
            this.path = path.substring(0, qo);
            this.getFields = path.substring(qo+1);
        } else {
            this.path = path;
            this.getFields = new MyHashMap<>();
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

    public Map<String, String> payloadFields() {
        if (postFields instanceof ByteList) {
            ByteList pf = (ByteList) postFields;

            String ct = headers.getOrDefault("Content-Type", "");
            if (ct.startsWith("multipart")) {
                return Helpers.cast(postFields = convertToFields(payloadMultipart()));
            } else {
                postFields = getQueries(IOUtil.SharedCoder.get().decodeR(pf), "&");
            }
        }
        return Helpers.cast(postFields);
    }

    public static Map<String, Object> convertToFields(List<FormData> data) {
        UTFCoder uc = IOUtil.SharedCoder.get();
        Map<String, Object> map = new SipHashMap<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            FormData d = data.get(i);
            String type = d.h.getOrDefault("Content-Type", "text/plain");
            String cp = d.h.get("Content-Disposition");
            if (cp == null) throw new IllegalArgumentException("No Content-Disposition header");
            MyHashMap<String, String> map1 = Headers.decodeValue(cp, false);
            String name = map1.get("name");
            if (name == null) throw new IllegalArgumentException("No name in Content-Disposition header");
            map.put(name, type.startsWith("text") ? uc.decode(d.data) : d.data);
        }
        return map;
    }

    public List<FormData> payloadMultipart() {
        if (postFields instanceof ByteList) {
            String ct = headers.get("Content-Type");
            if (ct == null || !ct.startsWith("multipart")) {
                throw new IllegalArgumentException("Not multipart environment");
            } else {
                // multipart
                MyHashMap<String, String> hdr = Headers.decodeValue(ct, false);
                String b = hdr.get("boundary");
                if (b == null)
                    throw new IllegalArgumentException("Not found boundary in Content-Type header: " + ct);
                byte[] boundary = IOUtil.SharedCoder.get().encode(b);
                postFields = decodeBoundary((ByteList) postFields, boundary);
            }
        }
        return Helpers.cast(postFields);
    }

    private static List<FormData> decodeBoundary(ByteList fields, byte[] boundary) {
        byte[] b = fields.list;
        int i = 0, prev = 0;
        int state = 0;
        HttpLexer hl = new HttpLexer().init(fields);

        List<FormData> list = new SimpleList<>();

        while (i < fields.wIndex()) {
            check:
            if (b[i] == '-' && b[i+1] == '-') {
                i += 2;
                for (int j = 0; j < boundary.length; j++) {
                    if (b[i+j] != boundary[j]) {
                        i -= 2;
                        break check;
                    }
                }
                i += boundary.length;
                if (b[i] == '-' && b[i+1] == '-') {
                    int end = i - 4 - boundary.length;
                    if (b[end] != '\r' || b[end+1] != '\n')
                        throw new IllegalArgumentException("Invalid multipart format");
                    list.get(list.size() - 1).data = fields.slice(prev, end - prev);

                    // EOF
                    return list;
                }
                if (b[i] != '\r' || b[i+1] != '\n')
                    throw new IllegalArgumentException("Invalid multipart format");
                i += 2;

                if (state > 0) {
                    int end = i - 6 - boundary.length;
                    if (b[end] != '\r' || b[end+1] != '\n')
                        throw new IllegalArgumentException("Invalid multipart format");
                    list.get(list.size() - 1).data = fields.slice(prev, end - prev);
                }

                FormData data = new FormData();
                hl.index = i;
                try {
                    data.h.readFromLexer(hl);
                } catch (ParseException e) {
                    throw new IllegalArgumentException("Invalid multipart format", e);
                }
                i = prev = hl.index;
                list.add(data);

                state = 1;
            }
            if (state == 0)
                throw new IllegalArgumentException("Invalid multipart format");
            i++;
        }
        throw new IllegalArgumentException("Unexpected EOF");
    }

    public static final class FormData {
        public Headers h = new Headers();
        public ByteList data;

        @Override
        public String toString() {
            return "FormData{" + "h=" + h + ", data=" + data + '}';
        }
    }

    public String payloadUTF() {
        if (postFields == null) {
            return null;
        } else if (postFields instanceof ByteList) {
            String pf;
            postFields = pf = IOUtil.SharedCoder.get().decode((ByteList) postFields);
            return pf;
        }
        throw new IllegalStateException("Parsed");
    }

    public ByteList payload() {
        if (postFields == null) {
            return null;
        } else if (postFields instanceof ByteList) {
            return (ByteList) postFields;
        }
        throw new IllegalStateException("Parsed");
    }

    public Map<String, String> getFields() {
        if (getFields instanceof CharSequence) {
            getFields = getQueries((CharSequence) getFields, "&");
        }
        return Helpers.cast(getFields);
    }

    public String getFieldsRaw() {
        if (getFields == null) {
            return null;
        } else if (getFields instanceof String) {
            return (String) getFields;
        }
        throw new IllegalStateException("Parsed");
    }

    public Map<String, String> cookie() {
        if (cookie == null) {
            this.cookie = getQueries(headers.get("Cookie"), "; ");
        }
        return cookie;
    }

    private static Map<String, String> getQueries(CharSequence query, String delimiter) {
        SipHashMap<String, String> map = new SipHashMap<>();
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
        return map;
    }

    @Nonnull
    public String header(String s) {
        return headers.getOrDefault(s, "");
    }

    public Map<String, String> fields() {
        SipHashMap<String, String> map = new SipHashMap<>(getFields());
        Map<String, String> map1 = payloadFields();
        if (map1 != null) map.putAll(map1);
        return map;
    }

    public String toString() {
        return Action.toString(action) + ' ' + host() + path;
    }
}
