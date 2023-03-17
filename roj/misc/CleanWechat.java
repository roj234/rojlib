package roj.misc;

import roj.collect.MyHashSet;
import roj.io.FileUtil;
import roj.ui.UIUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author PC
 * @since 2022/1/27 19:35
 */
public class CleanWechat {
	public static void main(String[] args) throws IOException {
		if (args.length < 3) {
			System.out.print("CleanWechat folder time-past(by hours) force");
			System.out.println("清理一定时间以前的文件");
			return;
		}
		long time = System.currentTimeMillis();
		long check = time - Integer.parseInt(args[1]) * 3600000L;
		List<File> del = FileUtil.findAllFiles(new File(args[0]), file -> {
			if (file.lastModified() < check) {
				System.out.println(file);
				return true;
			} else {
				return false;
			}
		});
		MyHashSet<String> fs = new MyHashSet<>();
		if (Boolean.parseBoolean(args[2]) || UIUtil.readBoolean("以上文件会被删除，确定请输入t并按回车")) {
			for (int i = 0; i < del.size(); i++) {
				del.get(i).delete();
				fs.add(del.get(i).getParent());
			}
		}
		FileUtil.removeEmptyPaths(fs);
	}
}
