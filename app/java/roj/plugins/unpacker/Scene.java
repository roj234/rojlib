package roj.plugins.unpacker;

import roj.collect.TrieTree;
import roj.io.CorruptedInputException;
import roj.io.IOUtil;
import roj.io.MyDataInputStream;
import roj.io.source.FileSource;
import roj.io.source.SourceInputStream;
import roj.text.TextUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;

/**
 * @author Roj234
 * @since 2023/12/1 0:44
 */
class Scene implements Unpacker {
	File file;
	TrieTree<PosInfo> tree;

	@Override
	public TrieTree<?> load(File file) throws IOException {
		this.file = file;
		try (var b = new MyDataInputStream(new FileInputStream(file))) {
			int len = b.readIntLE();
			String ver = b.readUTF(len);
			int verInt = Integer.parseInt(ver.substring(4));
			if (!ver.startsWith("PKGV00") || verInt > 22) throw new CorruptedInputException("不是受支持的版本: "+ver);

			TrieTree<PosInfo> tree = this.tree = new TrieTree<>();

			int count = b.readIntLE();
			long totalLen = 0;
			for (int i = 0; i < count; i++) {
				String name = b.readUTF(b.readIntLE());
				int offset = b.readIntLE();
				len = b.readIntLE();
				totalLen += len;
				tree.put(name, new PosInfo(offset, len));
			}

			long offset = b.position();
			tree.forEach((k, v) -> v.offset += offset);

			System.out.println("加载了"+tree.size()+"个文件, 合计"+TextUtil.scaledNumber1024(totalLen));
			return tree;
		}
	}

	@Override
	public void export(File path, String prefix) throws IOException {
		try (FileSource in = new FileSource(file, false)) {
			tree.forEachSince(prefix, (k, v) -> {
				File file = new File(path, k.toString());
				file.getParentFile().mkdirs();

				try {
					IOUtil.createSparseFile(file, v.length);

					FileOutputStream out = new FileOutputStream(file);
					try {
						in.seek(v.offset);
						IOUtil.copyStream(new SourceInputStream(in, v.length, false), out);
					} finally {
						IOUtil.closeSilently(out);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}

				return FileVisitResult.CONTINUE;
			});
		}
	}
}