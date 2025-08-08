package roj.io;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/1/18 0:14
 */
public interface UnsafeOutputStream {
	void write0(Object ref, long address, int length) throws IOException;
}