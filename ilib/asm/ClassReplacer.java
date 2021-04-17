package ilib.asm;

import ilib.Config;
import roj.asm.Parser;
import roj.asm.cst.Constant;
import roj.asm.cst.CstUTF;
import roj.asm.tree.ConstantData;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.text.CharList;

import net.minecraft.launchwrapper.IClassTransformer;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public class ClassReplacer implements IClassTransformer {
	public static final ClassReplacer FIRST = new ClassReplacer();
	public static final ClassReplacer LAST = new ClassReplacer();
	private final MyHashMap<String, byte[]> list = new MyHashMap<>();

	@Override
	public byte[] transform(String name, String trName, byte[] code) {
		byte[] replace = list.remove(trName);
		if (replace == null) return code;

		if ((Config.debug & 2) != 0) {
			Loader.logger.info("CL替换 " + trName + "(" + name + ')');
		}

		return replace;
	}

	public void add(String name, String father, byte[] arr, boolean replace) {
		if (Config.controlViaFile) {
			if (!Config.instance.getConfig().getOrCreateMap("每文件控制.ClassReplacer").putIfAbsent(name, true)) return;
		}

		ConstantData data = Parser.parseConstants(arr);
		String target = name.replace('.', '/');

		if (replace) {
			List<Constant> cp = data.cp.array();
			CharList tmp1 = IOUtil.getSharedCharBuf();
			for (int i = 0; i < cp.size(); i++) {
				Constant c = cp.get(i);
				if (c.type() == Constant.UTF) {
					CstUTF cu = (CstUTF) c;
					tmp1.append(cu.str()).replace(data.name, target);
					data.cp.setUTFValue(cu, tmp1.toString());
					tmp1.clear();
				}
			}
		}

		if (father != null) {
			data.parent(father);
		}

		data.name(target);

		list.put(name.replace('/', '.'), Parser.toByteArray(data));
	}

	public void addDeep(String name, byte[] arr) {
		add(name, null, arr, true);
	}

	public void add(String name, byte[] arr, String father) {
		add(name, father, arr, false);
	}

	public void add(String name, byte[] arr) {
		add(name, null, arr, false);
	}
}