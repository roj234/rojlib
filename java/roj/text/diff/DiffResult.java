package roj.text.diff;

import roj.config.serial.Optional;
import roj.config.serial.SerializerFactory;
import roj.config.serial.Serializers;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextReader;
import roj.ui.EasyProgressBar;
import roj.util.BsDiff;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2024/3/5 0005 2:30
 */
public final class DiffResult {
	static final SerializerFactory sf = Serializers.newSerializerFactory();
	static final EasyProgressBar bar = new EasyProgressBar("进度");

	public transient File leftFile, rightFile;
	public String left, right;
	public int diff, pos;
	@Optional
	public int minSize;

	public void postProcess(boolean isText) throws Exception {
		if (minSize != 0) return;

		BsDiff d = new BsDiff();
		byte[] data = read(leftFile, isText);
		System.out.print(".");
		d.setLeft(data);
		System.out.print(".");
		byte[] data2 = read(rightFile, isText);
		System.out.print(".");
		diff = d.getDiffLength(data2, 0, data2.length, Integer.MAX_VALUE);
		minSize = Math.min(data.length, data2.length);
		bar.addCurrent(1);
	}

	private static byte[] read(File file, boolean isText) throws IOException {
		if (isText) {
			CharList sb = IOUtil.getSharedCharBuf();
			try (TextReader in = TextReader.auto(file)) {
				sb.readFully(in, true);
				byte[] out = new byte[sb.length()*2];
				u.copyMemory(sb.list, Unsafe.ARRAY_CHAR_BASE_OFFSET, out, Unsafe.ARRAY_BYTE_BASE_OFFSET, out.length);
				return out;
			}
		} else {
			return IOUtil.read(file);
		}
	}
}