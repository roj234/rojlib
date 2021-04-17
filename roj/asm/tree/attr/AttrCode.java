package roj.asm.tree.attr;

import roj.asm.AsmShared;
import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cst.*;
import roj.asm.tree.Attributed;
import roj.asm.tree.MethodNode;
import roj.asm.tree.insn.*;
import roj.asm.util.AttributeList;
import roj.asm.util.ExceptionEntry;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Frame2;
import roj.asm.visitor.FrameVisitor;
import roj.asm.visitor.Label;
import roj.collect.IntBiMap;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @version 2.0
 * @since 2021/6/18 9:51
 */
public class AttrCode extends Attribute implements Attributed {
	public static final byte COMPUTE_FRAMES = 1, COMPUTE_SIZES = 2;
	@Nonnull
	private final MethodNode owner;

	public final InsnList instructions = new InsnList();

	public char stackSize, localSize;

	public byte interpretFlags;
	public List<Frame2> frames;
	public SimpleList<ExceptionEntry> exceptions;

	private AttributeList attributes;

	public AttrCode(MethodNode method) {
		super("Code");
		this.owner = method;
		// noinspection all
		method.getClass();
	}

	public AttrCode(MethodNode owner, DynByteBuf r, ConstantPool cp) {
		this(owner);
		this.stackSize = r.readChar();
		this.localSize = r.readChar();

		int largestIndex = r.readInt();
		if (largestIndex < 1 || largestIndex > r.readableBytes()) {
			throw new IllegalStateException("Wrong code length: " + largestIndex + " of length at " + owner.ownerClass() + "." + owner.name() + " remaining " + r.readableBytes());
		}
		IntMap<InsnNode> pc = parseCode(cp, r, largestIndex);

		int len = r.readUnsignedShort();
		if (len > 0) {
			SimpleList<ExceptionEntry> ex = exceptions = new SimpleList<>(len);
			for (int i = 0; i < len; i++) {
				int pci;
				ex.add(new ExceptionEntry(pc.get(r.readUnsignedShort()), // start
										  (pci = r.readUnsignedShort()) == largestIndex ? null : pc.get(pci), // end
										  pc.get(r.readUnsignedShort()), // handler
										  (CstClass) cp.get(r)));      // type
			}
		}

		len = r.readUnsignedShort();
		// attributes

		if (len == 0) return;

		attributes = new AttributeList(len);
		while (len-- > 0) {
			String name = ((CstUTF) cp.get(r)).str();
			int exw = r.wIndex();
			int br;
			r.wIndex(r.readInt()+(br=r.rIndex));
			try {
				switch (name) {
					case "LineNumberTable":
						attributes.i_direct_add(new AttrLineNumber(r, pc));
						break;
					case "LocalVariableTable": case "LocalVariableTypeTable":
						attributes.i_direct_add(new AttrLocalVars(name, cp, r, pc, largestIndex));
						break;
					case "StackMapTable":
						// 不会有人大改代码还直接在原SMT上改的吧，不会的吧
						// 不管怎么样，我把这里的验证删了，这样至少在As-Is模式下不需要读取外部类(Var2.merge)
						FrameVisitor.readFrames(frames = new SimpleList<>(), r, cp, pc, owner, localSize, stackSize);
						break;
					case "RuntimeInvisibleTypeAnnotations": case "RuntimeVisibleTypeAnnotations":
						attributes.i_direct_add(new TypeAnnotations(name, r, cp));
						break;
					default: System.err.println("[R.A.AC]Skip unknown " + name + " for " + (owner.ownerClass() + '.' + owner.name()));
				}

				if (r.isReadable()) {
					System.err.println("无法读取"+owner.ownerClass()+"."+owner.name()+"的'Code'的子属性'"+name+"' ,剩余了"+r.readableBytes()+",数据:"+r.dump());
				}
			} catch (Throwable e) {
				r.rIndex = br;
				throw new RuntimeException("读取子属性'"+name+"',数据:"+r.dump(), e);
			} finally {
				r.wIndex(exw);
			}
		}
	}

