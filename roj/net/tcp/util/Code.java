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

import roj.collect.CharMap;

/**
 * A helper class which define the HTTP response codes
 */
public class Code {
    private final int id;
    private final String reason;

    static CharMap<Code> byId = new CharMap<>();

    public Code(int id, String reason) {
        this.id = id;
        this.reason = reason;
        byId.put((char) id, this);
    }

    public final String toString() {
        return String.valueOf(id) + ' ' + reason;
    }

    public static final Code
            SWITCHING_PROTOCOL = new Code(101, "Switching Protocols"),

    OK = new Code(200, "OK"),

    MOVED_PERMANENTLY = new Code(301, "Moved Permanently"),
    FOUND = new Code(302, "Found"),
    NOT_MODIFIED = new Code(304, "Not Modified"),

    BAD_REQUEST = new Code(400, "Bad Request"),
    FORBIDDEN = new Code(403, "Forbidden"),
    NOT_FOUND = new Code(404, "Not Found"),
    METHOD_NOT_ALLOWED = new Code(405, "Method Not Allowed"),
    TIMEOUT = new Code(408, "Request Timeout"),
    ENTITY_TOO_LARGE = new Code(413, "Request Entity Too Large"),
    URI_TOO_LONG = new Code(414, "Request-URI Too Long"),
    UPGRADE_REQUIRED = new Code(426, "Upgrade Required"),

    INTERNAL_ERROR = new Code(500, "Internal Server Error"),
    UNAVAILABLE = new Code(503, "Service Unavailable");

    public String description() {
        return null;
    }

    public static Code byId(int id) {
        return byId.get((char) id);
    }
}
