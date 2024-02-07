package roj.misc;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFileWriter;
import roj.collect.TrieTree;
import roj.io.IOUtil;
import roj.math.MutableInt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/1/4 0004 15:52
 */
public class RemoveFolder {
	public static void main(String[] args) throws IOException {
		if (args.length > 1) {
			deobf(args);
			return;
		}

		try (ZipArchive za = new ZipArchive(args[0])) {
			for (ZEntry ze : za.getEntries().values()) {
				if (ze.getName().endsWith("/")) {
					za.put(ze.getName(), null);
				}
			}
			za.store();
		}
	}
	public static void deobf(String[] args) throws IOException {
		TrieTree<ZEntry> terminator = new TrieTree<>();
		ZipFileWriter out2 = new ZipFileWriter(new File(args[1]));
		System.out.println(args[1]);
		try (ZipArchive za = new ZipArchive(args[0])) {
			for (ZEntry ze : za.getEntries().values()) {
				if (ze.getName().endsWith("/")) {
					if (ze.getSize() > 0) {
						try (InputStream in = za.getInput(ze)) {
							byte[] data = IOUtil.read(in);
							out2.beginEntry(new ZEntry(ze.getName().substring(0, ze.getName().length()-1)));
							out2.write(data);
							out2.closeEntry();
							System.out.println("转换假文件夹 "+ze);
							continue;
						} catch (Exception e) {
							System.out.println("忽略UE文件夹 "+ze);
							continue;
						}
					}
					System.out.println("忽略真文件夹 "+ze);
				} else {
					terminator.put(ze.getName(), ze);
				}
			}
			for (ZEntry ze : za.getEntries().values()) {
				if (!ze.getName().endsWith("/")) {
					Map.Entry<MutableInt, ZEntry> fake = terminator.longestMatches(ze.getName(), 0, ze.getName().length());
					if (fake != null && fake.getKey().value < ze.getName().length()) {
						System.out.println("忽略假货 "+fake.getValue());
						System.out.println("self="+ze);
						za.put(fake.getValue().getName(), null);
					} else {
						try (InputStream in = za.getInput(ze)) {
							byte[] data = IOUtil.read(in);
							out2.beginEntry(new ZEntry(ze.getName()));
							out2.write(data);
							out2.closeEntry();
							System.out.println("转换假文件 "+ze);
							continue;
						} catch (Exception e) {
							System.out.println("忽略UE文件 "+ze);
							continue;
						}
					}
				}
			}
			za.store();
			out2.close();
		}
	}
}