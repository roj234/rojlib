package roj.asm.visitor;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.AsmShared;
import roj.asm.OpcodeUtil;
import roj.asm.cst.Constant;
import roj.asm.cst.ConstantPool;
import roj.asm.type.TypeHelper;
import roj.collect.AbstractIterator;
import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.mapper.util.Desc;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.*;

import static roj.asm.OpcodeUtil.assertCate;
import static roj.asm.Opcodes.*;

/**
 * @author Roj233
 * @since 2021/8/16 19:07
 */
public class XInsnList extends AbstractCodeWriter implements Iterable<XInsnNodeView> {
	public XInsnList() { clear(); }

	public void clear() {
		if (segments.getClass() != SimpleList.class) segments = new SimpleList<>();
		else segments.clear();
		offset = 0;

		StaticSegment b = new StaticSegment();
		segments.add(b);
		codeOb = b.getData();

		pc = null;
		pcLen = 0;
	}

	// region pre-parse
	@Override
	public void visitBytecode(ConstantPool cp, DynByteBuf r, int len) {
		AsmShared dd = AsmShared.local();
		boolean myIsReading = false;

		if (!dd.xInsn_isReading && dd.xInsn_sharedRefPos.length > refPos.length) {
			// only read
			refPos = dd.xInsn_sharedRefPos;
			refVal = dd.xInsn_sharedRefVal;
			segments = new SimpleList<>();
			((SimpleList<Segment>)segments).setRawArray(dd.xInsn_sharedSegments);
			myIsReading = dd.xInsn_isReading = true;
		}

		clear();

		pc = new MyBitSet(len);
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

		updateLabelValues();
		validateBciRef();
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
			bciR2W.putInt(pos, lbl);
		}
		return lbl;
	}

	// endregion

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
				case "roj.mapper.util.Desc":
					Desc d = (Desc) c;
					if (d.owner == null) index = cp.getInvokeDynId(d.flags, d.name, d.param);
					else {
						switch (d.flags >>> 14) {
							default: throw new IllegalStateException("unknown flag " + d.flags);
							case 1: index = cp.getMethodRefId(d.owner, d.name, d.param); break;
							case 2: index = cp.getFieldRefId(d.owner, d.name, d.param); break;
							case 3:
								index = cp.getItfRefId(d.owner, d.name, d.param);
								bb.put(offset+3, 1+TypeHelper.paramSize(d.param));
							break;
						}
					}
				break;
				case "java.lang.String": index = cp.getClassId(c.toString()); break;
				default: index = cp.reset((Constant) c).getIndex(); break;
			}

			bb.putShort(offset+1, index);
		}

		cw.labels.addAll(labels);

		int blockFrom = cw.segments.size();
		for (int i = 0; i < segments.size(); i++) {
			cw.addSegment(segments.get(i).move(cw, blockFrom, REP_CLONE));
		}
		cw.codeOb = codeOb;

		cw.segments.remove(0);
	}

	private MyBitSet pc;
	private int pcLen;
	public MyBitSet getPcMap() {
		NodeIterator itr;
		if (pc == null) {
			pc = new MyBitSet(bci());
			itr = since(0);
		} else if (pcLen < bci()) { // inserted new nodes
			itr = since(pcLen);
		} else {
			return pc;
		}

		while (itr.hasNext()) {
			XInsnNodeView node = itr.next();
			pc.add(node.bci());
		}
		pcLen = bci();

		return pc;
	}

	public final XInsnNodeView getNodeAt(int bci) {
		if (!getPcMap().contains(bci)) throw new IllegalArgumentException("bci "+bci+" is not valid");
		Label label = new Label(bci);
		updateLabelValue(label);

		XInsnNodeView view = new XInsnNodeView(this, false);
		view._init(label, segments.get(label.block));
		return view;
	}
	public final int byteLength() { return bci(); }
	@Nonnull
	public final NodeIterator iterator() { return since(0); }
	public final NodeIterator since(int bci) { return new NodeIterator(bci); }

	public final class NodeIterator extends AbstractIterator<XInsnNodeView> {
		final Label label = newLabel();
		final XInsnNodeView view = new XInsnNodeView(XInsnList.this, true);

		public NodeIterator(int bci) {
			if (bci >= bci()) stage = ENDED;
			label.setRaw(bci);
			updateLabelValue(label);
		}

		public Label unsharedPos() {
			Label lbl = newLabel();
			lbl.set(label);
			return label;
		}

		@Override
		protected boolean computeNext() {
			Label pos = label;
			int len = view.length();

			Segment s = segments.get(pos.block);
			if (pos.offset+len >= s.length()) {
				assert pos.offset+len == s.length();

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

	public final void add(XInsnNodeView node) { node.appendTo(this); }

	// region instructions
	public final void multiArray(String clz, int dimension) { addRef(clz); codeOb.put(MULTIANEWARRAY).putShort(0).put(dimension); }
	public final void clazz(byte code, String clz) { assertCate(code,OpcodeUtil.CATE_CLASS); addRef(clz); codeOb.put(code).putShort(0); }
	public final void invokeDyn(int idx, String name, String desc, int type) {
		addRef(new Desc(null, name, desc, idx));
		codeOb.put(INVOKEDYNAMIC).putShort(0).putShort(type);
	}
	public final void invokeItf(String owner, String name, String desc) {
		addRef(new Desc(owner, name, desc, 3<<14));
		codeOb.put(INVOKEINTERFACE).putInt(0);
	}
	public final void invoke(byte code, String owner, String name, String desc) {
		// calling by user code
		if (code == INVOKEINTERFACE) {
			invokeItf(owner, name, desc);
			return;
		}

		assertCate(code,OpcodeUtil.CATE_METHOD);
		addRef(new Desc(owner, name, desc, 1<<14));
		codeOb.put(code).putShort(0);
	}
	public void field(byte code, String owner, String name, String type) {
		assertCate(code,OpcodeUtil.CATE_FIELD);
		addRef(new Desc(owner, name, type, 2<<14));
		codeOb.put(code).putShort(0);
	}

	protected final void ldc1(byte code, Constant c) { addSegment(new LdcSegment(code, c)); }
	protected final void ldc2(Constant c) { addRef(c); codeOb.put(LDC2_W).putShort(0); }
	// endregion

	public final void label(Label x) {
		if (x.block >= 0) throw new IllegalStateException("Label already had a position at "+x);

		if (segments.isEmpty()) {
			x.setFirst(codeOb.wIndex());
			return;
		}

		x.block = (short) (segments.size()-1);
		x.offset = (char) codeOb.wIndex();
		x.value = (char) (x.offset + offset);
		labels.add(x);
	}

	public int bci() { return codeOb.wIndex()+offset; }

	public final void addSegment(Segment c) {
		if (c == null) throw new NullPointerException("c");

		endSegment();

		if (segments.getClass() != SimpleList.class)
			segments = new SimpleList<>(segments);

		segments.add(c);
		offset += c.length();

		StaticSegment b = new StaticSegment();
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
	public final Object getNodeData(Label pos) {
		int target = (pos.block << 16) | pos.offset;
		int i = Arrays.binarySearch(refPos, 0, refLen, target);
		if (i < 0) throw new IllegalArgumentException("no data at " + pos);
		return refVal[i];
	}
	public final void setNodeData(Label pos, Object val) {
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

		@Nonnull
		@Override
		public Iterator<Map.Entry<Label, Object>> iterator() { i = 0; stage = INITIAL; return this; }

		public Label getKey() {
			label.block = (short) (refPos[realI] >>> 16);
			label.offset = (char) refPos[realI];
			updateLabelValue(label);
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

	public final XInsnList copy() { return copySlice(0, bci()); }
	public final XInsnList copySlice(int from, int to) { return copySlice(new Label(from), new Label(to)); }
	public final XInsnList copySlice(Label from, Label to) {
		updateLabelValue(from); updateLabelValue(to);

		XInsnList target = new XInsnList();
		List<Segment> out = target.segments;
		out.clear();

		int blockFrom = from.block, blockTo = to.block;
		List<Segment> blocks = segments;

		final int offTo = to.offset == 0 && blockTo>0 ? blocks.get(--blockTo).length() : to.offset;

		boolean myFlag = false;
		Segment b = blocks.get(blockFrom);
		if (from.offset > 0) {
			DynByteBuf data = AsmShared.local().copy(b.getData());

			data.rIndex = from.offset;
			if (from.block == blockTo) {
				data.wIndex(to.offset);
				myFlag = true;
			}

			blockFrom++;

			out.add(new StaticSegment().setData(data));
		}

		for (int i = blockFrom; i < blockTo; i++) {
			out.add(blocks.get(i).move(target, -from.block, REP_CLONE));
		}

		if (!myFlag) {
			b = blocks.get(blockTo);
			if (offTo < b.length()) {
				DynByteBuf data = AsmShared.local().copy(b.getData());
				data.wIndex(offTo);
				out.add(new StaticSegment().setData(data));
			} else {
				out.add(blocks.get(blockTo).move(target, -from.block, REP_CLONE));
			}
		}

		copyRef:
		if (refLen > 0) {
			int refBefore = Arrays.binarySearch(refPos, 0, refLen, from.block << 16 | from.offset);
			if (refBefore < 0) refBefore = -refBefore -1;
			int refAfter = Arrays.binarySearch(refPos, refBefore, refLen, to.block << 16 | to.offset);
			if (refAfter < 0) refAfter = -refAfter -1;

			target.refLen = refAfter-refBefore;
			if (target.refLen == 0) break copyRef;
			target.refPos = new int[target.refLen];
			target.refVal = new Object[target.refLen];

			int deltaBlock = from.block << 16;
			for (int i = 0; i < target.refLen; i++) {
				int p = refPos[refBefore+i]-deltaBlock;
				if ((p&0xFFFF0000) == 0) p -= from.offset;
				target.refPos[i] = p;
				target.refVal[i] = copyLabel(refVal[refBefore+i]);
			}
		}

		Segment lastBlock = out.isEmpty() ? null : out.get(out.size()-1);
		if (!(lastBlock instanceof StaticSegment) || ((StaticSegment) lastBlock).compacted()) {
			lastBlock = new StaticSegment();
			out.add(lastBlock);
		}
		target.codeOb = lastBlock.getData();

		target.updateLabelValues();
		return target;
	}

	private boolean updateLabelValues() {
		if (segments.size() > 0) {
			int segLen = segments.size()+1;
			int[] offSum = AsmShared.local().getIntArray_(segLen);
			boolean updated = updateOffset(labels, offSum, segLen);
			offset = offSum[segments.size()-1]; // last block begin
			return updated;
		} else {
			offset = 0;
		}
		return false;
	}
	private void updateLabelValue(Label pos) {
		if (pos.block < 0) {
			pos.value = pos.offset;

			int i = 0;
			for (int block = 0; block < segments.size(); block++) {
				Segment s = segments.get(block);
				int j = i + s.length();
				if (j > pos.offset || (j == pos.offset && block == segments.size() - 1)) {
					pos.block = (short) block;
					pos.offset -= i;
					if (pos.offset != 0 && s.getClass() != StaticSegment.class) throw new IllegalArgumentException("标签位于不可分割部分 " + pos);
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
	public final Label labelAt(ReadonlyLabel pos) {
		if (labels.contains(pos)) return (Label) pos;

		updateLabelValue((Label) pos);
		Label label = new Label(pos);
		updateLabelValue(label);
		labels.add(label);
		return label;
	}
	public final Label labelAt(int pos) {
		Label label = new Label(pos);
		updateLabelValue(label);
		labels.add(label);
		return label;
	}

	public static final int
		/** 在复制的列表中处理,不能与其它标志共用 */
		REP_CLONE = 0,
		/** 在源列表中处理 */
		REP_SHARED = 1,
		/** 在源列表中处理,但不更新输入的Label */
		REP_SHARED_NOUPDATE = 2;
	public final void replaceRange(int from, int to, XInsnList list1, @MagicConstant(intValues = {REP_CLONE, REP_SHARED, REP_SHARED_NOUPDATE}) int mode) { replaceRange(new Label(from), new Label(to), list1, mode); }
	public final void replaceRange(Label from, Label to, XInsnList list1, @MagicConstant(intValues = {REP_CLONE, REP_SHARED, REP_SHARED_NOUPDATE}) int mode) {
		updateLabelValue(from); updateLabelValue(to);

		pc = null;

		int blockFrom = from.block, blockTo = to.block;
		SimpleList<Segment> blocks = segments.getClass() == SimpleList.class ? (SimpleList<Segment>) segments : new SimpleList<>(segments);

		boolean inserted = false;
		splitSegment: {
			final int offTo = to.offset == 0 && blockTo>0 ? blocks.get(--blockTo).length() : to.offset;

			Segment b = blocks.get(blockFrom);
			if (from.offset > 0) {
				int length = b.length();

				DynByteBuf data = b.getData();
				data.wIndex(from.offset);
				blocks.set(blockFrom, b.setData(data));

				blockFrom++;

				if (from.block == blockTo) {
					if (offTo < length) {
						data.rIndex = offTo;
						data.wIndex(data.capacity());

						b = new StaticSegment().setData(data);
						blocks.add(blockFrom, b);

						inserted = true;
					} else {
						blockTo++;
					}

					break splitSegment;
				}
			}

			b = blocks.get(blockTo);
			if (offTo < b.length()) {
				DynByteBuf data = b.getData();
				data.rIndex = offTo;
				blocks.set(blockTo, b.setData(data));
			} else {
				blockTo++;
			}
		}

		int blockInsert = list1.segments.size() - (list1.codeOb.isReadable() ? 0 : 1);
		int blockDelta = blockInsert + blockFrom - blockTo;

		for (Iterator<Label> itr = labels.iterator(); itr.hasNext(); ) {
			Label label = itr.next();

			if (label.compareTo(to) >= 0) {
				if (label.block == blockTo)
					label.offset -= to.offset;
				label.block += blockDelta;
			} else if (label.compareTo(from) > 0) {
				label.dispose();
				itr.remove();
			}
		}

		blocks.ensureCapacity(blocks.size()+blockDelta);
		Object[] array = blocks.getInternalArray();

		if (inserted) blockDelta--;

		// move
		if (blockTo < blocks.size()) System.arraycopy(array, blockTo, array, blockTo+blockDelta, blocks.size()-blockTo);
		// clear after
		for (int i = blocks.size()+blockDelta; i < blocks.size(); i++) array[i] = null;
		// copy
		if (blockInsert > 0) {
			for (int i = 0; i < blockInsert; i++) {
				array[blockFrom+i] = list1.segments.get(i).move(this, blockDelta, mode);
			}
		}
		blocks.i_setSize(blocks.size()+blockDelta);

		if (inserted) blockDelta++;

		if (mode == REP_SHARED) {
			for (Label label : list1.labels) {
				if (labels.add(label)) label.block += blockFrom;
			}
		}

		int refBefore = Arrays.binarySearch(refPos, 0, refLen, from.block << 16 | from.offset);
		if (refBefore < 0) refBefore = -refBefore -1;
		int refAfter = Arrays.binarySearch(refPos, refBefore, refLen, to.block << 16 | to.offset);
		if (refAfter < 0) refAfter = -refAfter -1;

		refAfter += replaceLabel(refBefore, refAfter, list1, 0, list1.refLen, blockFrom, mode == REP_CLONE);

		for (int k = refAfter; k < refLen; k++) {
			int pos = refPos[k];
			if (pos >>> 16 == blockTo)
				pos -= to.offset;

			pos += blockDelta << 16;
			refPos[k] = pos;
		}

		Segment lastBlock = blocks.isEmpty() ? null : blocks.get(blocks.size()-1);
		if (!(lastBlock instanceof StaticSegment) || ((StaticSegment) lastBlock).compacted()) {
			lastBlock = new StaticSegment();
			blocks.add(lastBlock);
		}
		segments = blocks;
		codeOb = lastBlock.getData();

		updateLabelValues();
	}
	private int replaceLabel(int from, int to, XInsnList listFrom, int listFromFrom, int listFromTo, int deltaBlock, boolean copy) {
		int delta = listFromTo-to + from-listFromFrom;

		if (refLen+delta > refPos.length) {
			int[] array1 = new int[refLen+delta];
			Object[] array2 = new Object[array1.length];

			if (to < refLen) {
				System.arraycopy(refPos, to, array1, to + delta, refLen - to);
				System.arraycopy(refVal, to, array2, to + delta, refLen - to);
			}

			System.arraycopy(refPos, 0, array1, 0, from);
			System.arraycopy(refVal, 0, array2, 0, from);

			refPos = array1;
			refVal = array2;
		} else if (to < refLen) {
			System.arraycopy(refPos, to, refPos, to + delta, refLen - to);
			System.arraycopy(refVal, to, refVal, to + delta, refLen - to);

			for (int i = refLen+delta; i < refLen; i++) refVal[i] = null;
		}

		deltaBlock <<= 16;
		for (int i = 0; i < listFrom.refLen; i++) {
			refPos[from+i] = listFrom.refPos[i+listFromFrom]+deltaBlock;

			Object val = listFrom.refVal[i+listFromFrom];
			refVal[from+i] = copy?copyLabel(val):val;
		}
		refLen += delta;
		return delta;
	}

	private static Object copyLabel(Object o) {
		if (o instanceof Constant) return ((Constant) o).clone();
		if (o instanceof Desc) return ((Desc) o).copy();
		return o;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder().append("[\n");
		for (XInsnNodeView node : this) {
			sb.append(' ').append(node).append('\n');
		}
		return sb.append(']').toString();
	}
}
