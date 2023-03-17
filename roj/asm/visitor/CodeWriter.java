package roj.asm.visitor;

import roj.RequireUpgrade;
import roj.asm.AsmShared;
import roj.asm.OpcodeUtil;
import roj.asm.cst.*;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.CodeAttributeSpec;
import roj.asm.tree.insn.SwitchEntry;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.ICodeWriter;
import roj.collect.IdentitySet;
import roj.collect.IntList;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.List;

import static roj.asm.OpcodeUtil.assertCate;
import static roj.asm.OpcodeUtil.assertTrait;
import static roj.asm.Opcodes.*;

/**
 * @author Roj233
 * @since 2021/8/16 19:07
 */
public class CodeWriter extends CodeVisitor implements ICodeWriter {
	public static final boolean ENABLE_FV = false;

	public DynByteBuf bw;
	private DynByteBuf codeOb;
	public ConstantPool cpw;

	private final List<Segment> segments = new SimpleList<>();

	private final IdentitySet<Label> labels = new IdentitySet<>();
	private final IntList offsets = new IntList();

	public MethodNode mn;
	public int interpretFlags;
	public boolean debugLines;

	public SimpleList<Frame2> frames;
	private FrameVisitor fv;
	private boolean hasFrames;

	private IntMap<Label> bciR2W;

	private int state;

	// 偏移和值
	private int tmpLenOffset, tmpLen;

	public CodeWriter() {}

	public void init(DynByteBuf bw, ConstantPool cpw) {
		this.bw = bw;
		this.cpw = cpw;

		bciR2W = null;

		offset = 0;
		interpretFlags = 0;
		segments.clear();
		labels.clear();

		bw.putShort(0).putShort(0).putInt(0);
		tmpLenOffset = bw.wIndex();

		codeOb = bw;
		state = 1;
	}
	public void initFrames(MethodNode owner, int flags) {
		if (flags != 0) {
			fv = new FrameVisitor();
			fv.init(owner);
			interpretFlags = flags;
		}
	}
	public final void init(DynByteBuf bw, ConstantPool cpw, MethodNode owner, byte flags) {
		init(bw, cpw);
		initFrames(owner, flags);
	}

	public void visitSize(int stackSize, int localSize) {
		if (state != 1) throw new IllegalStateException();
		bw.putShort(tmpLenOffset-8, stackSize).putShort(tmpLenOffset-6, localSize);
	}
	public void visitSizeMax(int stackSize, int localSize) {
		if (state != 1) throw new IllegalStateException();
		int s = bw.readShort(tmpLenOffset-8);
		if (stackSize > s) {
			bw.putShort(tmpLenOffset-8, stackSize);
		}
		s = bw.readShort(tmpLenOffset-6);
		if (localSize > s) {
			bw.putShort(tmpLenOffset-6, localSize);
		}
	}

	public void visitBytecode(ConstantPool cp, DynByteBuf r, int len) {
		int rPos = r.rIndex;
		r.rIndex += len;

		bciR2W = Helpers.cast(AsmShared.local()._IntMap(null));

		int len1 = r.readUnsignedShort();
		while (len1 > 0) {
			// S/E/H
			bciR2W.putInt(r.readUnsignedShort(), newLabel());
			bciR2W.putInt(r.readUnsignedShort(), newLabel());
			bciR2W.putInt(r.readUnsignedShort(), newLabel());
			r.rIndex += 2;

			len1--;
		}

		len1 = r.readUnsignedShort();
		int wend = r.wIndex();
		while (len1 > 0) {
			String name = ((CstUTF) cp.get(r)).str();
			int end = r.readInt() + r.rIndex;
			r.wIndex(end);
			try {
				preVisitAttribute(name, r, bciR2W, len);
				r.rIndex = end;
			} finally {
				r.wIndex(wend);
			}

			len1--;
		}

		r.rIndex = rPos;

		super.visitBytecode(cp, r, len);
	}
	@Override
	void _visitNodePre() {
		IntMap.Entry<Label> entry = bciR2W.getEntry(bci);
		if (entry != null) label(entry.getValue());
	}

