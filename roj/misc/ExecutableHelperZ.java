package roj.misc;

import roj.archive.zip.ZipArchive;
import roj.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipException;

/**
 * xyz.exe which can be opened with a zip tool, however, read only
 *
 * @author solo6975
 * @since 2021/10/6 19:31
 */
public class ExecutableHelperZ {
	public static void main(String[] args) throws IOException {
		if (args.length < 2) {
			System.out.println("ExecutableHelperZ <exe-file> <zip-file> [begin-offset]");
			System.out.println("  用途：简易修改各种自解压zip文件");
			return;
		}

		File file = new File(args[0]);
		byte[] data = IOUtil.read(file);
		int offset = args.length < 3 ? 0 : Integer.parseInt(args[2]);
		try {
			while (true) {
				do {
					while (data[offset++] != 'P') ;
				} while (data[offset++] != 'K');

				offset -= 2;
				try (ZipArchive mzf = new ZipArchive(file, ZipArchive.FLAG_VERIFY, offset, StandardCharsets.UTF_8)) {
					mzf.getEntries();
					break;
				} catch (Throwable ignored) {}
				offset += 2;
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new ZipException(file + " is not a valid executable zip file");
		}

		FileChannel cf = FileChannel.open(new File(args[1]).toPath(), StandardOpenOption.READ);
		FileChannel ct = FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.READ).position(offset);
		cf.transferTo(0, cf.size(), ct);
		cf.close();
		if (ct.size() != ct.position()) {
			ct.truncate(ct.position());
		}
		ct.close();
	}
}
