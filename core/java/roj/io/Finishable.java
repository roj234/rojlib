package roj.io;

import java.io.IOException;

/**
 * 回收当前级别的资源而不关闭它包装的下级对象
 */
public interface Finishable {
	void finish() throws IOException;
}
