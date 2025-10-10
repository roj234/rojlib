package roj.debug;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFileWriter;
import roj.asm.AsmCache;
import roj.asm.ClassNode;
import roj.ci.annotation.RedirectTo;
import roj.util.Helpers;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;

/**
 * @author Roj234
 * @since 2024/5/15 16:45
 */
public class DebugTool {
	@RedirectTo("roj/config/mapper/ObjectMapper.__redirected__inspect(Ljava/lang/Object;)Ljava/lang/String;")
	public static String inspect(Object o) {
		return "failed to inspect "+o.getClass().getName()+": ObjectMapper module not present.";
	}

	public static void dump(String id, ClassNode data) {
		try {
			Dumper.zip.beginEntry(new ZEntry(id+"/"+data.name()+".class"));
			AsmCache.toByteArrayShared(data).writeToStream(Dumper.zip);
			Dumper.zip.closeEntry();
		} catch (Exception ignored) {}
	}
	private static final class Dumper {
		static ZipFileWriter zip;
		static {
			try {
				AsmCache.clear(); // ZFW may call ClassDefiners
				zip = new ZipFileWriter(new File("IL-ClassDump.zip"));
				Runtime.getRuntime().addShutdownHook(new Thread(() -> {
					try {
						zip.close();
					} catch (IOException e) {
						Helpers.athrow(e);
					}
				}));
			} catch (IOException e) {
				Helpers.athrow(e);
			}
		}
	}

	public static void heapdump() {
		try {
			MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			ObjectName mbeanName = new ObjectName("com.sun.management:type=HotSpotDiagnostic");
			server.invoke(mbeanName, "dumpHeap", new Object[]{"coredump_"+System.nanoTime()+".hprof", true}, new String[]{String.class.getName(), boolean.class.getName()});
			System.out.println("Heap dump created.");
		} catch (Exception e) {
			System.out.println("Failed to create heap dump");
			e.printStackTrace();
		}
	}
}