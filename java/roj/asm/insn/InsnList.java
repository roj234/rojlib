package roj.asm.insn;

import org.jetbrains.annotations.NotNull;
import roj.asm.AsmCache;
import roj.asm.MemberDescriptor;
import roj.asm.Opcodes;
import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.type.TypeHelper;
import roj.collect.AbstractIterator;
import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.collect.IntMap;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.*;

import static roj.asm.Opcodes.*;

/**
 * @author Roj233
 * @since 2021/8/16 19:07
 */
public class InsnList extends AbstractCodeWriter implements Iterable<InsnNode> {
	public InsnList() { clear(); }

	public void clear() {
		if (segments.getClass() != ArrayList.class) segments = new ArrayList<>();
		else segments.clear();
		offset = 0;

		StaticSegment b = StaticSegment.emptyWritable();
		segments.add(b);
		codeOb = b.getData();

		pc = null;
		pcLen = 0;
	}

	// region pre-parse
	@Override
	public void visitBytecode(ConstantPool cp, DynByteBuf r, int len) {
		AsmCache dd = AsmCache.getInstance();
		boolean myIsReading = false;

		if (!dd.xInsn_isReading && dd.xInsn_sharedRefPos.length > refPos.length) {
			// only read
			refPos = dd.xInsn_sharedRefPos;
			refVal = dd.xInsn_sharedRefVal;
			segments = new ArrayList<>();
			((ArrayList<Segment>) segments)._setArray(dd.xInsn_sharedSegments);
			myIsReading = dd.xInsn_isReading = true;
		}

		clear();

		pc = new BitSet(len);
		pcLen = len;

		super.visitBytecode(cp, r, len);

		if (refLen == 0) {
			refPos = ArrayCache.INTS;
			refVal = ArrayCache.OBJECTS;
		} else {
			refPos = Arrays.copyOf(refPos, refLen);
			refVal = Arrays.copyOf(refVal, refLen);
		}

		List<Segment> block = segments;
		if (block.size() == 1) segments = Collections.singletonList(block.get(0));
		else segments = Helpers.cast(Arrays.asList(block.toArray()));
		block.clear(); // clear Segment reference

		if (myIsReading) dd.xInsn_isReading = false;

		ByteList ob = (ByteList) codeOb;
		ArrayCache.putArray(ob.list);
		ob.setArray(ob.toByteArray());

		validateBciRef();
		satisfySegments();
		bciR2W.clear();
		bciR2W = null;
	}

	@Override
	void _visitNodePre() {
		pc.add(bci);
		IntMap.Entry<Label> entry = bciR2W.getEntry(bci);
		if (entry != null) label(entry.getValue());
	}

	Label _rel(int pos) {
		boolean before = pos < 0;
		pos += bci;

		Label lbl = bciR2W.get(pos);
		if (lbl == null) lbl = newLabel();
		else if (lbl.isValid()) return lbl;

		labels.add(lbl);

		if (before) {
			if (segments.isEmpty() || pos < segments.get(0).length()) lbl.setFirst(pos);
			else lbl.setRaw(pos);
		} else { // after
			bciR2W.put(pos, lbl);
		}
		return lbl;
	}

	// endregion

	@SuppressWarnings("fallthrough")
	public void write(CodeWriter cw) {
		ConstantPool cp = cw.cpw;
		for (int i = 0; i < refLen; i++) {
			Object c = refVal[i];
			if (c == null) continue;

			int label = refPos[i];
			int block = label >>> 16;
			int offset = label & 0xFFFF;

			DynByteBuf bb = segments.get(block).getData();

			int index;
			switch (c.getClass().getName()) {
				case "roj.asm.MemberDescriptor":
					MemberDescriptor d = (MemberDescriptor) c;
					if (d.owner == null) index = cp.getInvokeDynId(d.modifier, d.name, d.rawDesc);
					else {
						switch (d.modifier >>> 14) {
							default: throw new IllegalStateException("unknown flag "+d.modifier);
							case 0: index = cp.getMethodRefId(d.owner, d.name, d.rawDesc); break;
							case 1: index = cp.getFieldRefId(d.owner, d.name, d.rawDesc); break;
							case 2: bb.put(offset+3, 1+ TypeHelper.paramSize(d.rawDesc));
							case 3: index = cp.getItfRefId(d.owner, d.name, d.rawDesc); break;
						}
					}
				break;
				case "java.lang.String": index = cp.getClassId(c.toString()); break;
				default: index = cp.fit((Constant) c); break;
			}

			bb.putShort(offset+1, index);
		}

		cw.labels.addAll(labels);

		int blockFrom = cw.segments.size();
		if (cw.segments.isEmpty())
			cw.segments = new ArrayList<>();
		for (int i = 0; i < segments.size(); i++) {
			cw.segments.add(segments.get(i).move(cw, blockFrom, true));
		}
		cw.codeOb = codeOb;
	}