	protected void preVisitAttribute(String name, DynByteBuf r, IntMap<Label> concerned, int len1) {
		switch (name) {
			case "LineNumberTable":
				int len = r.readUnsignedShort();
				while (len > 0) {
					concerned.putInt(r.readUnsignedShort(), newLabel());
					r.rIndex += 2;
					len--;
				}
				break;
			case "LocalVariableTable":
			case "LocalVariableTypeTable":
				len = r.readUnsignedShort();
				while (len > 0) {
					int start = r.readUnsignedShort();
					int end = start + r.readUnsignedShort();
					concerned.putInt(start, newLabel());
					if (end < len1) concerned.putInt(end, newLabel());
					r.rIndex += 6;
					len--;
				}
				break;
			case "StackMapTable":
				if (interpretFlags == 0) {
					FrameVisitor.readFrames(frames = new SimpleList<>(r.readUnsignedShort(r.rIndex)), r, cp, null, mn, 0xffff, 0xffff);
					for (int i = 0; i < frames.size(); i++) {
						Frame2 f = frames.get(i);
						f.addMonitorV(bciR2W);
						concerned.putInt(f.target, newLabel());
					}
				}
				break;
		}
	}

	// region instructions

	public void newArray(byte type) {
		codeOb.put(NEWARRAY).put(type);
	}
	protected final void multiArray(CstClass clz, int dimension) {
		multiArray(clz.name().str(), dimension);
	}
	public void multiArray(String clz, int dimension) {
		codeOb.put(MULTIANEWARRAY).putShort(cpw.getClassId(clz)).put((byte) dimension);
	}
	protected final void clazz(byte code, CstClass clz) {
		clazz(code, clz.name().str());
	}
	public void clazz(byte code, String clz) {
		assertCate(code,OpcodeUtil.CATE_CLASS);
		codeOb.put(code).putShort(cpw.getClassId(clz));
	}
	public void increase(int id, int count) {
		DynByteBuf ob = codeOb;
		if (id >= 0xFF || (byte) count != count) {
			// constant fold
			ob.putShort(((WIDE&0xFF)<<8) | (IINC&0xFF)).putShort(id).putShort(count);
		} else {
			ob.put(IINC).put((byte) id).put((byte) count);
		}
	}
	protected final void ldc(byte code, Constant c) { ldc(c); }
	public void ldc(Constant c) {
		c = cpw.reset(c);
		switch (c.type()) {
			case Constant.DYNAMIC:
				String dyn = ((CstDynamic) c).desc().getType().str();
				if (dyn.charAt(0) == Type.DOUBLE || dyn.charAt(0) == Type.LONG) {
					codeOb.put(LDC2_W).putShort(c.getIndex());
					return;
				}
				break;
			case Constant.STRING:
			case Constant.FLOAT:
			case Constant.INT:
			case Constant.CLASS:
			case Constant.METHOD_HANDLE:
			case Constant.METHOD_TYPE:
				break;
			case Constant.DOUBLE:
			case Constant.LONG:
				codeOb.put(LDC2_W).putShort(c.getIndex());
				return;
			default: throw new IllegalStateException("Constant " + c + " is not loadable");
		}

		int i = c.getIndex();
		if (i < 256) codeOb.put(LDC).put((byte) i);
		else codeOb.put(LDC_W).putShort(i);
	}
	protected final void invokeDyn(CstDynamic dyn, int type) {
		invokeDyn(dyn.tableIdx, dyn.desc().name().str(), dyn.desc().getType().str(), type);
	}
	public void invokeDyn(int idx, String name, String desc, int type) {
		codeOb.put(INVOKEDYNAMIC).putShort(cpw.getInvokeDynId(idx, name, desc)).putShort(type);
	}
	protected final void invokeItf(CstRefItf itf, short argc) {
		CstNameAndType desc = itf.desc();
		invokeItf(itf.className(), desc.name().str(), desc.getType().str());
	}
	public void invokeItf(String owner, String name, String desc) {
		codeOb.put(INVOKEINTERFACE).putShort(cpw.getItfRefId(owner, name, desc)).putShort(TypeHelper.paramSize(1+desc) << 8);
	}
	protected final void invoke(byte code, CstRef method) {
		CstNameAndType desc = method.desc();
		invoke(code, method.className(), desc.name().str(), desc.getType().str());
	}
	public void invoke(byte code, String owner, String name, String desc) {
		// calling by user code
		if (code == INVOKEINTERFACE) {
			invokeItf(owner, name, desc);
			return;
		}

		assertCate(code,OpcodeUtil.CATE_METHOD);
		codeOb.put(code).putShort(cpw.getMethodRefId(owner, name, desc));
	}
	protected final void field(byte code, CstRefField field) {
		CstNameAndType desc = field.desc();
		field(code, field.className(), desc.name().str(), desc.getType().str());
	}
	public void field(byte code, String owner, String name, String type) {
		assertCate(code,OpcodeUtil.CATE_FIELD);
		codeOb.put(code).putShort(cpw.getFieldRefId(owner, name, type));
	}
	protected final void jump(byte code, int offset) {
		jump(code, _rel(offset));
	}
	public void jump(byte code, Label label) {
		assertTrait(code,OpcodeUtil.TRAIT_JUMP);
		_segment(new JumpSegment(code, label));
	}
	public void one(byte code) {
		assertTrait(code,OpcodeUtil.TRAIT_ZERO_ADDRESS);
		codeOb.put(code);
	}

