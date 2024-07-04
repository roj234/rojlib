package roj.net.http;

import roj.net.http.server.Response;
import roj.net.http.server.StringResponse;

import java.io.IOException;

public class IllegalRequestException extends IOException {
	public final int code;
	public Response response;

	public IllegalRequestException(int code) {
		this.code = code;
	}

	public IllegalRequestException(int code, String msg) {
		super(msg);
		this.code = code;
		response = msg == null ? null : new StringResponse(msg);
	}

	public IllegalRequestException(int code, String msg, Throwable cause) {
		super(msg, cause);
		this.code = code;
		response = new StringResponse(msg);
	}

	public IllegalRequestException(int code, Throwable x) {
		super(x);
		this.code = code;
		response = StringResponse.detailedErrorPage(code, x);
	}

	public IllegalRequestException(int code, Response ret) {
		this.code = code;
		response = ret;
	}

	@Override
	public Throwable fillInStackTrace() {
		return this;
	}
}