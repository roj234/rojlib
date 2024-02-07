package roj.net.http.auth;

import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2023/5/16 0016 15:44
 */
public class BasicAuth implements AuthScheme {
	public final String user, pass;

	public BasicAuth(String user, String pass) {
		this.user = user;
		this.pass = pass;
	}

	@Override
	public String type() {
		return "Basic";
	}

	@Override
	public String check(String header) {
		ByteList out = IOUtil.ddLayeredByteBuf();
		String auth = Base64.decode(header, out).readUTF(out.readableBytes());
		out._free();

		int pos = auth.indexOf(':');
		String user = auth.substring(0,pos);
		String pass = auth.substring(pos+1);
		return user.equals(this.user) && pass.equals(this.pass) ? null : "用户名或密码错误";
	}

	@Override
	public String send() {
		ByteList in = IOUtil.ddLayeredByteBuf().putUTFData(user).put(':').putUTFData(pass);
		ByteList out = IOUtil.ddLayeredByteBuf();
		String auth = Base64.encode(in, out).toString();
		in._free();
		out._free();
		return auth;
	}
}
