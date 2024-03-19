package roj.net.mss;

import roj.collect.CharMap;
import roj.util.DynByteBuf;

public class Extension {
	public static final char
		server_name = 0,
		application_layer_protocol = 1,
		pre_shared_certificate = 2,
		session = 3,
		certificate_request = 4,
		certificate = 5;

	public static CharMap<DynByteBuf> read(DynByteBuf buf) {
		int len = buf.readUnsignedShort();
		CharMap<DynByteBuf> map = new CharMap<>(len);
		while (len-- > 0) {
			int id = buf.readUnsignedShort();
			DynByteBuf data = buf.slice(buf.readUnsignedShort());
			map.put((char) id, data);
		}
		return map;
	}

	public static void write(CharMap<DynByteBuf> ext, DynByteBuf buf) {
		if (ext == null) {
			buf.putShort(0);
			return;
		}
		buf.putShort(ext.size());
		for (CharMap.Entry<DynByteBuf> entry : ext.selfEntrySet()) {
			DynByteBuf b = entry.getValue();
			buf.putShort(entry.getCharKey());
			if (b == null) buf.putShort(0);
			else buf.putShort(b.readableBytes()).put(b);

			MSSEngine.free(b);
		}
	}

	public static void writeSSL(CharMap<DynByteBuf> ext, DynByteBuf buf) {
		buf.putShort(0);
		int pos = buf.wIndex();
		if (ext == null) return;
		for (CharMap.Entry<DynByteBuf> entry : ext.selfEntrySet()) {
			DynByteBuf b = entry.getValue();
			buf.putShort(entry.getCharKey());
			if (b == null) {
				buf.putShort(0);
			} else {
				buf.putShort(b.readableBytes()).put(b);
			}
		}
		buf.putShort(pos-2, buf.wIndex()-pos);
	}
}