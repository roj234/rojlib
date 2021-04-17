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