	@Override
	protected void toByteArray1(DynByteBuf w, ConstantPool cw) {
		CodeWriter c = AsmShared.local().cw();
		c.init(w, cw, owner, interpretFlags);

		c.visitSize(stackSize, localSize);

		InsnList insn = instructions;
		Map<InsnNode, Label> labels = AsmShared.local()._Map(insn.size());
		for (int i = 0; i < insn.size(); i++) {
			insn.get(i).preSerialize(labels);
		}

		SimpleList<ExceptionEntry> exs = exceptions;
		if (exs != null) {
			for (int i = 0; i < exs.size(); i++) {
				ExceptionEntry ex = exs.get(i);
				ex.start = InsnNode.validate(ex.start);
				ex.end = InsnNode.validate(ex.end);
				ex.handler = InsnNode.validate(ex.handler);

				monitorNode(labels, ex.start);
				monitorNode(labels, ex.end);
				monitorNode(labels, ex.handler);
			}
		}

		if (frames != null) {
			for (int i = 0; i < frames.size(); i++) {
				Frame2 f = frames.get(i);
				f.addMonitorT(labels);
				monitorNode(labels, f.target2 = InsnNode.validate(f.target2));
			}
		}

		if (attributes != null) {
			for (int i = 0; i < attributes.size(); i++) {
				Attribute attr = attributes.get(i);
				if (attr instanceof CodeAttributeSpec) {
					((CodeAttributeSpec) attr).preToByteArray(labels);
				}
			}
		}
		for (int i = 0; i < insn.size(); i++) {
			InsnNode node = insn.get(i);

			Label label = labels.get(node);
			if (label != null) c.label(label);

			node.serialize(c);
		}

		c.visitExceptions();
		if (exs != null) {
			for (int i = 0; i < exs.size(); i++) {
				ExceptionEntry ex = exs.get(i);
				c.visitException(labels.get(ex.start), labels.get(ex.end), labels.get(ex.handler), ex.type);
			}
		}

		for (Map.Entry<InsnNode, Label> entry : labels.entrySet()) {
			try {
				entry.getKey().bci = (char) entry.getValue().getValue();
			} catch (Exception e) {
				System.out.println("failed to get bci for " + entry.getKey() + ". Is it removed");
			}
		}

		c.visitAttributes();

		if (CodeWriter.ENABLE_FV && interpretFlags != 0) {
			FrameVisitor fv = c.getFv();

			if ((interpretFlags & COMPUTE_SIZES) != 0) {
				stackSize = (char) fv.maxStackSize;
				localSize = (char) fv.maxLocalSize;
			}
			if ((interpretFlags & COMPUTE_FRAMES) != 0) {
				frames = c.frames;
			}
		} else {
			s:
			if (frames != null) {
				int stack = c.visitAttributeI("StackMapTable");
				if (stack < 0) break s;
				FrameVisitor.writeFrames(frames, w.putShort(frames.size()), cw);
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

	public static final ThreadLocal<AttrCode> WORKING_ON = new ThreadLocal<>();
	public static Label monitorNode(Map<InsnNode, Label> labels, InsnNode node) {
		node = InsnNode.validate(node);
		Label label = labels.get(node);
		if (label == null) labels.put(node, label = CodeWriter.newLabel());
		return label;
	}

	// region readCode

	public IntMap<InsnNode> parseCode(ConstantPool pool, DynByteBuf r, int len) {
		IntMap<InsnNode> pci = AsmShared.local()._IntMap(owner);
		pci.ensureCapacity(len / 2);

		InsnList insn = instructions;
		insn.ensureCapacity(len / 2);

		int begin = r.rIndex;
		len += begin;

		int negXXX = 0;
		int bci = 0;
		InsnNode curr;
		boolean widen = false;
		while (r.rIndex < len) {
			byte code = OpcodeUtil.byId(r.readByte());
			if (widen) OpcodeUtil.checkWide(code);
			switch (code) {
				case PUTFIELD:
				case GETFIELD:
				case PUTSTATIC:
				case GETSTATIC:
					curr = new FieldInsnNode(code, (CstRefField) pool.get(r));
					break;
				case INVOKEVIRTUAL:
				case INVOKESPECIAL:
				case INVOKESTATIC:
					curr = new InvokeInsnNode(code, (CstRef) pool.get(r));
					break;
				case INVOKEINTERFACE:
					curr = new InvokeInsnNode(code, (CstRefItf) pool.get(r));
					r.rIndex += 2;
					break;
				case INVOKEDYNAMIC:
					curr = new InvokeDynInsnNode((CstDynamic) pool.get(r), r.readUnsignedShort());
					break;
				case GOTO:
				case IFEQ:
				case IFNE:
				case IFLT:
				case IFGE:
				case IFGT:
				case IFLE:
				case IF_icmpeq:
				case IF_icmpne:
				case IF_icmplt:
				case IF_icmpge:
				case IF_icmpgt:
				case IF_icmple:
				case IF_acmpeq:
				case IF_acmpne:
				case IFNULL:
				case IFNONNULL:
					curr = new JmPrimer(code, r.readShort());
					break;
				case GOTO_W:
					curr = new JmPrimer(code, r.readInt());
					break;

				case JSR:
					curr = new JsrInsnNode(code, r.readShort());
					break;
				case JSR_W:
					curr = new JsrInsnNode(code, r.readInt());
					break;
				case RET:
					curr = new U2InsnNode(code, widen?r.readShort():r.readByte());
					break;
				case SIPUSH:
					curr = new U2InsnNode(code, r.readShort());
					break;
				case BIPUSH:
				case NEWARRAY:
					curr = new U1InsnNode(code, r.readByte());
					break;
				case LDC:
					curr = new LdcInsnNode(pool.array(r.readUnsignedByte()));
					break;
				case LDC_W:
				case LDC2_W:
					curr = new LdcInsnNode(pool.get(r));
					break;
				case IINC:
					curr = widen ? new IncrInsnNode(r.readUnsignedShort(), r.readShort()) : new IncrInsnNode(r.readUnsignedByte(), r.readByte());
					break;
				case NEW:
				case ANEWARRAY:
				case INSTANCEOF:
				case CHECKCAST:
					curr = new ClassInsnNode(code, (CstClass) pool.get(r));
					break;
				case MULTIANEWARRAY:
					curr = new MDArrayInsnNode((CstClass) pool.get(r), r.readUnsignedByte());
					break;
				case ISTORE:
				case LSTORE:
				case FSTORE:
				case DSTORE:
				case ASTORE:
				case ILOAD:
				case LLOAD:
				case FLOAD:
				case DLOAD:
				case ALOAD:
					curr = new U2InsnNode(code, widen?r.readUnsignedShort():r.readUnsignedByte());
					break;
				case TABLESWITCH:
					// align
					r.rIndex += (4 - ((r.rIndex - begin) & 3)) & 3;
					curr = parseTableSwitch(r);
					break;
				case LOOKUPSWITCH:
					r.rIndex += (4 - ((r.rIndex - begin) & 3)) & 3;
					curr = parseLookupSwitch(r);
					break;
				case WIDE:
					widen = true;
					continue;
				default:
					curr = new NPInsnNode(code);
					break;
			}
			insn.add(curr);
			widen = false;

			if (curr.nodeType() == 123) {
				// noinspection all
				JmPrimer jp = (JmPrimer) curr;
				// 预处理
				if (jp.switcher == null && jp.def < 0) {
					insn.set(insn.size() - 1, curr = jp.bake(validateJump(pci, bci + jp.def, jp)));
				} else {
					jp.selfIndex = bci;
					jp.arrayIndex = insn.size() - 1;
					pci.putInt(--negXXX, jp);
				}
			}

			pci.putInt(bci, curr);
			curr.bci = (char) bci;
			bci = r.rIndex - begin;
		}

		while (negXXX < 0) {
			JmPrimer n = (JmPrimer) pci.remove(negXXX++);

			InsnNode target = validateJump(pci, n.selfIndex + n.def, n);
			if (n.switcher == null) {
				insn.set(n.arrayIndex, target = n.bake(target));
			} else {
				List<SwitchEntry> map = n.switcher;
				for (int i = 0; i < map.size(); i++) {
					SwitchEntry entry = map.get(i);
					entry.pos = validateJump(pci, n.selfIndex + (int) entry.pos, n);
				}

				insn.set(n.arrayIndex, target = new SwitchInsnNode(n.code, target, map));
			}

			n._i_replace(target);
			pci.putInt(n.selfIndex, target);
		}

		return pci;
	}

	private InsnNode validateJump(IntMap<InsnNode> pci, int index, JmPrimer self) {
		InsnNode node = pci.getOrDefault(index - 1, null);
		if (node != null && node.getOpcode() == WIDE) throw new IllegalArgumentException("Jump target must not \"after\" wide");
		node = pci.get(index);
		if (node == null)
			throw new IllegalArgumentException("At " + (owner.ownerClass() + '.' + owner.name()) +
				"\nNo Such Target at " + index +
				"\n Node: " + self +
				"\n Nodes:\n" + this);
		return node;
	}

	private static JmPrimer parseTableSwitch(DynByteBuf r) {
		int def = r.readInt();
		int low = r.readInt();
		int hig = r.readInt();
		int count = hig - low + 1;

		if (count <= 0 || count > 100000) throw new ArrayIndexOutOfBoundsException(count);

		SimpleList<SwitchEntry> entries = new SimpleList<>(count);
		int i = 0;
		while (count > i) {
			entries.add(new SwitchEntry(i++ + low, r.readInt()));
		}

		return new JmPrimer(Opcodes.TABLESWITCH, def, entries);
	}

	private static JmPrimer parseLookupSwitch(DynByteBuf r) {
		int def = r.readInt();
		int count = r.readInt();

		if (count < 0 || count > 100000) throw new ArrayIndexOutOfBoundsException(count);

		SimpleList<SwitchEntry> entries = new SimpleList<>(count);
		while (count > 0) {
			entries.add(new SwitchEntry(r.readInt(), r.readInt()));
			count--;
		}
		return new JmPrimer(Opcodes.LOOKUPSWITCH, def, entries);
	}

	// endregion

	public MethodNode getOwner() {
		return owner;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		try {
			IntBiMap<InsnNode> pci = instructions.getPCMap();
			for (int i = 0; i < instructions.size(); i++) {
				InsnNode n = instructions.get(i);
				sb.append("    ").append(pci.getInt(n)).append(' ').append(n).append('\n');
			}
		} catch (Throwable e) {
			for (int i = 0; i < instructions.size(); i++) {
				InsnNode n = instructions.get(i);
				sb.append("    #").append(i).append(' ').append(n).append('\n');
			}
		}
		if (exceptions != null && !exceptions.isEmpty()) {
			sb.append("    Exception Handlers: \n");
			for (ExceptionEntry ex : exceptions) {
				sb.append("        ").append(ex).append('\n');
			}
		}
		AttrLocalVars lvt = getLVT();
		if (lvt != null) sb.append("    变量: ").append(lvt.toString(getLVTT()));
		if (frames != null) {
			sb.append("    SMT: \n");
			for (int i = 0; i < frames.size(); i++) {
				sb.append(frames.get(i));
			}
		}
		return sb.toString();
	}

	public AttrLocalVars getLVT() {
		return attributes == null ? null : (AttrLocalVars) attributes.getByName("LocalVariableTable");
	}

	public AttrLocalVars getLVTT() {
		return (AttrLocalVars) attributes.getByName("LocalVariableTypeTable");
	}

	public void clear() {
		exceptions = null;
		instructions.clear();
		frames = null;
		attributes.clear();
		interpretFlags = 0;
	}

	public Attribute attrByName(String name) {
		return attributes == null ? null : (Attribute) attributes.getByName(name);
	}

	public AttributeList attributes() {
		return attributes == null ? attributes = new AttributeList() : attributes;
	}

	@Nullable
	public AttributeList attributesNullable() {
		return attributes;
	}

	public char modifier() {
		throw new UnsupportedOperationException();
	}

	public int type() {
		return Parser.CODE_ATTR;
	}
}