package roj.reflect;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFileWriter;
import roj.asm.AsmShared;
import roj.asm.ClassNode;
import roj.asm.Parser;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/5/15 16:45
 */
public final class Debug {
	public static final boolean CLASS_DUMP = System.clearProperty("roj.debug.dumpClass") != null;
	private static ZipFileWriter dumper;
	static {
		if (CLASS_DUMP) {
			try {
				AsmShared.reset(); // ZFW may call ClassDefiners
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

	public static void dump(String id, ClassNode data) {
		try {
			dumper.beginEntry(new ZEntry(id+"/"+data.name()+".class"));
			Parser.toByteArrayShared(data).writeToStream(dumper);
			dumper.closeEntry();
		} catch (Exception ignored) {}
	}
	public static void dump(String id, ByteList data) {
		try {
			dumper.beginEntry(new ZEntry(id+"/"+Parser.parseAccess(data.slice(), false).name+".class"));
			data.writeToStream(dumper);
			dumper.closeEntry();
		} catch (Exception ignored) {}
	}
}