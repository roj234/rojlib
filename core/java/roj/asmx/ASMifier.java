package roj.asmx;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.asm.insn.AbstractCodeWriter;
import roj.asm.insn.CodeVisitor;
import roj.collect.ArrayList;
import roj.collect.IntMap;
import roj.text.CharList;
import roj.text.Tokenizer;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2025/09/14 12:24
 */
public class ASMifier {
	public static CharList asmify(ClassNode node, CharList sb) {
		sb.append("ClassNode node = new ClassNode();\nConstantPool cp = node.cp;\n\n");

		sb.append("node.name(").append(str(node.name())).append(");\n");
		sb.append("node.parent(").append(str(node.parent())).append(");\n");
		for (String itf : node.interfaces()) {
			sb.append("node.addInterface(").append(str(itf)).append(");\n");
		}
		sb.append("\nint fid;\n");

		for (FieldNode field : node.fields) {
			sb.append("fid = node.newField(");
			showModifiers(field.modifier, ACC_TOSTRING[1], sb);
			sb.append(", ").append(str(field.name())).append(", ").append(str(field.rawDesc())).append(");\n");

		}

		sb.append("\nCodeWriter cw;\n");
		for (MethodNode method : node.methods) {
			sb.append("{\n");
			sb.append("cw = node.newMethod(");
			showModifiers(method.modifier, ACC_TOSTRING[2], sb);
			sb.append(", ").append(str(method.name())).append(", ").append(str(method.rawDesc())).append(");\n");

			method.transform(node.cp, new ByteList(), new CodeSerializer(sb));

			sb.append("}\n\n");
		}

		return sb;
	}

	private static final String[][] ACC_TOSTRING = new String[][] {
			{ "public", null, null, null, "final", "super", null, null, null, "interface", "abstract", null, "synthetic", "annotation", "enum", "module" },
			{ "public", "private", "protected", "static", "final", null, "volatile", "transient", null, null, null, null, "synthetic", null, "enum" },
			{ "public", "private", "protected", "static", "final", "synchronized", "bridge", "varargs", "native", null, "abstract", "strictfp", "synthetic" },
			{ "public", "private", "protected", "static", "final", null, null, null, null, "interface", "abstract", null, "synthetic", "annotation", "enum" }
	};

	public static CharList showModifiers(int modifier, String[] names, CharList sb) {
		if (modifier != 0) {
			for (int i = 0; i < names.length; i++) {
				if ((modifier & (1 << i)) != 0) {
					sb.append("ACC_").append(names[i].toUpperCase()).append(" | ");
				}
			}
			sb.setLength(sb.length() - 3);
		}
		return sb;
	}

	private static String str(String str) {
		return str == null ? "null" : "\""+Tokenizer.escape(str)+"\"";
	}

	private static final class CodeSerializer extends CodeVisitor {
		CharList start;
		CharList line = new CharList();
		IntMap<CharList> instructions = new IntMap<>();
		IntMap<Consumer<CharList>> labels = new IntMap<>();

		public CodeSerializer(CharList sb) {
			this.start = sb;
		}

		protected void visitPreInsn() {
			instructions.put(bci, line);
			line = new CharList();
		}

		private static @NotNull String op(byte code) {
			return Opcodes.toString(code).toUpperCase(Locale.ROOT);
		}
		private static String utfRef(CstRefUTF clz) {
			return "\""+Tokenizer.escape( clz.value().str())+"\"";
		}
		private static String ref(CstRef clz) {
			return "\""+Tokenizer.escape(clz.owner())+"\", \""+Tokenizer.escape(clz.name())+"\", \""+Tokenizer.escape(clz.rawDesc())+"\"";
		}
		private String label(int bci) {
			if (labels.containsKey(bci)) return "label"+bci;
			start.append("var label").append(bci).append(" = new Label();\n");
			labels.put(bci, sb -> sb.insert(0, "cw.label(label"+bci+");\n"));
			return "label"+bci;
		}

		protected void visitSize(int stackSize, int localSize) {line.append("cw.visitSize(").append(stackSize).append(", ").append(localSize).append(");\n");}
		protected void newArray(byte type) {line.append("cw.newArray(ToPrimitiveArrayId('").append(AbstractCodeWriter.FromPrimitiveArrayId(type)).append("'));\n");}
		protected void multiArray(CstClass clz, int dimension) {line.append("cw.multiArray(").append(utfRef(clz)).append(", ").append(dimension).append(");\n");}

