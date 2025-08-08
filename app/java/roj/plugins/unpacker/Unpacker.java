package roj.plugins.unpacker;

import roj.collect.TrieTree;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/5/31 0:38
 */
public interface Unpacker extends Closeable {
	TrieTree<?> load(File file) throws IOException;
	void export(File path, String prefix) throws IOException;
	default void close() throws IOException {}
}