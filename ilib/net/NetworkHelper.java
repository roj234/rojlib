package ilib.net;

import ilib.net.packet.MsgSyncField;
import ilib.net.packet.MsgSyncFields;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2020/9/25 13:25
 */
public class NetworkHelper {
	public static final MyChannel IL = new MyChannel("IL");

	static {
		IL.registerMessage(null, MsgSyncFields.class, 0, null);
		IL.registerMessage(null, MsgSyncField.class, 1, null);
	}

	public static void writeStringList(ByteList buf, String[] list) {
		if (list == null) {
			buf.putVarInt(-1);
			return;
		}
		buf.putVarInt(list.length);
		for (String s : list) buf.putVUIUTF(s);
	}

	public static String[] getStringList(ByteList buf) {
		int length = buf.readVarInt();
		if (length == -1) return null;
		String[] result = new String[length];
		for (int i = 0; i < length; i++) {
			result[i] = buf.readVUIUTF();
		}
		return result;
	}
}
