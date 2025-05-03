package roj.plugins.kuropack.loader;

import roj.archive.qz.xz.LZMA2InputStream;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/3/17 1:12
 */
public class CPMain extends ClassLoader {
	public static Consumer<Object> callback;
	public static void main(String[] args) throws Exception {
		new CPMain().findClass("cpk.@").getMethod("init").invoke(null);
		callback.accept(args);
	}

	static final class NODE {
		String name;
		int cp, off, len;

		NODE next;
	}

	private final ProtectionDomain pd;
	private final BareZIP zip;
	private final SDI dex;

	private final Object[] cps;
	private final NODE[] classes;
	private final int mask;

	public CPMain() throws IOException {
		var pd = CPMain.class.getProtectionDomain();
		var loc = pd.getCodeSource().getLocation().getPath();
		loc = loc.substring(loc.startsWith("/")?1:0);

		File self = new File(URLDecoder.decode(loc, StandardCharsets.UTF_8));
		CodeSource cs = new CodeSource(new URL("file:"+loc+"!CraftKuro.pak"), pd.getCodeSource().getCertificates());
		this.pd = new ProtectionDomain(cs, pd.getPermissions(), this, pd.getPrincipals());

		this.zip = new BareZIP(self);
		BareZIP.ZFILE file = zip.get("CraftKuro.pak");
		if (file == null || file.flag != 0) throw new IOException("pak error");
		int base = file.pos;

		SDI dex = this.dex = new SDI(self);

		dex.seek(base);

		int magic = dex.readInt();
		long timestamp = dex.readLong();
		long masked = dex.readLong();
		long off = dex.readLong() ^ timestamp;
		if (masked != off) throw new IOException("pak error");

		dex.seek(base+off);

		this.cps = new Object[dex.readVUInt()];
		int count = dex.readVUInt();

		this.classes = new NODE[BareZIP.getMin2PowerOf(count)];
		this.mask = classes.length-1;

		int cpi = -1;
		base += 28;
		int localBase = 0;
		LZMA2InputStream in = new LZMA2InputStream(dex, dex.readVUInt());
		try {
			SDI sdi2 = new SDI(in);
			while (count-- > 0) {
				String name = sdi2.readUTF(sdi2.readVUInt());
				int len = sdi2.readVUInt();

				NODE ref = new NODE();
				ref.len = len;

				if (name.isEmpty()) {
					ref.off = base;
					ref.len = sdi2.readVUInt(); // uLen (len is cLen)

					cps[++cpi] = ref;

					localBase = 0;
					base += len;
				} else {
					ref.off = localBase;
					ref.cp = cpi;
					ref.name = name;

					int j = name.hashCode() & mask;
					ref.next = classes[j];
					classes[j] = ref;

					localBase += len;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			in.close();
			throw e;
		}
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		InputStream in = getStream(name);
		if (in != null) return in;

		for (int j = 1; j < name.length(); j++) {
			if (name.charAt(j) != '/') {
				in = getStream(name.substring(j));
				if (in != null) return in;
				break;
			}
		}

		if (name.endsWith(".class")) {
			in = getStream(name.substring(0, name.length()-6).replace('/', '.'));
			if (in != null) return in;
		}

		return super.getResourceAsStream(name);
	}

	private InputStream getStream(String name) {
		int i = name.hashCode() & mask;
		NODE file = classes[i];
		while (file != null) {
			if (file.name.equals(name)) {
				byte[] cp = getCp(file);
				if (cp == null) break;

				return new ByteArrayInputStream(cp, file.off, file.len);
			}
			file = file.next;
		}

		BareZIP.ZFILE zfile = zip.get(name);
		if (zfile != null) {
			try {
				return zip.getStream(zfile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		int i = name.hashCode() & mask;
		NODE file = classes[i];
		while (file != null) {
			if (file.name.equals(name)) {
				byte[] cp = getCp(file);
				if (cp == null) break;

				return defineClass(name, cp, file.off, file.len, pd);
			}
			file = file.next;
		}

		throw new ClassNotFoundException(name);
	}

	private byte[] getCp(NODE file) {
		Object o = cps[file.cp];
		byte[] cp;
		if (o instanceof NODE n) {
			try {
				dex.seek(n.off);
				LZMA2InputStream in = new LZMA2InputStream(dex, n.len);
				byte[] cpBytes = new byte[n.len];

				int n1 = 0;
				while (n1 < n.len) {
					int r = in.read(cpBytes, n1, n.len - n1);
					if (r < 0) throw new EOFException();
					n1 += r;
				}

				cps[file.cp] = cpBytes;
				cp = cpBytes;

			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		} else {
			cp = (byte[]) o;
		}
		return cp;
	}
}