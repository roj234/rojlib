package roj.asm.visitor;

import roj.RequireUpgrade;
import roj.asm.AsmShared;
import roj.asm.Opcodes;
import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstUTF;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.CodeAttribute;
import roj.asm.type.TypeHelper;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.util.DynByteBuf;

import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj233
 * @since 2021/8/16 19:07
 */
public class CodeWriter extends AbstractCodeWriter {
	public static final boolean ENABLE_FV = false;

	public DynByteBuf bw;
	public ConstantPool cpw;

	public MethodNode mn;
	public int interpretFlags;
	public boolean debugLines;

	public SimpleList<Frame2> frames;
	private FrameVisitor fv;
	private boolean hasFrames;

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
		this.mn = owner;
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
	public int getStackSize() {
		if (state != 1) throw new IllegalStateException();
		return bw.readShort(tmpLenOffset-8);
	}
	public int getLocalSize() {
		if (state != 1) throw new IllegalStateException();
		return bw.readShort(tmpLenOffset-6);
	}

	protected void visitBytecode(ConstantPool cp, DynByteBuf r, int len) {
		int rPos = r.rIndex;
		r.rIndex += len;

		bciR2W = AsmShared.local().getBciMap();

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
				preVisitAttribute(cp, name, r, len);
				r.rIndex = end;
			} finally {
				r.wIndex(wend);
			}

