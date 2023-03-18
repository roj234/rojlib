package roj.asm.util;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.asm.Opcodes;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static roj.asm.Opcodes.IRETURN;
import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2023/1/7 0007 0:30
 */
public class TransformUtil {
	/**
	 * 删除代码，只保留方法自身
	 */
	public static boolean trimCode(ConstantData data) {
		boolean flag = false;
		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			RawMethod ms = (RawMethod) methods.get(i);

			if (ms.attrByName("Code") != null) {
				trimCode(data, ms);
				flag = true;
			}
		}
		return flag;
	}
	public static void trimCode(ConstantData data, MethodNode ms) {
		CodeWriter cw = new CodeWriter();
		cw.init(new ByteList(16), data.cp);

		Type t = ms.returnType();

		cw.visitSize(t.length(), TypeHelper.paramSize(ms.rawDesc()) + ((AccessFlag.STATIC & ms.modifier()) == 0 ? 1 : 0));

		switch (t.type) {
			case CLASS: cw.one(Opcodes.ACONST_NULL); break;
			case VOID: break;
			case BOOLEAN: case BYTE: case CHAR: case SHORT:
			case INT: cw.one(Opcodes.ICONST_0); break;
			case FLOAT: cw.one(Opcodes.FCONST_0); break;
			case DOUBLE: cw.one(Opcodes.DCONST_0); break;
			case LONG: cw.one(Opcodes.LCONST_0); break;
		}
		cw.one(t.shiftedOpcode(IRETURN, true));
		cw.finish();

		ms.putAttr(new AttrUnknown("Code", cw.bw));
	}

	// region Access Transformer
	/**
	 * 修改data中toOpen所包含的访问权限至public
	 */
	public static void makeAccessible(IClass data, Collection<String> toOpen) {
		if (toOpen.contains("<$extend>")) {
			data.modifier(toPublic(data.modifier(), true));
		}

		boolean starP = true;
		if (toOpen.contains("*")) {
			toOpen = null;
			starP = false;
		} else if (toOpen.contains("*P")) {
			toOpen = null;
		}

		toPublic(toOpen, starP, data.fields());
		toPublic(toOpen, starP, data.methods());
	}
	/**
	 * 修改InnerClasses属性中定义的内部类的访问权限
	 */
	public static void makeSubclassAccessible(IClass data, Collection<String> toOpen) {
		List<InnerClasses.InnerClass> classes = AttrHelper.getInnerClasses(data.cp(), data);
		if (classes == null) throw new IllegalStateException("no InnerClass in " + data.name());

		for (int i = 0; i < classes.size(); i++) {
			InnerClasses.InnerClass clz = classes.get(i);
			if (toOpen.contains(clz.self)) {
				clz.flags = (char) toPublic(clz.flags, true);
			}
		}
	}
	private static void toPublic(Collection<String> toOpen, boolean starP, List<? extends MoFNode> nodes) {
		for (int i = 0; i < nodes.size(); i++) {
			MoFNode node = nodes.get(i);
			if (toOpen != null && !toOpen.contains(node.name()) && !toOpen.contains(node.name()+'|'+node.rawDesc())) continue;

			int flag = toPublic(node.modifier(), starP || toOpen != null);
			node.modifier(flag);
		}
	}
	private static int toPublic(int flag, boolean starP) {
		flag &= ~(AccessFlag.PRIVATE | AccessFlag.FINAL);
		if (starP || (flag&AccessFlag.PROTECTED) == 0)
			return (flag & ~AccessFlag.PROTECTED) | AccessFlag.PUBLIC;
		return flag;
	}

	private static final MyHashSet<String>
		class_allow = new MyHashSet<>("InnerClasses", "RuntimeInvisibleAnnotations", "RuntimeVisibleAnnotations", "BootstrapMethods"),
		field_allow = new MyHashSet<>("ConstantValue", "RuntimeInvisibleAnnotations", "RuntimeVisibleAnnotations"),
		method_allow = new MyHashSet<>("Code", "RuntimeInvisibleAnnotations", "RuntimeVisibleAnnotations", "AnnotationDefault");
	// only support java8 at this time
	public static void runOnly(Context ctx) {
		ConstantData data = ctx.getData();
		filter(data, class_allow);

		boolean low = false;
		if (data.version >= 52 << 16 && data.attrByName("BootstrapMethods") == null) {
			data.version = 51 << 16;
			low = true;
		}

		SimpleList<FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			filter(fields.get(i), field_allow);
		}

		SimpleList<MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			filter(mn, method_allow);
			Attribute attr = mn.attrByName("Code");
			if (attr == null) continue;

			AttrCode code = new AttrCode(mn, attr.getRawData(), data.cp);
			mn.putAttr(code);
			System.out.println(mn.attributes());

			if (low) code.frames = null;

			AttributeList list = code.attributesNullable();
			if (list != null) list.clear();

		}
	}
	private static void filter(Attributed a, MyHashSet<String> permitted) {
		AttributeList list = a.attributesNullable();
		if (list == null) return;
		for (int i = list.size() - 1; i >= 0; i--) {
			if (!permitted.contains(list.get(i).name())) list.remove(i);
		}
	}
	// endregion
	// region Test
	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("Transformers <jar...>");
			System.out.println("  用途：依代码而定");
			return;
		}

		for (int i = 0; i < args.length; i++) {
			try (ZipArchive mzf = new ZipArchive(new File(args[0]))) {
				for (ZEntry entry : mzf.getEntries().values()) {
					if (entry.getName().endsWith(".class")) {
						Context ctx = new Context(entry.getName(), mzf.getStream(entry));
						if (transform(ctx)) {
							mzf.put(entry.getName(), ctx::getCompressedShared, true);
						}
					}
				}
				mzf.store();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	private static boolean transform(Context ctx) {
		// Fill you code here
		//
		return false;
	}
	// endregion
}
