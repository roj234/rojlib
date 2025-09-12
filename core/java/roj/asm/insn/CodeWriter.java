package roj.asm.insn;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;
import roj.asm.AsmCache;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.attr.CodeAttribute;
import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstUTF;
import roj.asm.frame.Frame;
import roj.asm.frame.FrameVisitor;
import roj.asm.type.TypeHelper;
import roj.collect.ArrayList;
import roj.io.IOUtil;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.reflect.Unaligned.U;

/**
 * @author Roj233
 * @since 2021/8/16 19:07
 */
public class CodeWriter extends AbstractCodeWriter {
	public static final int ADD_RETURN = 4;

	public DynByteBuf bw;
	public ConstantPool cpw;

	public MethodNode mn;
	private byte fvFlags;

	public ArrayList<Frame> frames;
	private FrameVisitor fv;
	private boolean hasFrames;

	private int state;

	// 偏移和值
	private int tmpLenOffset, tmpLen;

	public CodeWriter() {}

	public final void init(DynByteBuf bw, ConstantPool cpw, MethodNode method) {
		this.bw = bw;
		this.cpw = cpw;

		this.mn = method;
		this.fv = null;
		this.fvFlags = 0;

		bciR2W = null;

		offset = 0;
		segments.clear();
		labels.clear();

		bw.putLong(0);
		tmpLenOffset = bw.wIndex();

		codeOb = bw;
		state = 1;
	}
	public void computeFrames(@MagicConstant(flags = {Code.COMPUTE_SIZES, Code.COMPUTE_FRAMES, ADD_RETURN}) int interpretFlags) {
		this.fvFlags = (byte) interpretFlags;
	}

