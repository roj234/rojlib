package roj.misc;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/1/4 0004 15:52
 */
public class RemoveFolder {
	public static void main(String[] args) throws IOException {
		try (ZipArchive za = new ZipArchive(args[0])) {
			for (ZEntry ze : za.getEntries().values()) {
				if (ze.getName().endsWith("/")) {
					za.put(ze.getName(), null);
				}
			}
			za.store();
		}
	}
}