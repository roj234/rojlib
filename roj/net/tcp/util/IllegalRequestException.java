package roj.net.tcp.util;

public final class IllegalRequestException extends Exception {
    public final ResponseCode code;

    public IllegalRequestException(ResponseCode code) {
        this.code = code;
    }

    public IllegalRequestException(ResponseCode code, String msg) {
        super(msg);
        this.code = code;
    }

    public IllegalRequestException(ResponseCode code, Throwable x) {
        super(x);
        this.code = code;
    }
}
