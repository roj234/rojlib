package roj.net.tcp.client;

import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.config.word.AbstLexer;
import roj.math.MathUtils;
import roj.math.Version;
import roj.net.tcp.serv.util.Notify;
import roj.net.tcp.util.*;

import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/5 15:30
 */
public final class HTTPResponse {
    public final String version, content, code;
    public final Map<String, String> headers;

    public HTTPResponse(String version, String code, Map<String, String> headers, String content) {
        this.version = version;
        this.code = code;
        this.headers = headers;
        this.content = content;
    }

    public Version version() {
        return new Version(version);
    }

    @Override
    public String toString() {
        return "HTTPResponse{" + "v='" + version + '\'' + ", content='" + AbstLexer.addSlashes(content) + '\'' + ", code=" + code + ", headers=" + headers + '}';
    }

    public static HTTPResponse parse(WrappedSocket socket, long timeout, int maxReceive) throws ParseException {
        Object[] data = SharedConfig.SYNC_BUFFER.get();

        StreamLikeSequence plain = (StreamLikeSequence) data[0];
        HTTPHeaderLexer lexer = ((HTTPHeaderLexer) data[1]).init(plain.init(socket, timeout, maxReceive));

        try {
            return getRequest(lexer, socket, maxReceive);
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
            throw lexer.err(code.toString(), notifyException);
        } finally {
            lexer.init((CharSequence) null);
            plain.release();
        }
    }

    private static HTTPResponse getRequest(HTTPHeaderLexer lexer, WrappedSocket socket, int contentMax) throws ParseException {
        String version = lexer.readHttpWord();

        if (version == null || !version.startsWith("HTTP/"))
            throw lexer.err("Illegal header " + version);

        String codeNum = lexer.readHttpWord();
        try {
            MathUtils.parseInt(codeNum);
        } catch (NumberFormatException e) {
            throw lexer.err("Illegal code " + codeNum);
        }

        codeNum = codeNum + lexer.readLine();

        Map<String, String> headers = new MyHashMap<>();
        String t;
        while (true) {
            t = lexer.readHttpWord();
            if (t == SharedConfig._ERROR) {
                throw lexer.err("Unexpected " + t);
            } else if (t == SharedConfig._SHOULD_EOF) {
                break;
            } else if (t == null) {
                break;
            } else {
                headers.put(t, lexer.readHttpWord());
            }
        }

        try {
            t = lexer.content(headers.get("Content-Length"), contentMax);
        } catch (ParseException e) {
            throw lexer.err("Excepting EOF, got " + e.getMessage());
        }

        version = version.substring(version.indexOf('/') + 1);

        return new HTTPResponse(version, codeNum, headers, t);
    }
}