	/**
	 * 将buf插入代码开始之前
	 * 可能需要处理位于index0的label（见重载）
	 */
	public void insertBefore(DynByteBuf buf) {
		if (state != 1) throw new IllegalStateException();
		var bw = this.bw;

		int offset = tmpLenOffset;
		int length = buf.readableBytes();

		bw.preInsert(offset, length);
		bw.put(offset, buf);
		// update length
		if (!segments.isEmpty())
			segments.get(0).write(null, 0);

		for (Label label : labels) {
			if (label.block == 0) label.offset += length;
			if (label.isRaw()) throw new IllegalStateException("raw label "+label+" is not supported");
		}
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

		bciR2W = AsmCache.getInstance().getBciMap();

		int len1 = r.readUnsignedShort();
		while (len1 > 0) {
			// S/E/H
			bciR2W.put(r.readUnsignedShort(), newLabel());
			bciR2W.put(r.readUnsignedShort(), newLabel());
			bciR2W.put(r.readUnsignedShort(), newLabel());
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
	protected void _visitNodePre() {
		var label = bciR2W.get(bci);
		if (label != null) label(label);
	}

	protected void begin() {}

	protected void preVisitAttribute(ConstantPool cp, String name, DynByteBuf r, int bci) {
		switch (name) {
			case "LineNumberTable" -> {
				int len = r.readUnsignedShort();
				while (len > 0) {
					_monitor(r.readUnsignedShort());
					r.rIndex += 2;
					len--;
				}
			}
			case "LocalVariableTable", "LocalVariableTypeTable" -> {
				int len = r.readUnsignedShort();
				while (len > 0) {
					int start = r.readUnsignedShort();
					int end = start + r.readUnsignedShort();
					_monitor(start);
					if (end < bci) _monitor(end);
					r.rIndex += 6;
					len--;
				}
			}
			case "StackMapTable" -> {
				if (fvFlags == 0 && mn != null) {
					FrameVisitor.readFrames(frames = new ArrayList<>(r.readUnsignedShort(r.rIndex)), r, cp, this, mn.owner(), getLocalSize(), getStackSize());
				}
			}
		}
	}

	// region instruction
	public void multiArray(String clz, int dimension) { codeOb.put(MULTIANEWARRAY).putShort(cpw.getClassId(clz)).put(dimension); }
	public void clazz(byte code, String clz) { assertCate(code, Opcodes.CATE_CLASS); codeOb.put(code).putShort(cpw.getClassId(clz)); }

	protected void ldc1(byte code, Constant c) {
		// 同时读写，长度可能会变，特别是Transformer#compress...
		if (bciR2W != null) addSegment(new Ldc(code, c));
		else {
			int i = cpw.fit(c);
			if (i < 256) codeOb.put(LDC).put(i);
			else codeOb.put(LDC_W).putShort(i);
		}
	}
	protected void ldc2(Constant c) { codeOb.put(LDC2_W).putShort(cpw.fit(c)); }

	public void invokeDyn(int idx, String name, String desc, @Range(from = 0, to = 0) int reserved) { codeOb.put(INVOKEDYNAMIC).putShort(cpw.getInvokeDynId(idx, name, desc)).putShort(reserved); }
	public void invokeItf(String owner, String name, String desc) { codeOb.put(INVOKEINTERFACE).putShort(cpw.getItfRefId(owner, name, desc)).putShortLE(1+TypeHelper.paramSize(desc)); }
	public void invoke(byte code, String owner, String name, String desc, boolean isInterfaceMethod) {
		// calling by user code
		if (code == INVOKEINTERFACE) {
			invokeItf(owner, name, desc);
			return;
		}

		assertCate(code, Opcodes.CATE_METHOD);
		// itf ref只用于INVOKESTATIC指令
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

		// make modify <clinit> easier
		if ((fvFlags&ADD_RETURN) != 0) insn(RETURN);
		satisfySegments();

		bw.putInt(tmpLenOffset - 4, bw.wIndex() - tmpLenOffset);

		tmpLenOffset = bw.wIndex();
		tmpLen = 0;
		bw.putShort(0);
	}

	private void satisfySegments() {
		// 如何处理只有隐式跳转(exception handler)的情况？
		fv = null;

		if (!segments.isEmpty()) {
			endSegment();

			int begin = tmpLenOffset;
			List<Segment> segments = this.segments;
			int wi = bw.wIndex();

			int len = segments.size()+1;
			int[] offSum = AsmCache.getInstance().getIntArray_(len);
			updateOffset(labels, offSum, len);

			codeOb = bw;
			boolean changed;
			do {
				bci = wi - begin;
				bw.wIndex(wi);

				changed = false;
				for (int i = 0; i < segments.size(); i++) {
					changed |= segments.get(i).write(this, i);

					bci = bw.wIndex() - begin;
				}

				changed |= updateOffset(labels, offSum, len);
			} while (changed);

			if (fvFlags != 0) {
				codeOb = bw.slice(begin, bw.wIndex()-begin);
				fv = new FrameVisitor();
				fv.visitBlocks(mn, segments);
			}

			segments.clear();
		} else {
			// used for getBci, and on simple method it fails
			bci = bw.wIndex() - tmpLenOffset;
			if (!labels.isEmpty()) _updateOffsets();

			if (fvFlags != 0) {
				int begin = tmpLenOffset;
				codeOb = bw.slice(begin, bw.wIndex()-begin);
				fv = new FrameVisitor();
				fv.visitBlocks(mn, segments);
			}
		}

		labels.clear();
	}
	public void _updateOffsets() {
		int len = segments.size()+1;
		int[] offSum = AsmCache.getInstance().getIntArray_(len);
		updateOffset(labels, offSum, len);
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
		bw.putShort(start).putShort(end).putShort(handler).putShort(type == null ? 0 : cpw.fit(type));
		// 在这里是exception的数量
		tmpLen++;

		if (fv != null) {
			fv.visitException(start, end, handler, type == null ? null : type.value().str());
		}
	}
	public void visitException(Label start, Label end, Label handler, String type) {
		if (state != 2) throw new IllegalStateException();

		int endId = end == null ? bci : end.getValue();
		if (fv != null) {
			fv.visitException(start.getValue(), endId, handler.getValue(), type);
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
		if (fv != null) {
			int rIndex = codeOb.rIndex;
			ArrayList<Frame> frames;
			try {
				frames = (ArrayList<Frame>) fv.finish(codeOb, cpw, (fvFlags&Code.COMPUTE_FRAMES) != 0);
			} catch (Throwable e) {
				Object debugInfo;
				try {
					codeOb.rIndex = rIndex;
					debugInfo = new Code(new ByteList().putInt(0).putInt(codeOb.readableBytes()).put(codeOb).putInt(0), cpw, mn);
				} catch (Exception e1) {
					debugInfo = "(Illegal instruction "+e1+"): "+codeOb.dump();
				}
				throw new IllegalStateException("无法为代码生成StackMapTable:\n BCI #"+fv.bci()+"\n method "+mn+"\n "+debugInfo, e);
			}

			if ((fvFlags & Code.COMPUTE_SIZES) != 0) {
				U.put16UB(codeOb.array(), codeOb._unsafeAddr()-8, fv.maxStackSize);
				U.put16UB(codeOb.array(), codeOb._unsafeAddr()-6, fv.maxLocalSize);
			}

			if (frames != null) {
				int stack = visitAttributeI("StackMapTable");
				FrameVisitor.writeFrames(frames, bw, cpw);
				visitAttributeIEnd(stack);
			}
		}
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
			case "LineNumberTable" -> {
				int len1 = b.readUnsignedShort();
				if (len1 == 0) return;
				while (len1-- > 0) {
					b.putShort(b.rIndex, bciR2W.get(b.readUnsignedShort()).getValue());
					b.rIndex += 2;
				}
			}
			case "LocalVariableTable", "LocalVariableTypeTable" -> {
				int len1 = b.readUnsignedShort();
				if (len1 == 0) return;
				while (len1-- > 0) {
					int start = b.readUnsignedShort();
					int end = start + b.readUnsignedShort();
					Label oldEnd = bciR2W.get(end);
					b.putShort(b.rIndex - 4, start = bciR2W.get(start).getValue())
							.putShort(b.rIndex - 2, (oldEnd == null ? bci : oldEnd.getValue()) - start);
					if (cp != cpw) {
						b.putShort(b.rIndex, cpw.fit(cp.get(b)));
						b.putShort(b.rIndex, cpw.fit(cp.get(b)));
						b.rIndex += 2;
					} else {
						b.rIndex += 6;
					}
				}
			}
			case "StackMapTable" -> {
				if (hasFrames || frames == null) return;
				hasFrames = true;

				// must rewrite, bci change
				pos = 0;
				b = IOUtil.getSharedByteBuf();
				FrameVisitor.writeFrames(frames, b, cpw);
				len = b.readableBytes();
				frames = null;
			}
			/*case "RuntimeInvisibleTypeAnnotations", "RuntimeVisibleTypeAnnotations" -> {
				var slice = b.slice();
				var rita = Attribute.parse(null, cp, name, slice, AttrCode.ATTR_CODE);
				slice.clear();
				rita.toByteArrayNoHeader(slice, cpw);
				if (slice.wIndex() != len) {
					Logger.FALLBACK.debug("{}.{} 中遇到不支持的属性 {}", mn.owner(), mn.name(), name);
					return;
				}
			}*/
			default -> {
				Logger.FALLBACK.debug("{}.{} 中遇到不支持的属性 {}", mn.owner(), mn.name(), name);
				return;
			}
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
			bciR2W.put(pos, lbl);
		}
		return lbl;
	}

	/**
	 * 获取不稳定的代码位置，仅用作参考.
	 * Segment的大小可能因之后的修改而在序列化之前改变
	 * 如果需要精确的位置，请使用{@link #label()}
	 */
	public int bci() {
		if (state < 2) return segments.isEmpty() ? (bw.wIndex() - tmpLenOffset) : codeOb.wIndex() + offset;
		return bci;
	}

	@ApiStatus.Experimental
	public FrameVisitor getFv() {return fv;}
	public int getState() {return state;}

	public final void addSegment(Segment c) {
		if (c == null) throw new NullPointerException("c");

		if (segments.isEmpty()) _addFirst();
		else endSegment();

		segments.add(c);
		offset += c.length();

		StaticSegment ss;
		if (c instanceof StaticSegment x && !x.isReadonly()) ss = x;
		else {
			ss = new StaticSegment();
			segments.add(ss);
		}
		codeOb = ss.getData();
	}

	protected final void _addOffset(int c) {offset += c;}
	protected final void _addFirst() {
		segments = new ArrayList<>(3);
		offset = bw.wIndex()-tmpLenOffset;
		segments.add(new FirstBlock(bw, tmpLenOffset));
	}

	public boolean isContinuousControlFlow() {
		int block = segments.size()-1;
		return block >= 0 ? isContinuousControlFlow(block) : !StaticSegment.isTerminate(bw, tmpLenOffset, bw.wIndex());
	}

	public boolean isContinuousControlFlow(int targetBlock) {
		Segment seg = segments.get(targetBlock);
		if (seg.length() > 0) {
			if (!seg.isTerminate()) return true;
		} else if (targetBlock == 0 || !segments.get(targetBlock - 1).isTerminate() && isContinuousControlFlow(targetBlock - 1)) {
			return true;
		}
		for (int i = 1; i < segments.size(); i++) {
			if (segments.get(i).willJumpTo(targetBlock, -1)) return true;
		}
		return false;
	}

	@Deprecated
	public boolean isImmediateBeforeContinuous(int targetBlock) {
		Segment seg = segments.get(targetBlock);
		// targetBlock-1 : 有机会走到这个长度为0的StaticSegment
		return seg.length() > 0 ? !seg.isTerminate() : targetBlock == 0 || !segments.get(targetBlock - 1).isTerminate();
	}

	public boolean willJumpTo(Label label) {return willJumpTo(label, 1);}
	public boolean willJumpTo(Label label, int segmentId) {
		for (; segmentId < segments.size(); segmentId++) {
			if (segments.get(segmentId).willJumpTo(label.block, label.offset)) return true;
		}
		return false;
	}

	public boolean isFinished() {return state == 5;}

	@Override
	public String toString() {return getClass().getName()+"@"+ segments.toString();}
}