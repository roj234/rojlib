package roj.net.mss;

import roj.crypt.HMAC;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Roj233
 * @since 2021/12/22 13:46
 */
public class MSSException extends IOException {
	public final int code;

	public MSSException(String msg) {
		super(msg);
		this.code = MSSEngine.INTERNAL_ERROR;
	}

	public MSSException(int code, String msg, Throwable cause) {
		super(msg, cause);
		this.code = code;
	}

	public void notifyRemote(MSSEngine engine, DynByteBuf buf) {
		String msg = getMessage();
		byte[] data = msg == null ? ArrayCache.BYTES : msg.getBytes(StandardCharsets.UTF_8);

		HMAC kd = engine.keyDeriver;
		int extra = 5+data.length + (kd == null ? 0 : kd.getDigestLength());

		if (buf.writableBytes() < extra) return;

		int pos = buf.wIndex();
		buf.put(MSSEngine.P_ALERT).putShort(extra-3).put((byte) code).put((byte) data.length).put(data);

		if (kd != null) {
			kd.setSignKey(engine.deriveKey("alert", kd.getDigestLength()));
			kd.update(buf.slice(pos+3,2+data.length));
			buf.put(kd.digestShared());
		}
	}
}