			len1--;
		}

		r.rIndex = rPos;

		begin();
		super.visitBytecode(cp, r, len);
		validateBciRef();
	}
	@Override
	final void _visitNodePre() {
		IntMap.Entry<Label> entry = bciR2W.getEntry(bci);
		if (entry != null) label(entry.getValue());
	}

	protected void begin() {}

	protected void preVisitAttribute(ConstantPool cp, String name, DynByteBuf r, int bci) {
		switch (name) {
			case "LineNumberTable":
				int len = r.readUnsignedShort();
				while (len > 0) {
					_monitor(r.readUnsignedShort());
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
					_monitor(start);
					if (end < bci) _monitor(end);
					r.rIndex += 6;
					len--;
				}
				break;
			case "StackMapTable":
				if (interpretFlags == 0) {
					FrameVisitor.readFrames(frames = new SimpleList<>(r.readUnsignedShort(r.rIndex)), r, cp, this, mn.ownerClass(), 0xffff, 0xffff);
				}
				break;
		}
	}

	// region instruction
	public void multiArray(String clz, int dimension) { codeOb.put(MULTIANEWARRAY).putShort(cpw.getClassId(clz)).put(dimension); }
	public void clazz(byte code, String clz) { assertCate(code, Opcodes.CATE_CLASS); codeOb.put(code).putShort(cpw.getClassId(clz)); }

	protected void ldc1(byte code, Constant c) {
		int i = cpw.reset(c).getIndex();
		if (i < 256) codeOb.put(LDC).put(i);
		else codeOb.put(LDC_W).putShort(i);
	}
	protected void ldc2(Constant c) { codeOb.put(LDC2_W).putShort(cpw.reset(c).getIndex()); }

	public void invokeDyn(int idx, String name, String desc, int type) { codeOb.put(INVOKEDYNAMIC).putShort(cpw.getInvokeDynId(idx, name, desc)).putShort(type); }
	public void invokeItf(String owner, String name, String desc) { codeOb.put(INVOKEINTERFACE).putShort(cpw.getItfRefId(owner, name, desc)).putShortLE(1+TypeHelper.paramSize(desc)); }
	public void invoke(byte code, String owner, String name, String desc, boolean isInterfaceMethod) {
		// calling by user code
		if (code == INVOKEINTERFACE) {
			invokeItf(owner, name, desc);
			return;
		}

		assertCate(code, Opcodes.CATE_METHOD);
		codeOb.put(code).putShort(isInterfaceMethod ? cpw.getItfRefId(owner, name, desc) : cpw.getMethodRefId(owner, name, desc));
	}
	public void field(byte code, String owner, String name, String type) { assertCate(code, Opcodes.CATE_FIELD); codeOb.put(code).putShort(cpw.getFieldRefId(owner, name, type)); }

	// endregion

	public void clear() {
		if (state != 1) throw new IllegalStateException();
		segments.clear();
		bci = 0;
		codeOb = bw;
		bw.wIndex(tmpLenOffset);
	}

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
		// 只有隐式跳转(exception handler)的情况？
		// 增加：label(Label)写offset，cont. seg不改变off也不变

		if (!segments.isEmpty()) {
			endSegment();

			int begin = tmpLenOffset;
			List<Segment> segments = this.segments;
			int wi = bw.wIndex();

			int len = segments.size()+1;
			int[] offSum = AsmShared.local().getIntArray_(len);
			updateOffset(labels, offSum, len);

			codeOb = bw;
			boolean changed;
			do {
				bci = wi - begin;
				bw.wIndex(wi);

				changed = false;
				for (int i = 0; i < segments.size(); i++) {
					if (segments.get(i).put(this)) changed = true;

					bci = bw.wIndex() - begin;
				}

				changed |= updateOffset(labels, offSum, len);
			} while (changed);

			if (ENABLE_FV && interpretFlags != 0) {
				if (fv == null) initFrames(mn, interpretFlags);
				codeOb = bw.slice(begin, bw.wIndex()-begin);
				fv.preVisit(segments);
			}

			segments.clear();
			labels.clear();
		} else {
			// used for getBci, and on simple method it fails
			bci = bw.wIndex() - tmpLenOffset;
		}
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

	protected void visitAttribute(ConstantPool cp, String name, int len, DynByteBuf b) {
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
					Label oldEnd = bciR2W.get(end);
					b.putShort(b.rIndex-4, start = bciR2W.get(start).getValue())
					 .putShort(b.rIndex-2, (oldEnd == null ? bci:oldEnd.getValue()) - start);
					if (cp != cpw) {
						b.putShort(b.rIndex, cpw.reset(cp.get(b)).getIndex());
						b.putShort(b.rIndex, cpw.reset(cp.get(b)).getIndex());
						b.rIndex += 2;
					} else {
						b.rIndex += 6;
					}
				}
				break;
			case "StackMapTable":
				if (hasFrames) return;
				hasFrames = true;

				// must rewrite, bci change
				pos = 0;
				b = IOUtil.getSharedByteBuf().putShort(frames.size());
				FrameVisitor.writeFrames(frames, b, cpw);
				len = b.readableBytes();
				frames = null;
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

		if (a instanceof CodeAttribute) {
			int stack = visitAttributeI(a.name());
			if (stack < 0) return;
			((CodeAttribute) a).toByteArray(this);
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
	Label _rel(int pos) {
		boolean before = pos < 0;
		pos += bci;

		Label lbl = bciR2W.get(pos);
		if (lbl == null) lbl = new Label();
		else if (lbl.isValid()) return lbl;

		if (before) {
			if (segments.isEmpty() || pos < segments.get(0).length()) lbl.setFirst(pos);
			else {
				lbl.setRaw(pos);
				labels.add(lbl); // no further mod
			}
		} else { // after
			bciR2W.putInt(pos, lbl);
		}
		return lbl;
	}

	public final void label(Label x) {
		if (x.block >= 0) throw new IllegalStateException("Label already had a position at "+x);

		if (segments.isEmpty()) {
			x.setFirst(bw.wIndex() - tmpLenOffset);
			return;
		}

		x.block = (short) (segments.size()-1);
		x.offset = (char) codeOb.wIndex();
		x.value = (char) (x.offset + offset);
		labels.add(x);
	}

	public boolean hasCode() {
		return bci() > 0;
	}
	public int bci() {
		if (state < 2) return segments.isEmpty() ? (bw.wIndex() - tmpLenOffset) : codeOb.wIndex() + offset;
		return bci;
	}

	@RequireUpgrade
	public FrameVisitor getFv() {
		return fv;
	}

	public final void addSegment(Segment c) {
		if (c == null) throw new NullPointerException("c");

		if (segments.isEmpty()) segments.add(new FirstSegment(offset = bw.wIndex()-tmpLenOffset));
		else endSegment();

		segments.add(c);
		offset += c.length();

		StaticSegment ss;
		if (c instanceof StaticSegment && !((StaticSegment) c).compacted()) ss = (StaticSegment) c;
		else {
			ss = new StaticSegment();
			segments.add(ss);
		}
		codeOb = ss.getData();
	}

	public boolean hasNormalEnd(Label label) {
		Segment seg = segments.get(label.block);
		System.out.println(label);
		return true;
	}
}