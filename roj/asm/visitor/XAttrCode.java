package roj.asm.visitor;

import roj.asm.AsmShared;
import roj.asm.OpcodeUtil;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.tree.Attributed;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.LineNumberTable;
import roj.asm.tree.attr.LocalVariableTable;
import roj.asm.tree.attr.TypeAnnotations;
import roj.asm.util.AttributeList;
import roj.asm.util.TryCatchEntry;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/10/1 00:21
 */
public class XAttrCode extends Attribute implements Attributed {
	public static final byte COMPUTE_FRAMES = 1, COMPUTE_SIZES = 2;

	public XInsnList instructions = new XInsnList();
	public char stackSize, localSize;

	private MethodNode owner;
	private byte frameFlag;

	public List<Frame2> frames;

	public SimpleList<TryCatchEntry> tryCatch;

	private AttributeList attributes;

	public XAttrCode() {}
	public XAttrCode(DynByteBuf r, ConstantPool cp, MethodNode mn) {
		stackSize = r.readChar();
		localSize = r.readChar();

		int codeLength = r.readInt();
		if (codeLength < 1 || codeLength > r.readableBytes()) {
			throw new IllegalStateException("Wrong code length: "+codeLength+", readable=" + r.readableBytes());
		}

		int codePos = r.rIndex;
		r.rIndex += codeLength;
		instructions.bciR2W = AsmShared.local().getBciMap();

		int len = r.readUnsignedShort();
		if (len > 0) {
			SimpleList<TryCatchEntry> ex = tryCatch = new SimpleList<>(len);
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
						case "LineNumberTable":
							attributes.i_direct_add(new LineNumberTable(instructions, r));
						break;
						case "LocalVariableTable": case "LocalVariableTypeTable":
							attributes.i_direct_add(new LocalVariableTable(name, instructions, cp, r, codeLength));
						break;
						case "StackMapTable":
							FrameVisitor.readFrames(frames = new SimpleList<>(), r, cp, instructions, mn.ownerClass(), localSize, stackSize);
						break;
						case "RuntimeInvisibleTypeAnnotations": case "RuntimeVisibleTypeAnnotations":
							attributes.i_direct_add(new TypeAnnotations(name, r, cp));
						break;
						default: System.err.println("[R.A.AC]Skip unknown " + name + " for " + (mn.ownerClass() + '.' + mn.name()));
					}

					if (r.isReadable()) {
						System.err.println("无法读取"+mn.ownerClass()+"."+mn.name()+"的'Code'的子属性'"+name+"' ,剩余了"+r.readableBytes()+",数据:"+r.dump());
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

	public void recomputeFrames(int flag, MethodNode owner) {
		this.frameFlag = (byte) flag;
		if (flag != 0 && owner == null) throw new IllegalArgumentException();
		this.owner = owner;
	}
	public byte getFrameFlag() { return frameFlag; }

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool cp) {
		CodeWriter c = AsmShared.local().cw();
		c.init(w, cp, owner, frameFlag);

		c.visitSize(stackSize, localSize);
		instructions.write(c);

		c.visitExceptions();
		SimpleList<TryCatchEntry> exs = tryCatch;
		if (exs != null) {
			for (int i = 0; i < exs.size(); i++) {
				TryCatchEntry ex = exs.get(i);
				c.visitException(ex.start, ex.end, ex.handler, ex.type);
			}
		}

		c.visitAttributes();

		if (CodeWriter.ENABLE_FV && frameFlag != 0) {
			FrameVisitor fv = c.getFv();

			if ((frameFlag & COMPUTE_SIZES) != 0) {
				stackSize = (char) fv.maxStackSize;
				localSize = (char) fv.maxLocalSize;
			}

			if ((frameFlag & COMPUTE_FRAMES) != 0) {
				frames = c.frames;
			}
		} else {
			s:
			if (frames != null) {
				int stack = c.visitAttributeI("StackMapTable");
				if (stack < 0) break s;
				FrameVisitor.writeFrames(frames, w.putShort(frames.size()), cp);
				c.visitAttributeIEnd(stack);
			}
		}

		AttributeList attrs = attributes;
		if (attrs != null) {
			for (int i = 0; i < attrs.size(); i++) {
				Attribute attr = attrs.get(i);
				if (attr.isEmpty()) continue;
				c.visitAttribute(attr);
			}
		}

		c.visitEnd();
	}

	public String toString() {
		CharList sb = new CharList().append("代码");

		LineNumberTable lines = getLines();
		SimpleList<Object> a = SimpleList.asModifiableList("BCI","操作码","参数","Label",IntMap.UNDEFINED);
		if (lines != null) a.add(0, "行号");

		CharList sb2 = new CharList();
		for (XInsnNodeView node : instructions) {
			if (lines != null) {
				int line = lines.searchLine(node.bci());
				if (line < 0) a.add("");
				else a.add(line);
			}

			a.add(node.bci());
			a.add(OpcodeUtil.toString0(node.opcode()));
			if ((OpcodeUtil.flag(node.opcode())&16) == 0) {
				sb2.clear();
				try {
					String string = node.myToString(sb2, true).toString();
					if (string.length() > 255) string = string.substring(0, 240).concat("<字符过长...>");
					a.add(string);
				} catch (IllegalStateException e) {
					a.add("<参数错误>");
				} catch (Exception e) {
					a.add("<"+e.getClass().getSimpleName()+">: "+e.getMessage());
				}
			} else {
				a.add("");
			}
			a.add("b"+node.pos().getBlock()+"+"+node.pos().getOffset());
			a.add(IntMap.UNDEFINED);
		}
		TextUtil.prettyTable(sb, "    ", a.toArray(), "  ", "    ");

		if (tryCatch != null && !tryCatch.isEmpty()) {
			sb.append("  异常处理程序");

			a.clear(); a.addAll("从","至","处理程序","异常",IntMap.UNDEFINED);
			for (TryCatchEntry ex : tryCatch) {
				a.add(ex.start.getValue());
				a.add(ex.end.getValue());
				a.add(ex.handler.getValue());
				a.add(ex.type==null||ex.type.equals("java/lang/Throwable")?"<任意>":ex.type);
				a.add(IntMap.UNDEFINED);
			}
			TextUtil.prettyTable(sb, "    ", a.toArray(), "  ", "  ");
		}

		LocalVariableTable lvt = getLVT();
		if (lvt != null) sb.append("  变量: ").append(lvt.toString(getLVTT()));

		if (frames != null) {
			sb.append("  堆栈映射:\n");
			for (int i = 0; i < frames.size(); i++) {
				sb.append(frames.get(i));
			}
		}
		return sb.toString();
	}

	public LocalVariableTable getLVT() { return (LocalVariableTable) attrByName("LocalVariableTable"); }
	public LocalVariableTable getLVTT() { return (LocalVariableTable) attrByName("LocalVariableTypeTable"); }
	public LineNumberTable getLines() { return (LineNumberTable) attrByName("LineNumberTable"); }

	public Attribute attrByName(String name) { return attributes == null ? null : (Attribute) attributes.getByName(name); }
	public AttributeList attributes() { return attributes == null ? attributes = new AttributeList() : attributes; }
	@Nullable
	public AttributeList attributesNullable() { return attributes; }

	public char modifier() { throw new UnsupportedOperationException(); }
}