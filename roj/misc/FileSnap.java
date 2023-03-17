package roj.misc;

import roj.config.serial.ToJson;
import roj.io.IOUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileSnap {
	public static void main(String[] args) throws IOException {
		File file = args.length > 0 ? new File(args[0]) : new File("");
		ToJson j = new ToJson();
		j.valueMap();

		j.key("info");
		j.valueMap();

		j.key("base");
		j.value(file.getAbsolutePath());
		j.key("time");
		j.value(System.currentTimeMillis());

		j.pop();

		j.key("files");
		j.valueMap();
		Iterator(file, j);

		IOUtil.SharedCoder.get().encodeTo(j.getValue(), new FileOutputStream("snap.json"));
	}

	private static void Iterator(File file, ToJson map) {
		if (file.isDirectory()) {
			map.key(file.getName());
			map.valueMap();
			for (File sub : file.listFiles()) {
				Iterator(sub, map);
			}
			map.pop();
		} else if (file.isFile()) {
			FileInfo(file, map);
		}
	}

	private static void FileInfo(File file, ToJson map) {
		map.key(file.getName());
		map.valueList();
		map.value(file.lastModified());
		map.value(file.length());
		map.value(file.isHidden() ? 1 : 0);
		map.pop();
	}
}
