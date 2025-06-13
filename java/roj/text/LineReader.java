package roj.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.collect.AbstractIterator;
import roj.collect.ArrayList;
import roj.concurrent.OperationDone;
import roj.io.IOUtil;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * @author Roj234
 * @since 2023/9/7 9:36
 */
public interface LineReader extends Iterable<String> {
	@Nullable
	default String readLine() throws IOException {
		var sb = IOUtil.getSharedCharBuf();
		boolean read = readLine(sb);
		return read ? sb.toString() : null;
	}
	boolean readLine(CharList buf) throws IOException;

	default List<String> lines() throws IOException {
		List<String> list = new ArrayList<>();
		CharList sb = IOUtil.getSharedCharBuf();
		while (readLine(sb)) {
			list.add(sb.toString());
			sb.clear();
		}
		return list;
	}

	@NotNull
	default Iterator<String> iterator() {
		return new AbstractIterator<String>() {
			@Override
			protected boolean computeNext() {
				try {
					return (result = readLine()) != null;
				} catch (IOException e) {
					return false;
				}
			}
		};
	}

	default int skipLines(int lines) throws IOException {
		int lines1 = lines;
		var sb = IOUtil.getSharedCharBuf();
		while (lines > 0) {
			if (!readLine(sb)) break;
			sb.clear();

			lines--;
		}
		return lines - lines1;
	}

	static Impl create(CharSequence str) { return new Impl(str, true); }
	static Impl create(CharSequence str, boolean removeEmpty) { return new Impl(str, removeEmpty); }
	static List<String> getAllLines(CharSequence str, boolean clean) {return create(str, clean).lines();}

	/**
	 * @author Roj234
	 * @since 2021/5/27 0:12
	 */
	final class Impl implements Iterator<String>, LineReader {
		private final CharSequence str;
		private final boolean keepEmpty;
		private int i;

		private Impl(CharSequence s, boolean cleanEmpty) {
			this.str = s;
			this.keepEmpty = !cleanEmpty;
		}

		private String tmp;
		@NotNull
		@Override
		public Iterator<String> iterator() { i = 0; return this; }
		@Override
		public boolean hasNext() {
			if (tmp == null) tmp = readLine();
			return tmp != null;
		}
		@Override
		public String next() {
			if (!hasNext()) throw new NoSuchElementException();

			String t = tmp;
			tmp = null;
			return t;
		}

		@Override
		public int skipLines(int lines) {
			int lines1 = lines;
			int i = this.i;
			while (--lines > 0) {
				int j = TextUtil.gNextCRLF(str, i);
				if (j < 0) break;
				i = j;
			}
			this.i = i;
			return lines - lines1;
		}

		@Override
		public String readLine() {
			try {
				return LineReader.super.readLine();
			} catch (IOException e) {
				throw OperationDone.NEVER;
			}
		}

		@Override
		public List<String> lines() {
			try {
				return LineReader.super.lines();
			} catch (IOException e) {
				throw OperationDone.NEVER;
			}
		}

		@Override
		public boolean readLine(CharList buf) {
			int prevLen = buf.length();
			while (true) {
				if (i < 0) return false;
				i = TextUtil.gAppendToNextCRLF(str, i, buf, -1);
				if (keepEmpty || buf.length() > prevLen) return true;
			}
		}
	}
}