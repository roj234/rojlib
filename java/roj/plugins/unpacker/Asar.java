package roj.plugins.unpacker;

import roj.collect.TrieTree;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.io.CorruptedInputException;
import roj.io.IOUtil;
import roj.io.MyDataInputStream;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author solo6975
 * @since 2022/3/24 18:52
 */
class Asar extends Scene {
	private int unpackCount, dirCount;
	private long baseOffset, size;

	@Override
	public TrieTree<?> load(File file) throws IOException {
		this.file = file;
		try (MyDataInputStream f = new MyDataInputStream(new FileInputStream(file))) {
			if (0x04000000 != f.readInt()) throw new IOException("Not ASAR header (04000000)");

			int headerSize = f.readIntLE();
			int someLen = f.readIntLE();
			int jsonLen = f.readIntLE();

			baseOffset = headerSize + 8;

			CMap root;
			try {
				root = new JSONParser().charset(StandardCharsets.UTF_8).parse(f).asMap();
			} catch (ParseException e) {
				throw new CorruptedInputException("无法解析文件", e);
			}

			TrieTree<PosInfo> tree = this.tree = new TrieTree<>();

			size = 0;
			unpackCount = dirCount = 0;
			recursionTree(IOUtil.getSharedCharBuf(), root, tree);

			System.out.println("加载了"+tree.size()+"个文件("+dirCount+"文件夹/"+unpackCount+"外部资源), 合计"+TextUtil.scaledNumber1024(size));
		}

		return tree;
	}

	private void recursionTree(CharList parents, CMap directory, TrieTree<PosInfo> tree) {
		int len = parents.length();
		for (Map.Entry<String, CEntry> entry : directory.get("files").asMap().entrySet()) {
			parents.setLength(len);
			parents.append(entry.getKey());

			CMap val = entry.getValue().asMap();
			if (val.containsKey("unpacked")) {
				unpackCount++;
				System.out.println("跳过解包的 "+parents);
				continue;
			}

			if (val.containsKey("files")) {
				dirCount++;
				recursionTree(parents.append('/'), val, tree);
			} else {
				int size1 = val.getInt("size");
				size += size1;

				// use string to store long value...
				tree.put(parents.toString(), new PosInfo(baseOffset+val.get("offset").asLong(), size1));
			}
		}
	}
}