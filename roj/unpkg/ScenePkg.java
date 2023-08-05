package roj.unpkg;

import roj.collect.TrieTree;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.CLIUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/12/1 0001 0:44
 */
public class ScenePkg {
	public static void main(String[] args) throws IOException, ParseException {
		if (args.length < 2) {
			System.out.println("ScenePkgExporter <string:scene.pkg> <string:保存目录> [bool:手动过滤内容]");
			return;
		}

		ByteList b = new ByteList().readStreamFully(new FileInputStream(args[0]));
		int len = b.readIntLE();
		String s = b.readUTF(len);
		if (!s.equals("PKGV0018")) throw new RuntimeException("not version 18: "+s);

		TrieTree<PosInfo> tree = new TrieTree<>();

		int count = b.readIntLE();
		int totalLen = 0;
		for (int i = 0; i < count; i++) {
			String name = b.readUTF(b.readIntLE());
			int offset = b.readIntLE();
			len = b.readIntLE();
			totalLen += len;
			tree.put(name, new PosInfo(offset, len));
		}

		System.out.println("总文件数目: "+tree.size()+", 总文件大小: "+TextUtil.scaledNumber1024(totalLen)+"B");

		int baseOffset = b.rIndex;
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

		ByteList tmp = IOUtil.getSharedByteBuf();
		tree.forEachSince(key, (k, v) -> {
			File file = new File(target, k.toString());
			file.getParentFile().mkdirs();
			try (FileOutputStream fos = new FileOutputStream(file)) {
				tmp.ensureCapacity(v.length);
				b.read((int) (v.offset+baseOffset), tmp.list, 0, v.length);
				fos.write(tmp.list, 0, v.length);
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

}
