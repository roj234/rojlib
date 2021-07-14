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
public class ResponseCode {
    private final int id;
    private final String reason;

    static CharMap<ResponseCode> byId = new CharMap<>();

    public ResponseCode(int id, String reason) {
        this.id = id;
        this.reason = reason;
        byId.put((char) id, this);
    }

    public final String toString() {
        return String.valueOf(id) + ' ' + reason;
    }

    public static final ResponseCode
            SWITCHING_PROTOCOL = new ResponseCode(101, "Switching Protocols"),

    OK = new ResponseCode(200, "OK"),

    BAD_REQUEST = new ResponseCode(400, "Bad Request"),
            FORBIDDEN = new ResponseCode(403, "Forbidden"),
            NOT_FOUND = new ResponseCode(404, "Not Found"),
            METHOD_NOT_ALLOWED = new ResponseCode(405, "Method Not Allowed"),
            TIMEOUT = new ResponseCode(408, "Request Timeout"),
            ENTITY_TOO_LARGE = new ResponseCode(413, "Request Entity Too Large"),
            URI_TOO_LONG = new ResponseCode(414, "Request-URI Too Long"),
            UPGRADE_REQUIRED = new ResponseCode(426, "Upgrade Required"),

    INTERNAL_ERROR = new ResponseCode(500, "Internal Server Error"),
            UNAVAILABLE = new ResponseCode(503, "Service Unavailable");

    public String description() {
        return null;
    }

    public static ResponseCode byId(int id) {
        return byId.get((char) id);
    }
}
