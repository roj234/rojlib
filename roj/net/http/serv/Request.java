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
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.io.IOUtil;
import roj.math.Version;
import roj.net.http.Headers;
import roj.net.http.HttpLexer;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public final class Request {
    private final int action;
    private final String path, version;
    final Headers headers;

    Object postFields, getFields;
    private Map<String, String> cookie;

    Request(int action, String version, String path) {
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
    public Map<String, String> payloadFields() {
        if (postFields instanceof CharSequence) {
            CharSequence pf = (CharSequence) postFields;
            if (pf instanceof ByteList) {
                String ct = headers.getOrDefault("Content-Type", "");
                if (ct.startsWith("multipart")) {
                    Map<String, Object> fields = convertToFields(payloadMultipart());
                    for (Iterator<Object> itr = fields.values().iterator(); itr.hasNext(); ) {
                        Object o = itr.next();
                        if (!(o instanceof String)) {
                            itr.remove();
                        }
                    }
                    return Helpers.cast(postFields = fields);
                } else {
                    pf = IOUtil.SharedUTFCoder.get().decodeR((ByteList) pf);
                }
            }
            postFields = getQueries(pf, "&");
        }
        return Helpers.cast(postFields);
    }

    public static Map<String, Object> convertToFields(List<FormData> data) {
        UTFCoder uc = IOUtil.SharedUTFCoder.get();
        Map<String, Object> map = new MyHashMap<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            FormData d = data.get(i);
            String type = d.h.getOrDefault("Content-Type", "text/plain");
            String nm = d.h.get("Content-Disposition");
            int j = nm.indexOf("name=\"");
            if (j < 0) throw new IllegalArgumentException("No name header found");
            map.put(nm.substring(17, nm.indexOf('"', j+1)), type.startsWith("text") ?
                    uc.decode(d.data) : d.data);
        }
        return map;
    }

    public List<FormData> payloadMultipart() {
        st:
        if (postFields instanceof ByteList) {
            String ct = headers.get("Content-Type");
            if (ct == null || !ct.startsWith("multipart")) {
                throw new IllegalArgumentException("Not multipart environment");
            } else {
                // multipart
                String[] hdr = TextUtil.split(ct, ';');
                for (int i = 1; i < hdr.length; i++) {
                    String k = hdr[i].trim();
                    if (k.startsWith("boundary=")) {
                        byte[] boundary = IOUtil.SharedUTFCoder.get().encode(k.substring(9));
                        postFields = decodeBoundary((ByteList) postFields, boundary);
                        break st;
                    }
                }
                throw new IllegalArgumentException("Not found boundary in Content-Type header: " + ct);
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

    @Nullable
    public String payloadUTF() {
        if (postFields == null) {
            return null;
        } else if (postFields instanceof CharSequence) {
            CharSequence pf = (CharSequence) postFields;
            if (pf instanceof ByteList) {
                pf = IOUtil.SharedUTFCoder.get().decode((ByteList) pf);
            }
            return (String) (postFields = pf.toString());
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

    @Nonnull
    public String header(String s) {
        return headers.getOrDefault(s, "");
    }

    public Map<String, String> fields() {
        MyHashMap<String, String> map = new MyHashMap<>(getFields());
        Map<String, String> map1 = payloadFields();
        if (map1 != null) map.putAll(map1);
        return map;
    }

    public String toString() {
        return action + ' ' + host() + path;
    }
}
