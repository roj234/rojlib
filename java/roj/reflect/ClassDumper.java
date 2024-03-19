package roj.reflect;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFileWriter;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/5/15 16:45
 */
public final class ClassDumper {
	public static final boolean DUMP_ENABLED = System.clearProperty("roj.debug.dumpClass") != null;
	private static ZipFileWriter dumper;
	static {
		if (DUMP_ENABLED) {
			try {
				dumper = new ZipFileWriter(new File("IL-Debug-ClassDump.zip"));
				Runtime.getRuntime().addShutdownHook(new Thread(() -> {
					try {
						dumper.close();
					} catch (IOException e) {
						Helpers.athrow(e);
					}
				}));
			} catch (IOException e) {
				Helpers.athrow(e);
			}
		}
	}

	public static void dump(String id, ConstantData data) {
		try {
			dumper.beginEntry(new ZEntry(id+"/"+data.name+".class"));
			Parser.toByteArrayShared(data).writeToStream(dumper);
			dumper.closeEntry();
		} catch (Exception ignored) {}
	}
	public static void dump(String id, ByteList data) {
		try {
			dumper.beginEntry(new ZEntry(id+"/"+Parser.parseAccess(data.slice(), false)+".class"));
			data.writeToStream(dumper);
			dumper.closeEntry();
		} catch (Exception ignored) {}
	}
}