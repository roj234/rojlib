package roj.asm.insn;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.AsmCache;
import roj.asm.Attributed;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.*;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.frame.Frame;
import roj.asm.frame.FrameVisitor;
import roj.collect.ArrayList;
import roj.collect.IntMap;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.TextUtil;
import roj.text.logging.Logger;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/10/1 00:21
 */
public class Code extends Attribute implements Attributed {
	public static final int ATTR_CODE = 2;
	public static final byte COMPUTE_FRAMES = 1, COMPUTE_SIZES = 2;

	public InsnList instructions = new InsnList();
	public char stackSize, localSize;

	private final MethodNode method;
	private byte fvFlags;

	public List<Frame> frames;

	public ArrayList<TryCatchEntry> tryCatch;

	private AttributeList attributes;

	public Code(@NotNull MethodNode mn) {method = mn;}
	public Code(DynByteBuf r, ConstantPool cp, MethodNode mn) {
		stackSize = r.readChar();
		localSize = r.readChar();
		method = mn;

		int codeLength = r.readInt();
		if (codeLength < 1 || codeLength > r.readableBytes()) {
			throw new IllegalStateException("Wrong code length: "+codeLength+", readable=" + r.readableBytes());
		}

		int codePos = r.rIndex;
		r.rIndex += codeLength;
		instructions.bciR2W = AsmCache.getInstance().getBciMap();

		int len = r.readUnsignedShort();
		if (len > 0) {
			ArrayList<TryCatchEntry> ex = tryCatch = new ArrayList<>(len);
			for (int i = 0; i < len; i++) {
				Label start = instructions._monitor(r.readUnsignedShort());
				int bci = r.readUnsignedShort();
				Label end = bci == codeLength ? null : instructions._monitor(bci);
				Label handler = instructions._monitor(r.readUnsignedShort());
				ex.add(new TryCatchEntry(start, end, handler, cp.getRefName(r)));
			}
		}

		len = r.readUnsignedShort();
		if (len > 0) {
			attributes = new AttributeList(len);
			while (len-- > 0) {
				String name = ((CstUTF) cp.get(r)).str();
				int exw = r.wIndex();
				int br;
				r.wIndex(r.readInt()+(br=r.rIndex));
				try {
					switch (name) {
						case "LineNumberTable" -> attributes._add(new LineNumberTable(instructions, r));
						case "LocalVariableTable", "LocalVariableTypeTable" -> attributes._add(new LocalVariableTable(name, instructions, cp, r, codeLength));
						case "StackMapTable" -> FrameVisitor.readFrames(frames = new ArrayList<>(), r, cp, instructions, mn.owner(), localSize, stackSize);
						case "RuntimeInvisibleTypeAnnotations", "RuntimeVisibleTypeAnnotations" -> attributes._add(new TypeAnnotations(name, r, cp));
						default -> {
							Logger.FALLBACK.debug("{}.{} 中遇到不支持的属性 {}", mn.owner(), mn.name(), name);
							r.rIndex = r.wIndex();
							continue;
						}
					}

					if (r.isReadable()) {
						Logger.FALLBACK.warn("无法读取"+mn.owner()+"."+mn.name()+"的'Code'的子属性'"+name+"' ,剩余了"+r.readableBytes()+",数据:"+r.dump());
						r.rIndex = r.wIndex();
					}
				} catch (Throwable e) {
					r.rIndex = br;
					throw new RuntimeException("读取子属性'"+name+"',数据:"+r.dump(), e);
				} finally {
					r.wIndex(exw);
				}
			}
		}

		int myLen = r.rIndex;
		r.rIndex = codePos;
		instructions.visitBytecode(cp, r, codeLength);
		r.rIndex = myLen;
	}

	@Override
	public String name() { return "Code"; }

