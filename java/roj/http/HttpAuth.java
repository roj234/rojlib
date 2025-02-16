package roj.http;

import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2023/5/16 0016 15:44
 */
@Deprecated
public class HttpAuth {
	public static String makeBasicAuth(String user, String pass) {
		ByteList in = new ByteList().putUTFData(user).put(':').putUTFData(pass);
		String auth = Base64.encode(in, IOUtil.getSharedCharBuf()).toString();
		in._free();
		return auth;
	}
}