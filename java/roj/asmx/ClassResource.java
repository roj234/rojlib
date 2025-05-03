package roj.asmx;

import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2025/2/12 13:22
 */
public interface ClassResource {
	String getFileName();
	ByteList getClassBytes();
}