	public void computeFrames(@MagicConstant(flags = {COMPUTE_SIZES, COMPUTE_FRAMES}) int flag) {this.fvFlags = (byte) flag;}

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool cp) {
		var c = AsmCache.getInstance().cw();
		c.init(w, cp, method);
		c.computeFrames(fvFlags);

		c.visitSize(stackSize, localSize);
		instructions.write(c);

		c.visitExceptions();
		ArrayList<TryCatchEntry> exs = tryCatch;
		if (exs != null) {
			for (int i = 0; i < exs.size(); i++) {
				TryCatchEntry ex = exs.get(i);
				c.visitException(ex.start, ex.end, ex.handler, ex.type);
			}
		}

		c.visitAttributes();

		if (fvFlags != 0) {
			FrameVisitor fv = c.getFv();

			if ((fvFlags & COMPUTE_SIZES) != 0) {
				stackSize = (char) fv.maxStackSize;
				localSize = (char) fv.maxLocalSize;
			}

			if ((fvFlags & COMPUTE_FRAMES) != 0) {
				frames = c.frames;
			}
		} else {
			s:
			if (frames != null) {
				int stack = c.visitAttributeI("StackMapTable");
				if (stack < 0) break s;
				FrameVisitor.writeFrames(frames, w, cp);
				c.visitAttributeIEnd(stack);
			}
		}

		AttributeList attrs = attributes;
		if (attrs != null) {
			for (int i = 0; i < attrs.size(); i++) {
				Attribute attr = attrs.get(i);
				if (attr.writeIgnore()) continue;
				c.visitAttribute(attr);
			}
		}

		c.visitEnd();
		c.bw = null;
		c.cpw = null;
		AsmCache.getInstance().cw(c);
	}

	public String toString() { return toString(IOUtil.getSharedCharBuf().append("代码"), 4).toString(); }
	public CharList toString(CharList sb, int prefix) {
		sb.padEnd(' ', prefix).append("stack=").append((int)stackSize).append(", local=").append((int)localSize).append('\n');

		LineNumberTable lines = getLines();
		ArrayList<Object> a = ArrayList.asModifiableList("PC","指令","参数",IntMap.UNDEFINED);
		if (lines != null) a.add(0, "行");

		CharList sb2 = new CharList();
		for (InsnNode node : instructions) {
			if (lines != null) {
				int line = lines.searchLine(node.bci());
				if (line < 0) a.add("");
				else a.add(line);
			}

			a.add(node.bci());
			a.add(Opcodes.toString(node.opcode()));
			if ((Opcodes.flag(node.opcode())&16) == 0) {
				sb2.clear();
				try {
					String string = node.myToString(sb2, true).toString();
					if (string.length() > 255) {
						List<String> lines1 = LineReader.create(string).lines();
						for (int i = 0; i < lines1.size(); i++) {
							String s = lines1.get(i);
							if (s.length() > 255) lines1.set(i, s.substring(0, 250).concat("<...>"));
						}
						string = TextUtil.join(lines1, "\n");
					}
					a.add(string);
				} catch (IllegalStateException e) {
					a.add("<参数错误>");
				} catch (Exception e) {
					a.add("<"+e.getClass().getSimpleName()+">: "+e.getMessage());
				}
			} else {
				a.add("");
			}
			a.add(IntMap.UNDEFINED);
		}
		sb2.clear();
		TextUtil.prettyTable(sb, sb2.padEnd(' ', prefix).toString(), a.toArray(), "  ", "    ");

		if (tryCatch != null) {
			sb.append('\n').padEnd(' ', prefix).append("异常处理程序");

			a.clear(); a.addAll("从","至","处理程序","异常类型",IntMap.UNDEFINED);
			for (TryCatchEntry ex : tryCatch) {
				a.add(ex.start.getValue());
				a.add(ex.end.getValue());
				a.add(ex.handler.getValue());
				a.add(ex.type==null||ex.type.equals("java/lang/Throwable")?"<任意>":ex.type);
				a.add(IntMap.UNDEFINED);
			}
			sb2.clear();
			TextUtil.prettyTable(sb, sb2.padEnd(' ', prefix).toString(), a.toArray(), "  ", "  ");
		}

		LocalVariableTable lvt = getLVT();
		if (lvt != null) lvt.toString(sb.append('\n').padEnd(' ', prefix).append("变量: "), getLVTT(), prefix+4);

		if (frames != null) {
			sb.append('\n').padEnd(' ', prefix).append("堆栈状态:\n");
			for (int i = 0; i < frames.size(); i++)
				frames.get(i).toString(sb, prefix).append('\n');
		}
		return sb;
	}

	//WIP
	public CharList simpleDecompiler(CharList sb, int prefix) {
		sb.padEnd(' ', prefix).append(".stack ").append((int)stackSize).append('\n')
		  .padEnd(' ', prefix).append(".local ").append((int)localSize).append('\n');

		IntMap<String> labels = new IntMap<>();
		if (tryCatch != null) {
			for (int i = 0; i < tryCatch.size(); i++) {
				TryCatchEntry ex = tryCatch.get(i);
				labels.putIfAbsent(ex.start.getValue(), "exc_"+i+"_start");
				labels.putIfAbsent(ex.end.getValue(), "exc_"+i+"_end");
				labels.putIfAbsent(ex.handler.getValue(), "exc_"+i+"_handler");
			}
		}

		for (InsnNode node : instructions) {
			if (Opcodes.toString(node.opcode()).endsWith("Switch")) {
				SwitchBlock block = node.switchTargets();
				labels.putIfAbsent(block.def.getValue(), "switch_"+node.bci()+"_def");
				List<SwitchTarget> targets = block.targets;
				for (int i = 0; i < targets.size(); i++) {
					SwitchTarget target = targets.get(i);
					labels.putIfAbsent(target.target.getValue(), "switch_"+node.bci()+"_target_"+target.value);
				}
			} else {
				Label target = node.targetOrNull();
				if (target != null) labels.putIfAbsent(target.getValue(), "label"+target.getValue());
			}
		}

		LineNumberTable lines = getLines();

		for (var node : instructions) {
			if (lines != null) {
				int line = lines.searchLine(node.bci());
				if (line > 0) sb.padEnd(' ', prefix).append(".line ").append(line).append('\n');
			}

			String lbl = labels.get(node.bci());
			if (lbl != null) sb.padEnd(' ', prefix).append(lbl).append(':').append('\n');

			sb.padEnd(' ', prefix).append(Opcodes.toString(node.opcode()));
			if ((Opcodes.flag(node.opcode())&16) == 0) {
				sb.append(' ');
				node.myToString(sb, false);
			}
			sb.append('\n');
		}

		if (tryCatch != null) {
			sb.append('\n').padEnd(' ', prefix).append(".exception\n");

			for (int i = 0; i < tryCatch.size(); i++) {
				TryCatchEntry ex = tryCatch.get(i);
				sb.padEnd(' ', prefix+4).append("exc_").append(i).append("_start exc_").append(i).append("_end => exc_").append(i).append("_handler").append('\n')
				  .padEnd(' ', prefix+4).append(ex.type == null ? "*" : ex.type).append('\n');
			}
		}

		return sb;
	}

	public LocalVariableTable getLVT() { return (LocalVariableTable) getAttribute("LocalVariableTable"); }
	public LocalVariableTable getLVTT() { return (LocalVariableTable) getAttribute("LocalVariableTypeTable"); }
	public LineNumberTable getLines() { return (LineNumberTable) getAttribute("LineNumberTable"); }

	public AttributeList attributes() { return attributes == null ? attributes = new AttributeList() : attributes; }
	@Nullable public AttributeList attributesNullable() { return attributes; }

	public char modifier() { throw new UnsupportedOperationException(); }
}