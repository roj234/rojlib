package roj.asm;

import roj.io.IOUtil;

import java.io.File;

/**
 * 字节码解析器
 * @author Roj234
 * @version 3.1
 * @since 2021/5/29 17:16
 */
public final class Javap {
	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.out.println("Parser <类名或文件名>\n通过RojASM输出ASM节点信息");
			return;
		}

		var classOrFile = new File(args[0]);
		if (classOrFile.isFile()) {
			System.out.println(ClassNode.parseAll(IOUtil.read(classOrFile)));
		} else {
			ClassNode node = ClassNode.fromType(Class.forName(args[0]));
			if (node == null) System.out.println("找不到"+args[0]+"的类文件");
			else System.out.println(node);
		}
	}
}