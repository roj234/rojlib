package roj.text;

import roj.util.ArrayCache;
import roj.util.Helpers;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/11/17 05:05
 */
public interface CharSource extends Closeable {
	int next();
	default int peek() {return peek(0);}
	int peek(int n);
	int position();

	static CharSource forString(CharSequence str) {
		return new CharSource() {
			int index;

			public int next() {return index >= str.length() ? -1 : str.charAt(index++);}
			public int peek(int n) {
				n += index;
				return n >= str.length() ? -1 : str.charAt(n);
			}
			public int position() {return index;}

			public void close() {}
		};
	}

	static CharSource forReader(TextReader reader) {
		return new CharSource() {
			// initial size: 4096 or larger
			char[] buf = ArrayCache.getIOCharBuffer();
			int index, count;
			int totalOffset;

			private int fill(int n, int needIndex) {
				// 丢弃已经被next()读取过的字符
				// 由next()调用时，应会丢弃所有字符
				if (index > 0) {
					System.arraycopy(buf, index, buf, 0, count -= index);
					totalOffset += index;
					index = 0;
					needIndex = n;
				}

				// 如果buf无法容纳这些字符
				// 这个分支不会从next()调用进入
				if (needIndex >= buf.length) {
					// 返回长度不小于needIndex的缓冲区
					char[] newBuf = ArrayCache.getCharArray(needIndex);
					System.arraycopy(buf, 0, newBuf, 0, count);
					ArrayCache.putArray(buf);
					buf = newBuf;
				}

				try {
					// 读取数据
					int newRead = reader.fill(buf, count, buf.length - count);
					if (newRead > 0) count += newRead;
				} catch (IOException e) {
					// no catch
					Helpers.athrow(e);
				}
				return needIndex;
			}

			@Override
			public int next() {
				if (index >= count) fill(0, 0);
				return index < count ? buf[index++] : -1;
			}

			@Override
			public int peek(int n) {
				int needIndex = n + index;
				// 如果超过缓冲区
				if (needIndex >= count) {
					needIndex = fill(n, needIndex);
				}

				return needIndex < count ? buf[needIndex] : -1;
			}

			@Override
			public int position() {
				return totalOffset + index;
			}

			@Override
			public void close() throws IOException {
				reader.close();
				ArrayCache.putArray(buf);
			}
		};
	}
}