	private BitSet pc;
	private int pcLen;
	public BitSet getPcMap() {
		NodeIterator itr;
		if (pc == null) {
			pc = new BitSet(bci());
			itr = since(0);
		} else if (pcLen < bci()) { // inserted new nodes
			itr = since(pcLen);
		} else {
			return pc;
		}

		while (itr.hasNext()) {
			InsnNode node = itr.next();
			pc.add(node.bci());
		}
		pcLen = bci();

		return pc;
	}

	public final InsnNode getNodeAt(int bci) {
		if (!getPcMap().contains(bci)) throw new IllegalArgumentException("bci "+bci+" is not valid");
		Label label = new Label(bci);
		indexLabel(label);

		InsnNode view = new InsnNode(this, false);
		view._init(label, segments.get(label.block));
		return view;
	}
	@NotNull
	public final NodeIterator iterator() { return since(0); }
	public final NodeIterator since(int bci) { return new NodeIterator(bci); }

	public final class NodeIterator extends AbstractIterator<InsnNode> {
		final Label label = newLabel();
		final InsnNode view = new InsnNode(InsnList.this, true);

		public NodeIterator(int bci) {
			if (bci >= bci()) stage = ENDED;
			label.setRaw(bci);
			indexLabel(label);
		}

		public Label unsharedPos() {return new Label(label);}

		@Override
		protected boolean computeNext() {
			Label pos = label;
			int len = view.length();

			Segment s = segments.get(pos.block);
			if (pos.offset+len >= s.length()) {
				if (pos.offset+len != s.length()) throw new ConcurrentModificationException();

				if (pos.block == segments.size()-1) return false;

				s = segments.get(++pos.block);
				if (s.length() == 0) return false;// empty block
				pos.offset = 0;
			} else {
				pos.offset += len;
			}
			pos.value += len;

			result = view;
			view._init(pos, s);
			return true;
		}
	}

	public final void add(InsnNode node) { node.appendTo(this); }

	// region instructions
	public final void multiArray(String clz, int dimension) { addRef(clz); codeOb.put(MULTIANEWARRAY).putShort(0).put(dimension); }
	public final void clazz(byte code, String clz) { assertCate(code, Opcodes.CATE_CLASS); addRef(clz); codeOb.put(code).putShort(0); }
	public final void invokeDyn(int idx, String name, String desc, int type) {
		addRef(new MemberDescriptor(null, name, desc, idx));
		codeOb.put(INVOKEDYNAMIC).putShort(0).putShort(type);
	}
	public final void invokeItf(String owner, String name, String desc) {
		addRef(new MemberDescriptor(owner, name, desc, 2<<14));
		codeOb.put(INVOKEINTERFACE).putInt(0);
	}
	public final void invoke(byte code, String owner, String name, String desc, boolean isInterfaceMethod) {
		// calling by user code
		if (code == INVOKEINTERFACE) {
			invokeItf(owner, name, desc);
			return;
		}

		assertCate(code, Opcodes.CATE_METHOD);
		addRef(new MemberDescriptor(owner, name, desc, isInterfaceMethod ? 3<<14 : 0));
		codeOb.put(code).putShort(0);
	}
	public void field(byte code, String owner, String name, String type) {
		assertCate(code, Opcodes.CATE_FIELD);
		addRef(new MemberDescriptor(owner, name, type, 1<<14));
		codeOb.put(code).putShort(0);
	}

	protected final void ldc1(byte code, Constant c) { addSegment(new Ldc(code, c)); }
	protected final void ldc2(Constant c) { addRef(c); codeOb.put(LDC2_W).putShort(0); }
	// endregion

	public int bci() { return codeOb.wIndex()+offset; }

	public final void addSegment(Segment c) {
		if (c == null) throw new NullPointerException("c");

		endSegment();

		if (segments.getClass() != ArrayList.class)
			segments = new ArrayList<>(segments);

		segments.add(c);
		offset += c.length();

		StaticSegment b = StaticSegment.emptyWritable();
		segments.add(b);
		codeOb = b.getData();
	}

