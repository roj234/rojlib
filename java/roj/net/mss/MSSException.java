package roj.net.mss;

import roj.RojLib;
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
		super(msg == null ? String.valueOf(code) : msg, cause);
		this.code = code;
	}

	public void notifyRemote(MSSEngine engine, DynByteBuf buf) {
		String msg = getMessage();
		byte[] data = msg == null ? ArrayCache.BYTES : msg.getBytes(StandardCharsets.UTF_8);

		HMAC kd = engine.keyDeriver;
		int extra = 5+data.length + (kd == null ? 0 : kd.getDigestLength());

		if (buf.writableBytes() < extra) return;

		int pos = buf.wIndex();
		buf.put(MSSEngine.P_ALERT).putShort(extra-3).put(code);
		if (RojLib.IS_DEV) buf.put(data.length).put(data);
		else buf.put(0);

		if (kd != null) {
			kd.init(engine.deriveKey("alert", kd.getDigestLength()));
			kd.update(buf.slice(pos+4,2+data.length));
			buf.put(kd.digestShared());
		}
	}

	public boolean shouldNotifyRemote() {return code != MSSEngine.ILLEGAL_PACKET;}
}