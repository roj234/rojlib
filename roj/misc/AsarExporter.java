package roj.misc;

import roj.collect.TrieTree;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.ui.CLIUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;

/**
 * @author solo6975
 * @since 2022/3/24 18:52
 */
public class AsarExporter {
	public static void main(String[] args) throws IOException, ParseException {
		if (args.length < 2) {
			System.out.println("AsarExporter <file> <store> [manual]");
			return;
		}
		System.out.println("ASAR exporter 1.0 by Roj234");
		RandomAccessFile f = new RandomAccessFile(args[0], "r");
		if (0x04000000 != f.readInt()) throw new IOException("Not ASAR header (04000000)");
		f.skipBytes(8);
		int jsonLen = Integer.reverseBytes(f.readInt());
		byte[] data = new byte[jsonLen];
		f.readFully(data);
		if (f.read() != 0) {
			throw new IOException("EOF index not found");
		}

		UTFCoder uc = IOUtil.SharedCoder.get();
		CMapping root = JSONParser.parses(uc.decodeR(data)).asMap();

		TrieTree<PosInfo> tree = new TrieTree<>();

		MutableInt dir = new MutableInt();
		CharList tmp = uc.charBuf;
		tmp.clear();
		recursionTree(tmp, root, tree, dir);

		System.out.println("总文件数目: " + tree.size() + ", 总文件大小: " + TextUtil.scaledNumber(f.length() - 16 - jsonLen) + "B, 目录数: " + dir.getValue());

		// 这里大概是要根据第一个或者第二个int计算的
		long baseOffset = f.getFilePointer() + 1;
		File target = new File(args[1]);

		String key;
		if (args.length > 2) {
			System.err.println("我们会使用STDOUT输出文件列表");
			System.err.println("准备好请按任意键");
			CLIUtil.userInput("");
			tree.forEach((k, v) -> System.err.println(k));
			System.err.println("请写出从前缀树的哪个节点开始导出(可以为空)");
			key = CLIUtil.userInput("");
		} else {
			key = "";
		}

		ByteList tmp2 = uc.byteBuf;
		tree.forEachSince(key, (k, v) -> {
			File file = new File(target, k.toString());
			file.getParentFile().mkdirs();
			try (FileOutputStream fos = new FileOutputStream(file)) {
				f.seek(v.offset + baseOffset);
				tmp2.clear();
				tmp2.ensureCapacity(v.length);
				f.readFully(tmp2.list, 0, v.length);
				fos.write(tmp2.list, 0, v.length);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	private static void recursionTree(CharList parents, CMapping directory, TrieTree<PosInfo> tree, MutableInt dir) {
		CMapping files = directory.get("files").asMap();
		int len = parents.length();
		for (Map.Entry<String, CEntry> entry : files.entrySet()) {
			parents.setLength(len);
			parents.append(entry.getKey());

			CMapping val = entry.getValue().asMap();
			if (val.containsKey("files")) {
				dir.increment();
				parents.append('/');

				if (val.containsKey("unpacked")) {
					System.out.println("跳过解包的文件夹 " + parents);
					continue;
				}

				recursionTree(parents, val, tree, dir);
			} else {
				if (val.containsKey("unpacked")) {
					System.out.println("跳过解包的文件 " + parents);
					continue;
				}

				// use string to store long value...
				tree.put(parents.toString(), new PosInfo(val.get("offset").asLong(), val.getInteger("size")));
			}
		}
	}

	static final class PosInfo {
		final long offset;
		final int length;

		PosInfo(long offset, int length) {
			this.offset = offset;
			this.length = length;
		}
	}
}