	public void smallNum(byte code, int value) {
		DynByteBuf ob = codeOb;
		if (code == BIPUSH) {
			ob.put(BIPUSH).put((byte) value);
		} else {
			ob.put(SIPUSH).putShort(value);
		}
	}
	public void var(byte code, int value) {
		assertCate(code,OpcodeUtil.CATE_LOAD_STORE_LEN);
		DynByteBuf ob = codeOb;
		if ((value&0xFF00) != 0) {
			ob.put(WIDE).put(code).putShort(value);
		} else {
			if (value <= 3) {
				byte c = code >= ISTORE ? ISTORE : ILOAD;
				ob.put((byte) (((code-c) << 2) + c + 5 + value));
			} else {
				ob.put(code).put((byte) value);
			}
		}
	}
	public void jsr(int value) {
		if (((short)value != value)) {
			bw.put(JSR_W).putInt(value);
		} else {
			bw.put(JSR).putShort(value);
		}
	}
	public void ret(int value) {
		throw new UnsupportedOperationException();
	}
	public static SwitchSegment newSwitch(byte code) {
		return new SwitchSegment(code);
	}
	public void switches(SwitchSegment c) {
		if (c == null) throw new NullPointerException("SwitchSegment c");
		_segment(c);
	}
	protected final void tableSwitch(DynByteBuf r) {
		int def = r.readInt();
		int low = r.readInt();
		int hig = r.readInt();
		int count = hig - low + 1;

		SimpleList<SwitchEntry> map = new SimpleList<>(count);

		if (count > 100000) throw new IllegalArgumentException("length > 100000");

		int i = 0;
		while (count > i) {
			map.add(new SwitchEntry(i++ + low, _rel(r.readInt())));
		}

		switches(new SwitchSegment(TABLESWITCH, _rel(def), map, bci));
	}
	protected final void lookupSwitch(DynByteBuf r) {
		int def = r.readInt();
		int count = r.readInt();

		SimpleList<SwitchEntry> map = new SimpleList<>(count);

		if (count > 100000) throw new IllegalArgumentException("length > 100000");

		while (count-- > 0) {
			map.add(new SwitchEntry(r.readInt(), _rel(r.readInt())));
		}

		switches(new SwitchSegment(LOOKUPSWITCH, _rel(def), map, bci));
	}

	// endregion
	// region easier instructions
	public DynByteBuf cob() { return codeOb; }

