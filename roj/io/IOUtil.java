package roj.io;

import roj.concurrent.FastThreadLocal;
import roj.text.CharList;
import roj.text.UTFCoder;
import roj.util.ByteList;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

/**
 * @author Roj234
 * @since 2021/5/26 0:13
 */
public class IOUtil {
	public static final FastThreadLocal<UTFCoder> SharedCoder = FastThreadLocal.withInitial(UTFCoder::new);

	/**
	 * Should not use in static initializator!!!
	 */
	public static ByteList getSharedByteBuf() {
		ByteList o = SharedCoder.get().byteBuf;
		o.ensureCapacity(1024);
		o.clear();
		return o;
	}

	public static CharList getSharedCharBuf() {
		CharList o = SharedCoder.get().charBuf;
		o.ensureCapacity(1024);
		o.clear();
		return o;
	}

	private static ByteList read1(Class<?> jar, String path, ByteList list) throws IOException {
		InputStream in = jar.getClassLoader().getResourceAsStream(path);
		if (in == null) throw new FileNotFoundException(path + " is not in jar " + jar.getName());
		return list.readStreamFully(in);
	}

	public static String readAs(InputStream in, String encoding) throws UnsupportedCharsetException, IOException {
		if (encoding.equalsIgnoreCase("UTF-8") || encoding.equalsIgnoreCase("UTF8")) {
			return readUTF(in);
		} else {
			ByteList bl = getSharedByteBuf().readStreamFully(in);
			return new String(bl.list, 0, bl.wIndex(), Charset.forName(encoding));
		}
	}

	public static byte[] read(String path) throws IOException {
		return read1(IOUtil.class, path, getSharedByteBuf()).toByteArray();
	}

	public static byte[] read(Class<?> provider, String path) throws IOException {
		return read1(provider, path, getSharedByteBuf()).toByteArray();
	}

	public static byte[] read(File file) throws IOException {
		return read(new FileInputStream(file));
	}

	public static byte[] read(InputStream in) throws IOException {
		return getSharedByteBuf().readStreamFully(in).toByteArray();
	}

	public static String readUTF(Class<?> jar, String path) throws IOException {
		UTFCoder x = SharedCoder.get();
		x.keep = false;
		x.byteBuf.clear();
		read1(jar, path, x.byteBuf);
		return x.decode();
	}

	public static String readUTF(String path) throws IOException {
		return readUTF(IOUtil.class, path);
	}

	public static String readUTF(File f) throws IOException {
		return readUTF(new FileInputStream(f));
	}

	public static String readUTF(InputStream in) throws IOException {
		UTFCoder x = SharedCoder.get();
		x.keep = false;
		x.byteBuf.clear();
		x.byteBuf.readStreamFully(in);
		return x.decode();
	}

	public static void write(CharSequence cs, File out) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(out)) {
			SharedCoder.get().encodeTo(cs, fos);
		}
	}

	public static void write(CharSequence cs, OutputStream out) throws IOException {
		SharedCoder.get().encodeTo(cs, out);
	}

	public static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
		while (len > 0) {
			int r = in.read(b, off, len);
			if (r < 0) throw new EOFException();
			len -= r;
			off += r;
		}
	}
}