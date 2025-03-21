package roj.io.vfs;

/**
 * @author Roj234
 * @since 2025/4/4 0004 19:55
 */
public interface VirtualFile {
	default boolean exists() {return true;}
	default boolean isFile() {return exists() && !isDirectory();}
	default boolean isDirectory() {return false;}
	String getName();
	String getPath();
	long length();
	long lastModified();
}