	public final void newObject(String name) {
		clazz(NEW, name);
		one(DUP);
		invoke(INVOKESPECIAL, name, "<init>", "()V");
	}
	public final void ldc(int value) {
		DynByteBuf ob = codeOb;
		if (value >= -1 && value <= 5) {
			ob.put((byte) (value+3));
		} else if ((byte) value == value) {
			ob.put(BIPUSH).put((byte) value);
		} else if ((short)value == value) {
			ob.put(SIPUSH).putShort(value);
		} else {
			ldc(new CstInt(value));
		}
	}
	public final void unpackArray(int slot, Class<?>... types) {
		Type[] types1 = new Type[types.length];
		for (int i = 0; i < types.length; i++) types1[i] = TypeHelper.class2type(types[i]);
		unpackArray(slot, 0, types1);
	}
	public final void unpackArray(int slot, int begin, Type... types) {
		for (int i = 0; i < types.length; i++) {
			var(ALOAD, slot);
			ldc(begin+i);
			one(AALOAD);

			Type klass = types[i];
			if (klass.isPrimitive()) {
				switch (klass.type) {
					case Type.BOOLEAN:
						clazz(CHECKCAST, "java/lang/Boolean");
						invoke(INVOKESPECIAL, "java/lang/Boolean", "booleanValue", "()Z");
						break;
					case Type.CHAR:
						clazz(CHECKCAST, "java/lang/Character");
						invoke(INVOKESPECIAL, "java/lang/Character", "charValue", "()C");
						break;
					default:
						clazz(CHECKCAST, "java/lang/Number");
						invoke(INVOKEVIRTUAL, "java/lang/Number", Type.toString(klass.type)+"Value", "()"+(char)klass.type);
						break;
				}
			} else {
				clazz(CHECKCAST, klass.getActualClass());
			}
		}
	}
	public final void stdOut(String s) {
		field(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
		ldc(new CstString(s));
		invoke(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V");
	}

	public void clear() {
		if (state != 1) throw new IllegalStateException();
		segments.clear();
		bci = 0;
		codeOb = bw;
		bw.wIndex(tmpLenOffset);
	}
	// endregion

	public void visitExceptions() {
		if (state != 1) throw new IllegalStateException();
		state = 2;

		satisfySegments();

		bw.putInt(tmpLenOffset - 4, bw.wIndex() - tmpLenOffset);

		tmpLenOffset = bw.wIndex();
		tmpLen = 0;
		bw.putShort(0);
	}

	private void satisfySegments() {
		offsets.clear();

		// 只有隐式跳转(exception handler)的情况？
		// 增加：label(Label)写offset，cont. seg不改变off也不变

		if (!segments.isEmpty()) {
			int begin = tmpLenOffset;
			List<Segment> segments = this.segments;
			int wi = bw.wIndex();

			offsets.setSize(segments.size()+1);
			int[] off = offsets.getRawArray();
			updateOffset(segments, off);

			codeOb = bw;
			boolean changed;
			do {
				bci = wi - begin;
				bw.wIndex(wi);

				changed = false;
				for (int i = 1; i < segments.size(); i++) {
					if (segments.get(i).put(this)) changed = true;

					bci = bw.wIndex() - begin;
				}

				changed |= updateOffset(segments, off);
			} while (changed);

			if (ENABLE_FV && interpretFlags != 0) {
				if (fv == null) initFrames(mn, interpretFlags);
				codeOb = bw.slice(begin, bw.wIndex()-begin);
				fv.preVisit(segments);
			}

			segments.clear();
		} else {
			// used for getBci, and on simple method it fails
			bci = bw.wIndex() - tmpLenOffset;
		}
		labels.clear();
	}
	private boolean updateOffset(List<Segment> segments, int[] offSum) {
		sumOffset(segments, offSum);

		boolean changed = false;
		for (Label label : labels) {
			changed |= label.update(offsets);
		}
		return changed;
	}
	private static void sumOffset(List<Segment> segments, int[] offSum) {
		int i = 0;
		offSum[0] = 0;
		do {
			Segment c = segments.get(i);
			c.computeLength();

			offSum[++i] = offSum[i-1] + c.length;
		} while (i != segments.size());
	}

	protected void visitException(int start, int end, int handler, CstClass type) {
		if (state != 2) throw new IllegalStateException();

		try {
			start = bciR2W.get(start).getValue();
			end = bciR2W.get(end).getValue();
			handler = bciR2W.get(handler).getValue();
		} catch (NullPointerException e) {
			throw new IllegalStateException("异常处理器的一部分无法找到", e);
		}
		bw.putShort(start).putShort(end).putShort(handler).putShort(type == null ? 0 : cpw.reset(type).getIndex());
		// 在这里是exception的数量
		tmpLen++;

		if (ENABLE_FV && interpretFlags != 0) {
			fv.visitExceptionEntry(start, end, handler, type == null ? null : type.name().str());
		}
	}
	public void visitException(Label start, Label end, Label handler, String type) {
		if (state != 2) throw new IllegalStateException();

		int endId = end == null ? bci : end.getValue();
		if (ENABLE_FV && interpretFlags != 0) {
			fv.visitExceptionEntry(start.getValue(), endId, handler.getValue(), type);
		}

		bw.putShort(start.getValue()).putShort(endId).putShort(handler.getValue())
		  .putShort(type == null ? 0 : cpw.getClassId(type));
		tmpLen++;
	}

	public void visitAttributes() {
		if (state != 2) throw new IllegalStateException();
		state = 3;

		bw.putShort(tmpLenOffset, tmpLen);

		tmpLenOffset = bw.wIndex();
		tmpLen = 0;
		bw.putShort(0);

		hasFrames = false;
		if (ENABLE_FV && interpretFlags != 0) {
			interpretFlags = 0;

			frames = (SimpleList<Frame2>) fv.finish(codeOb, cpw);

			int stack = visitAttributeI("StackMapTable");
			//frames.remove(0);
			bw.putShort(frames.size());
			FrameVisitor.writeFrames(frames, bw, cpw);
			visitAttributeIEnd(stack);

			hasFrames = true;
		}

		if (debugLines) lineNumberDebug();
	}

	void _addFrames(DynByteBuf data) {
		bw.putShort(cpw.getUtfId("StackMapTable")).putInt(data.readableBytes()).put(data);
		data.rIndex = data.wIndex();
		tmpLen++;
	}

	public final void lineNumberDebug() {
		int stack = visitAttributeI("LineNumberTable");
		bw.putShort(0);
		int pos = bw.wIndex();
		new CodeVisitor() {
			@Override
			void _visitNodePre() {
				CodeWriter.this.bw.putShort(bci).putShort(bci);
			}
		}.visitCopied(cpw, bw);
		bw.putShort(pos-2,(bw.wIndex()-pos)/4);
		visitAttributeIEnd(stack);
	}

	public final int visitAttributeI(String name) {
		if (state != 3) throw new IllegalStateException();
		if (name.equals("StackMapTable")) {
			if (hasFrames) return -1;
			hasFrames = true;
		}
		state = 4;

		bw.putShort(cpw.getUtfId(name)).putInt(0);
		int stack = tmpLenOffset;
		tmpLenOffset = bw.wIndex();
		return stack;
	}
	public final void visitAttributeIEnd(int stack) {
		if (state != 4) throw new IllegalStateException();
		state = 3;

		tmpLen++;
		bw.putInt(tmpLenOffset-4, bw.wIndex()-tmpLenOffset);
		tmpLenOffset = stack;
	}

	protected void visitAttribute(String name, int len, DynByteBuf b) {
		if (state != 3) throw new IllegalStateException();

		int pos = b.rIndex;
		switch (name) {
			case "LineNumberTable":
				int len1 = b.readUnsignedShort();
				if (len1 == 0) return;

				while (len1-- > 0) {
					b.putShort(b.rIndex, bciR2W.get(b.readUnsignedShort()).getValue());
					b.rIndex += 2;
				}
				break;
			case "LocalVariableTable":
			case "LocalVariableTypeTable":
				len1 = b.readUnsignedShort();
				if (len1 == 0) return;

				while (len1-- > 0) {
					int start = b.readUnsignedShort();
					int end = start+b.readUnsignedShort();
					b.putShort(b.rIndex-4, start = bciR2W.get(start).getValue())
					 .putShort(b.rIndex-2, (end>=bci?bci:bciR2W.get(end).getValue()) - start);
					b.rIndex += 6;
				}
				break;
			case "StackMapTable":
				if (hasFrames) return;
				hasFrames = true;

				//if (interpretFlags == 0) {
					boolean mod = false;
					for (int i = 0; i < frames.size(); i++) {
						Frame2 f = frames.get(i);
						mod |= f.applyMonitorV(bciR2W);
						mod |= f.target != (f.target = bciR2W.get(f.target).getValue());
					}
					if (mod) {
						pos = 0;
						b = IOUtil.getSharedByteBuf().putShort(frames.size());
						FrameVisitor.writeFrames(frames, b, cpw);
						len = b.readableBytes();
					}
					frames = null;
				//}
				break;
		}
		b.rIndex = pos;

		int end = b.rIndex + len;
		bw.putShort(cpw.getUtfId(name)).putInt(len).put(b, len);
		tmpLen++;
		b.rIndex = end;
	}
	public final void visitAttribute(Attribute a) {
		if (state != 3) throw new IllegalStateException();

		if (a instanceof CodeAttributeSpec) {
			int stack = visitAttributeI(a.name());
			if (stack < 0) return;
			((CodeAttributeSpec) a).toByteArray(this);
			visitAttributeIEnd(stack);
		} else {
			a.toByteArray(bw, cpw);
			tmpLen++;
		}
	}

	public void visitEnd() {
		if (state != 3) throw new IllegalStateException();
		state = 5;

		frames = null;
		if (bciR2W != null) bciR2W.clear();

		bw.putShort(tmpLenOffset, tmpLen);
	}

	@SuppressWarnings("fallthrough")
	public final void finish() {
		switch (state) {
			case 4:throw new IllegalStateException("Attribute stack missing");
			case 0:throw new IllegalStateException("Nothing ever written");
			case 1:visitExceptions();
			case 2:visitAttributes();
			case 3:visitEnd();
		}
	}

	// only for CodeVisitor jumping
	private Label _rel(int pos) {
		Label l = new Label();

		boolean less = pos < 0;
		pos += bci;
		// 忽略第一个StaticSegment的标签
		int firstSt = segments.isEmpty() ? bci : segments.get(0).length;
		if (pos <= firstSt) {
			l._first(bw.wIndex()-tmpLenOffset);
		} else {
			labels.add(l);

			if (less) {
				offsets.setSize(segments.size()+1);
				sumOffset(segments, offsets.getRawArray());

				l.offset = pos;
				l.update(offsets);
			} else {
				Label l1 = bciR2W.get(pos);
				if (l1 != null) {
					labels.remove(l);
					return l1;
				} else {
					bciR2W.putInt(pos, l);
				}
			}
		}
		return l;
	}

	public static Label newLabel() {
		return new Label();
	}
	public final Label label() {
		Label l = newLabel();
		label(l);
		return l;
	}
	public final void label(Label x) {
		if (x.offset >= 0) throw new IllegalStateException("Already added at " + x);

		// 忽略第一个StaticSegment的标签
		if (segments.isEmpty()) {
			x._first(bw.wIndex()-tmpLenOffset);
			return;
		}

		x.block = segments.size()-1;
		x.offset = codeOb.wIndex();
		//x.setValue(offset + x.offset);
		labels.add(x);
	}

	private int offset;

	public boolean hasCode() {
		return getBci() > 0;
	}
	public int getBci() {
		if (state < 2) return segments.isEmpty() ? (bw.wIndex() - tmpLenOffset) : codeOb.wIndex() + offset;
		return bci;
	}

	@RequireUpgrade
	public FrameVisitor getFv() {
		return fv;
	}

	public final void _segment(Segment c) {
		if (segments.isEmpty()) segments.add(new StaticSegment(bw, offset = bw.wIndex()-tmpLenOffset));
		else if (!codeOb.isReadable()) segments.remove(segments.size() - 1);
		else {
			StaticSegment prev = (StaticSegment) segments.get(segments.size() - 1);
			prev.computeLength();
			offset += prev.length;
		}

		if (c != null) {
			segments.add(c);
			offset += c.length;
		}

		StaticSegment ss = new StaticSegment(offset);
		segments.add(ss);
		codeOb = ss.data;
	}
}