		protected void clazz(byte code, CstClass clz) {line.append("cw.clazz(").append(op(code)).append(", ").append(utfRef(clz)).append(");\n");}
		protected void iinc(int id, int count) {line.append("cw.iinc(").append(id).append(", ").append(count).append(");\n");}
		protected void ldc(byte code, Constant c) {
			switch (c.type()) {
				case Constant.STRING, Constant.FLOAT, Constant.INT, Constant.CLASS, Constant.DOUBLE, Constant.LONG -> {
					line.append("cw.ldc(").append(c.toString()).append(");\n");
				}
				case Constant.DYNAMIC -> {
					var dyn = (CstDynamic) c;
					line.append("var cst").append(bci).append(" = cp.getLoadDyn(").append(dyn.tableIdx)
							.append(", ").append(str(dyn.desc().name().str()))
							.append(", ").append(str(dyn.desc().rawDesc().str())).append(");\n");
					line.append("cw.ldc(cst").append(bci).append(");\n");
				}
				case Constant.METHOD_HANDLE -> {
					var mh = (CstMethodHandle) c;
					line.append("cw.ldc(new CstMethodHandle(").append(mh.kind).append(", ").append("cp.getRefByType(").append(ref(mh.getTarget())).append(", ").append(mh.getTarget().type()).append(")));\n");
				}
				case Constant.METHOD_TYPE -> {
					var methodType = (CstMethodType) c;
					line.append("cw.ldc(new CstMethodType(").append(utfRef(methodType)).append(")));\n");
				}
			}
		}
		protected void invokeDyn(CstDynamic dyn, int reserved) {
			line.append("cw.invokeDyn(").append((int)dyn.tableIdx)
					.append(", ").append(str(dyn.desc().name().str()))
					.append(", ").append(str(dyn.desc().rawDesc().str())).append(");\n");
		}
		protected void invokeItf(CstRef method, short argc) {line.append("cw.invokeItf(").append(ref(method)).append(");\n");}
		protected void invoke(byte code, CstRef method) {line.append("cw.invoke(").append(op(code)).append(", ").append(ref(method)).append(");\n");}
		protected void field(byte code, CstRef field) {line.append("cw.field(").append(op(code)).append(", ").append(ref(field)).append(");\n");}
		protected void jump(byte code, int offset) {line.append("cw.jump(").append(op(code)).append(", ").append(label(bci+offset)).append(");\n");}
		protected void insn(byte code) {line.append("cw.insn(").append(op(code)).append(");\n");}
		protected void smallNum(byte code, int value) {line.append("cw.smallNum(").append(op(code)).append(", ").append(value).append(");\n");}
		protected void vars(byte code, int value) {line.append("cw.vars(").append(op(code)).append(", ").append(value).append(");\n");}
		protected void ret(int value) {line.append("cw.ret(").append(value).append(");\n");}
		protected final void tableSwitch(DynByteBuf r) {
			int def = r.readInt();
			int low = r.readInt();
			int hig = r.readInt();
			int count = hig - low + 1;

			var labelId = bci;
			line.append("var seg").append(labelId).append(" = SwitchBlock.ofSwitch(TABLESWITCH);\n");
			line.append("seg").append(labelId).append(".def = ").append(label(bci+def)).append(");\n");

			int i = 0;
			while (count > i) {
				line.append("seg").append(labelId).append(".branch(").append(i++ + low).append(", ").append(label(bci+r.readInt())).append(");\n");
			}
		}
		protected final void lookupSwitch(DynByteBuf r) {
			int def = r.readInt();
			int count = r.readInt();

			var labelId = bci;
			line.append("var seg").append(labelId).append(" = SwitchBlock.ofSwitch(LOOKUPSWITCH);\n");
			line.append("seg").append(labelId).append(".def = ").append(label(bci+def)).append(");\n");

			while (count-- > 0) {
				line.append("seg").append(labelId).append(".branch(").append(r.readInt()).append(", ").append(label(bci+r.readInt())).append(");\n");
			}
		}

		public void visitExceptions() {line.append("cw.visitExceptions();\n");}
		protected void visitException(int start, int end, int handler, CstClass type) {
			line.append("cw.visitException(").append(label(start)).append(", ").append(label(end)).append(", ").append(label(handler)).append(", ").append(utfRef(type)).append(");\n");
		}

		public void visitAttributes() {line.append("cw.visitAttributes();\n");}
		protected void visitAttribute(ConstantPool cp, String name, int len, DynByteBuf data) {
			// not implemented
			line.append("//cw.visitAttribute(cp, ").append(name).append(", ").append(len).append(");\n");
		}

		public void visitEnd() {
			line.append("cw.visitEnd();\n");

			for (var entry : labels.selfEntrySet())
				entry.getValue().accept(instructions.get(entry.getIntKey()));

			var entries = new ArrayList<>(instructions.selfEntrySet());
			entries.sort((o1, o2) -> Integer.compare(o1.getIntKey(), o2.getIntKey()));

			for (IntMap.Entry<CharList> entry : entries) {
				start.append(entry.getValue());
			}
			start.append(line);
		}
	}
}
