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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Request {
    private final int action;
    private final String path, version;
    private final Map<String, String> headers;
    private final InetSocketAddress remote;

    private Object postFields;
    private Map<String, String> getFields;

    private Request(int action, String version, String path, Map<String, String> headers, String postFields, InetSocketAddress remote) {
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
        return headers.get("Host");
    }

    public String path() {
        int qo = path.indexOf('?');
        if (qo != -1) {
            return path.substring(0, qo);
        } else {
            return path;
        }
    }

    public Map<String, String> headers() {
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
        List<String> queries = TextUtil.splitStringF(new ArrayList<>(), query, '&');
        for (String member : queries) {
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
        Object[] data = SharedConfig.SYNC_BUFFER.get();

        StreamLikeSequence plain = (StreamLikeSequence) data[0];
        HTTPHeaderLexer lexer = ((HTTPHeaderLexer) data[1]).init(plain.init(socket, router));

        try {
            return getRequest(lexer, router, socket);
        } catch (Notify notifyException) {
            ResponseCode code = ResponseCode.INTERNAL_ERROR;
            switch (notifyException.code) {
                case -127:
                    code = ResponseCode.ENTITY_TOO_LARGE;
                    break;
                case -128:
                    code = ResponseCode.TIMEOUT;
                    break;
            }
            throw new IllegalRequestException(code, notifyException);
        } finally {
            lexer.init((CharSequence) null);
            plain.release();
        }
    }

    public static Request getRequest(HTTPHeaderLexer lexer, Router router, WrappedSocket socket) throws IllegalRequestException {
        String method = lexer.readHttpWord(),
                path = lexer.readHttpWord(),
                version = lexer.readHttpWord();

        if (version == null || !version.startsWith("HTTP/"))
            throw new IllegalRequestException(ResponseCode.BAD_REQUEST, "Illegal header " + version);

        if (path.length() > 1024) {
            throw new IllegalRequestException(ResponseCode.URI_TOO_LONG);
        }

        int act = Action.valueOf(method);
        if (!router.checkAction(act))
            throw new IllegalRequestException(ResponseCode.METHOD_NOT_ALLOWED, "Illegal action " + method);

        String postFields = null;

        Map<String, String> headers = new MyHashMap<>();
        String t;
        while (true) {
            t = lexer.readHttpWord();
            if (t == SharedConfig._ERROR) {
                throw new IllegalRequestException(ResponseCode.BAD_REQUEST, lexer.err("Unexpected " + t));
            } else if (t == SharedConfig._SHOULD_EOF) {
                //if (t != (t = lexer.readHttpWord())) {
                    //if (act == Action.POST) {
                        //lexer.retractWord();
                    //} else {
                    //    throw new IllegalRequestException(ResponseCode.BAD_REQUEST, lexer.exception("Excepting EOF, got " + t));
                    //}
                //}
                // streamlike: end
                break;
            } else if (t == null) {
                break;
            } else {
                headers.put(t, lexer.readHttpWord());
            }
        }

        if (act == Action.POST) {
            try {
                postFields = lexer.content(headers.get("Content-Length"), router.postMaxLength());
            } catch (ParseException e) {
                throw new IllegalRequestException(ResponseCode.BAD_REQUEST, lexer.err("Excepting EOF, got " + e.getMessage()));
            }
        }

        version = version.substring(version.indexOf('/') + 1);

        final ByteList buffer = socket.buffer();
        if(buffer != null)
            buffer.clear();

        try {
            return new Request(act, version, path, headers, postFields, (InetSocketAddress) socket.socket().getRemoteAddress());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Request parseAsync(WrappedSocket socket, Router router, Object[] holder) throws IllegalRequestException {
        Object obj = holder[0];


        HTTPHeaderLexer lexer;
        if(obj == null) {
            StreamLikeSequence seq = new StreamLikeSequence(SharedConfig.STREAM_SEQ_INITIAL_CAPACITY, true);
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
            ResponseCode code = ResponseCode.INTERNAL_ERROR;
            switch (notifyException.code) {
                case -127:
                    code = ResponseCode.ENTITY_TOO_LARGE;
                    break;
                case -128:
                    code = ResponseCode.TIMEOUT;
                    break;
            }
            throw new IllegalRequestException(code, notifyException);
        }
    }

    public String headers(String s) {
        return headers.get(s);
    }
}
