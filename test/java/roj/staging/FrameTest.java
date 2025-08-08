package roj.staging;

import roj.asm.MethodNode;
import roj.asm.attr.Attribute;
import roj.asm.insn.Code;
import roj.asmx.Context;
import roj.config.ParseException;
import roj.util.FastFailException;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;

import java.io.IOException;
import java.util.Iterator;

/**
 * @author Roj234
 * @since 2025/3/21 2:36
 */
public class FrameTest {
	public static void main(String[] args) throws IOException, ParseException {
		for (Context context : Context.fromZip(IOUtil.getJar(FrameTest.class), null)) {
			for (MethodNode method : context.getData().methods()) {
				method.getAttribute(context.getConstantPool(), Attribute.Code).computeFrames(Code.COMPUTE_SIZES | Code.COMPUTE_FRAMES);
			}

			String className = context.getClassName();
			// TODO uninitialized_this
			//  misc: MCDiff 用一个7z压缩所有修改的文件，索引放在最后【[name, size, lastModified, hash], message, parents】
			//  更新时删除原先的index，只保留发生变动之前的文件，并计算hash，放进新索引的parents项目，这样还能支持多个parent，也就是merge了
			//  filesync mynotes tinyjvm simplechat

			//		roj/compiler/plugins/moreop/MapGet.copyValue(Lroj/compiler/asm/MethodWriter;Z)I @20: invokevirtual
			//     roj/collect/RSegmentTree.<init>(IZI)V @15: iload_1
			if (args.length == 0 || className.endsWith(args[0])) {
				context.getData().parsed();
				if (args.length > 1) {
					for (Iterator<MethodNode> itr = context.getData().methods.iterator(); itr.hasNext(); ) {
						MethodNode method = itr.next();
						if (!method.name().equals(args[1])) {
							itr.remove();
						}
					}
					System.out.println(context.getData());
				}

				try {
					System.out.println(ClassDefiner.defineClass(FrameTest.class.getClassLoader(), context.getData()).getDeclaredMethods());
				} catch (VerifyError e) {
					synchronized (FrameTest.class) {
						System.out.println(context.getClassName()+" failed");
						e.printStackTrace();
						System.out.println("===");
					}
				} catch (LinkageError | SecurityException | FastFailException e) {
					//e.printStackTrace();
				} catch (Exception e) {
					synchronized (FrameTest.class) {
						System.out.println(context.getClassName()+" failed");
						e.printStackTrace();
						System.out.println("===");
					}
				}

			}
		}
		System.out.println("pass");
	}
}