	private int[] refPos = ArrayCache.INTS;
	private Object[] refVal = ArrayCache.OBJECTS;
	private int refLen;
	final void addRef(Object ref) {
		int pos = (segments.isEmpty() ? 0 : (segments.size()-1) << 16) | codeOb.wIndex();

		int i = refLen;

		if (i == refPos.length) {
			int[] pp = refPos;
			Object[] pp2 = refVal;
			int nextSize = i<100 ? i+10 : i+512;
			refPos = new int[nextSize];
			refVal = new Object[nextSize];
			System.arraycopy(pp, 0, refPos, 0, i);
			System.arraycopy(pp2, 0, refVal, 0, i);
		}

		refPos[i] = pos;
		refVal[i] = ref;
		refLen = i+1;
	}
	// OPTIONAL: override field, invoke, invokeItf and use Shared Desc
	final Object getNodeData(Label pos) {
		int target = (pos.block << 16) | pos.offset;
		if (refLen > refPos.length) refLen = refPos.length;
		int i = Arrays.binarySearch(refPos, 0, refLen, target);
		if (i < 0) return new MemberDescriptor("", "", "()V");//throw new IllegalArgumentException("no data at " + pos);
		return refVal[i];
	}
	final void setNodeData(Label pos, Object val) {
		if (val == null) throw new IllegalArgumentException("data cannot be null");
		int target = (pos.block << 16) | pos.offset;
		int i = Arrays.binarySearch(refPos, 0, refLen, target);
		if (i < 0) throw new IllegalArgumentException("no data at " + pos);
		refVal[i] = val;
	}

	public final Iterable<Map.Entry<Label, Object>> nodeData() { return new NodeDataIterator(); }
	private final class NodeDataIterator extends AbstractIterator<Map.Entry<Label, Object>> implements Map.Entry<Label, Object>, Iterable<Map.Entry<Label, Object>> {
		private int i, realI;
		private final Label label = newLabel();

		NodeDataIterator() { result = this; }

		@Override
		protected boolean computeNext() {
			if (i == refLen) return false;
			realI = i++;
			return true;
		}

		@NotNull
		@Override
		public Iterator<Map.Entry<Label, Object>> iterator() { i = 0; stage = INITIAL; return this; }

		public Label getKey() {
			label.block = (short) (refPos[realI] >>> 16);
			label.offset = (char) refPos[realI];
			indexLabel(label);
			return label;
		}
		public Object getValue() { return refVal[realI]; }
		public Object setValue(Object value) {
			Object prev = refVal[realI];
			if (prev.getClass() != value.getClass()) throw new IllegalArgumentException();
			refVal[realI] = value;
			return prev;
		}
	}

	public final InsnList copy() { return copySlice(0, bci()); }
	public final InsnList copySlice(int from, int to) { return copySlice(new Label(from), new Label(to)); }
	public final InsnList copySlice(Label from, Label to) {
		InsnList target = new InsnList();
		var zero = Label.atZero();
		insnCopy(this, target, from, to, zero, zero, true);
		return target;
	}

	private void satisfySegments() {
		if (segments.size() > 0) {
			int segLen = segments.size()+1;
			int[] offSum = AsmCache.getInstance().getIntArray_(segLen);
			boolean updated = updateOffset(labels, offSum, segLen);
			offset = offSum[segments.size()-1]; // last block begin
		} else {
			offset = 0;
		}
	}
	private void indexLabel(Label pos) {
		if (pos.block < 0) {
			pos.value = pos.offset;

			int i = 0;
			for (int block = 0; block < segments.size(); block++) {
				Segment s = segments.get(block);
				int j = i + s.length();
				if (j > pos.offset || (j == pos.offset && block == segments.size() - 1)) {
					pos.block = (short) block;
					pos.offset -= i;
					if (pos.offset != 0 && s.getClass() != StaticSegment.class) throw new IllegalArgumentException("标签位于不可分割部分 "+pos);
					return;
				}
				i = j;
			}
		} else if (pos.block < segments.size()) {
			int len = 0;
			for (int block = 0; block < pos.block; block++) {
				len += segments.get(block).length();
			}
			pos.value = (char) (len+pos.offset);
			return;
		}
		throw new IllegalArgumentException("找不到 " + pos);
	}
	public final Label labelAt(Label pos) {
		if (labels.contains(pos)) return pos;

		indexLabel(pos);
		var copy = new Label(pos);
		labels.add(copy);
		return copy;
	}
	public final Label labelAt(int pos) {
		Label label = new Label(pos);
		indexLabel(label);
		return labelAt(label);
	}

