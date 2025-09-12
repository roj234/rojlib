package roj.text.diff;

import roj.config.mapper.Optional;
import roj.io.IOUtil;
import roj.reflect.Unaligned;
import roj.text.CharList;
import roj.text.TextReader;
import roj.ui.EasyProgressBar;

import java.io.File;
import java.io.IOException;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2024/3/5 2:30
 */
public final class DiffInfo {
	public static final EasyProgressBar bar = new EasyProgressBar("进度");

	public transient File leftFile, rightFile;
	public String left, right;
	@Optional
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
		diff = d.calculateDiffLength(data2, 0, data2.length, Integer.MAX_VALUE);
		minSize = Math.min(data.length, data2.length);
		bar.increment(1);
	}

	private static byte[] read(File file, boolean isText) throws IOException {
		if (isText) {
			CharList sb = IOUtil.getSharedCharBuf();
			try (TextReader in = TextReader.auto(file)) {
				sb.readFully(in, true);
				byte[] out = new byte[sb.length()*2];
				U.copyMemory(sb.list, Unaligned.ARRAY_CHAR_BASE_OFFSET, out, Unaligned.ARRAY_BYTE_BASE_OFFSET, out.length);
				return out;
			}
		} else {
			return IOUtil.read(file);
		}
	}

	@Override
	public String toString() {
		return "Diff: "+left+" <=> "+right +" => "+diff+"/"+minSize;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof DiffInfo result)) return false;

		if (leftFile != null ? !leftFile.equals(result.leftFile) : result.left.equals(left)) return false;
		return rightFile != null ? rightFile.equals(result.rightFile) : result.right.equals(right);
	}

	@Override
	public int hashCode() {
		int result = leftFile != null ? leftFile.hashCode() : left.hashCode();
		result = 31 * result + (rightFile != null ? rightFile.hashCode() : right.hashCode());
		return result;
	}
}