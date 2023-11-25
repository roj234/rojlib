package roj.text;

import roj.collect.AbstractIterator;
import roj.collect.SimpleList;
import roj.io.IOUtil;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/9/7 0007 9:36
 */
public interface LinedReader extends Closeable {
	default String readLine() throws IOException {
		CharList s = IOUtil.getSharedCharBuf();
		boolean ok = readLine(s);
		return ok ? s.toString() : null;
	}
	boolean readLine(CharList buf) throws IOException;

	default List<String> readLines() throws IOException {
		try {
			List<String> list = new SimpleList<>();
			CharList sb = IOUtil.getSharedCharBuf();
			while (readLine(sb)) {
				list.add(sb.toString());
				sb.clear();
			}
			return list;
		} finally {
			close();
		}
	}

	default Iterable<String> lines() { return this::iterator; }
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
}