	public static void insnCopy(InsnList src, InsnList dst, Label sstart, Label send, Label dstart, Label dend, boolean clone) {
		src.indexLabel(sstart);
		src.indexLabel(send);
		dst.indexLabel(dstart);
		dst.indexLabel(dend);
		dst.pc = null;

		ArrayList<Segment> toInsert = new ArrayList<>();

		src.satisfySegments();
		int blockFrom = sstart.block, blockTo = send.block;
		List<Segment> srcSegments = src.segments;

		// 如果是某个segment的开始，那么移动到上一个的结尾
		final int offTo = send.offset == 0 && blockTo>0 ? srcSegments.get(--blockTo).length() : send.offset;

		//处理src的partial segment
		OnlyOneStaticSegment: {
			int dstartMoved = alignSegment(dstart, 1);

			if (sstart.offset != 0) {
				// 拆分start
				var bytecode = AsmCache.getInstance().copy(srcSegments.get(blockFrom++).getData());
				bytecode.rIndex = sstart.offset;
				toInsert.add(new StaticSegment().setData(bytecode));

				// 如果总共只复制一个StaticSegment
				if (sstart.block == blockTo) {
					bytecode.wIndex(send.offset);
					break OnlyOneStaticSegment;
				}
			}

			for (int i = blockFrom; i < blockTo; i++) {
				toInsert.add(srcSegments.get(i).move(dst, dstartMoved - blockFrom, clone));
			}

			var toBlock = srcSegments.get(blockTo);
			if (offTo != toBlock.length()) {
				//拆分end
				var bytecode = AsmCache.getInstance().copy(toBlock.getData());
				bytecode.wIndex(offTo);
				toInsert.add(new StaticSegment().setData(bytecode));
			} else {
				toInsert.add(toBlock.move(dst, dstartMoved - blockTo, clone));
			}
		}

		int segmentRemoved = 0;

		//处理dst的partial segment
		if (dstart.block != dend.block || dstart.offset != dend.offset || dstart.offset != 0) {
			var tmp = dst.segments.get(dstart.block);

			DynByteBuf bytecode;
			if (dstart.offset != 0) {
				bytecode = AsmCache.getInstance().copy(tmp.getData());
				tmp.setData(bytecode.slice(dstart.offset)); // left 可能长度为零
			} else {
				bytecode = ByteList.EMPTY;
			}

			if (dstart.block == dend.block) {
				bytecode.rIndex = dend.offset;
				if (bytecode.isReadable()) {
					toInsert.add(new StaticSegment().setData(bytecode));// right
				}
			} else {
				segmentRemoved = dend.block - dstart.block;
				if (dend.offset != 0) {
					tmp = dst.segments.get(dend.block);
					bytecode = AsmCache.getInstance().copy(tmp.getData());
					bytecode.rIndex = dend.offset;
					tmp.setData(bytecode); // right
				}
			}

			// 然后修复label
			int blockDelta = toInsert.size() - segmentRemoved;
			for (var itr = dst.labels.iterator(); itr.hasNext(); ) {
				Label label = itr.next();
				if (clone ? ((label.block|label.offset) != 0 && label.value == 0) : src.labels.contains(label)) continue;

				int labelBlock = label.block;
				// 受影响的label
				check:
				if (labelBlock >= dstart.block && labelBlock <= dend.block) {
					if (label.block == dstart.block && label.offset < dstart.offset) {
						continue;
					}
					if (label.block == dend.block && label.offset >= dend.offset) {
						label.offset -= dend.offset;
						// 如果这部分还存在
						//if (leftSplit) label.offset += dstart.offset;
						break check;
					}

					label.clear();
					itr.remove();
				}

				if (label.block >= dend.block) {
					label.block += blockDelta;
				}
			}
		}

		// 按顺序插入所有的segment

		ArrayList<Segment> dstSegments = dst.segments instanceof ArrayList<Segment> x ? x : (ArrayList<Segment>) (dst.segments = new ArrayList<>(dst.segments));
		int blockDelta = toInsert.size() - segmentRemoved;

		dstSegments.ensureCapacity(dstSegments.size()+blockDelta);
		Object[] array = dstSegments.getInternalArray();

		// move
		System.arraycopy(array, dend.block, array, dend.block + blockDelta, dstSegments.size() - dend.block);
		// copy
		System.arraycopy(toInsert.getInternalArray(), 0, array, alignSegment(dstart, 1), toInsert.size());
		// clear
		for (int i = dstSegments.size()+blockDelta; i < dstSegments.size(); i++) array[i] = null;

		dstSegments._setSize(dstSegments.size()+blockDelta);

		int refSrcStart, refSrcEnd;
		int refDstStart, refDstEnd;

		if (src.refLen > 0) {
			refSrcStart = Arrays.binarySearch(src.refPos, 0, src.refLen, sstart.block << 16 | sstart.offset);
			if (refSrcStart < 0) refSrcStart = -refSrcStart -1;
			refSrcEnd = Arrays.binarySearch(src.refPos, refSrcStart, src.refLen, send.block << 16 | send.offset);
			if (refSrcEnd < 0) refSrcEnd = -refSrcEnd -1;
		} else {
			refSrcStart = refSrcEnd = 0;
		}

		if (dst.refLen > 0) {
			refDstStart = Arrays.binarySearch(dst.refPos, 0, dst.refLen, dstart.block << 16 | dstart.offset);
			if (refDstStart < 0) refDstStart = -refDstStart -1;
			refDstEnd = Arrays.binarySearch(dst.refPos, refSrcStart, dst.refLen, dend.block << 16 | dend.offset);
			if (refDstEnd < 0) refDstEnd = -refDstEnd -1;
		} else {
			refDstStart = refDstEnd = 0;
		}

		int[] outRefPos;
		Object[] outRefVal;

		int refDstDeleteCount = refDstEnd - refDstStart;
		int refSrcInsertCount = refSrcEnd - refSrcStart;
		int refDeltaCount = refSrcInsertCount - refDstDeleteCount;
		int newRefLen = dst.refLen + refDeltaCount;

		if (dst.refPos.length < newRefLen) {
			outRefPos = Arrays.copyOf(dst.refPos, newRefLen);
			outRefVal = Arrays.copyOf(dst.refVal, newRefLen);
		} else {
			outRefPos = dst.refPos;
			outRefVal = dst.refVal;
		}

		// refPos update part1
		for (int srcPos = refDstEnd; srcPos < dst.refLen; srcPos++) {
			int label = dst.refPos[srcPos];

			if (dend.offset != 0 && (label >>> 16) == dend.block) {
				label -= dend.offset;
			}
			label += blockDelta << 16;

			outRefPos[srcPos + refDeltaCount] = label;
		}
		// refPos update part2
		for (int srcPos = refSrcStart, dstPos = refDstStart; srcPos < refSrcEnd; srcPos++, dstPos++) {
			int label = src.refPos[srcPos];

			if (sstart.offset != 0 && (label >>> 16) == sstart.block) {
				label -= sstart.offset;
			}
			label += alignSegment(dstart, 1) << 16;

			outRefPos[dstPos] = label;
		}

		// move
		System.arraycopy(dst.refVal, refDstEnd, outRefVal, refDstEnd + refDeltaCount, dst.refLen - refDstEnd);
		// copy
		if (clone) {
			for (int srcPos = refSrcStart, dstPos = refDstStart; srcPos < refSrcEnd; srcPos++, dstPos++) {
				outRefVal[dstPos] = copyData(src.refVal[srcPos]);
			}
		} else {
			System.arraycopy(src.refVal, refSrcStart, outRefVal, refDstStart, refSrcInsertCount);
		}
		// clear
		for (int i = dst.refLen + refDeltaCount; i < dst.refLen; i++) outRefVal[i] = null;

		dst.refPos = outRefPos;
		dst.refVal = outRefVal;
		dst.refLen += refDeltaCount;

		Segment lastBlock = dstSegments.isEmpty() ? null : dstSegments.getLast();
		if (!(lastBlock instanceof StaticSegment) || ((StaticSegment) lastBlock).isReadonly()) {
			lastBlock = new StaticSegment();
			dstSegments.add(lastBlock);
		}
		dst.codeOb = lastBlock.getData();
		dst.satisfySegments();
	}
	private static int alignSegment(Label label, int dir) {return label.block + (label.offset == 0 ? 0 : dir);}

	public final void replaceRange(int from, int to, InsnList list1, boolean clone) { replaceRange(new Label(from), new Label(to), list1, clone); }
	public final void replaceRange(Label from, Label to, InsnList list1, boolean clone) {insnCopy(list1, this, new Label(0), new Label(list1.bci()), from, to, clone);}

	private static Object copyData(Object o) {
		if (o instanceof Constant) return ((Constant) o).clone();
		if (o instanceof MemberDescriptor) return ((MemberDescriptor) o).copy();
		return o;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder().append("[\n");
		for (InsnNode node : this) {
			sb.append(' ').append(node).append('\n');
		}
		return sb.append(']').toString();
	}
}