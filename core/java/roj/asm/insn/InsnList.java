package roj.asm.insn;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.asm.AsmCache;
import roj.asm.MemberDescriptor;
import roj.asm.Opcodes;
import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.type.TypeHelper;
import roj.collect.AbstractIterator;
import roj.collect.ArrayIterator;
import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.*;

import static roj.asm.Opcodes.*;

/**
 * 指令列表，用于存储和管理JVM字节码指令序列。
 *
 * <p>{@code InsnList}是本库节点操作的核心类，负责指令的存储、解析、修改和迭代。
 * 它不仅和{@code CodeWriter}一样支持追加，还支持迭代和<b>低效</b><u>(我也不清楚)</u>的插入、删除和替换操作。
 * 同时存储非常量池引用以实现{@code ConstantPool}无关。</p>
 *
 * <p>主要特性：</p>
 * <ul>
 *   <li>和{@code CodeWriter}一样基于{@code Segment}存储，内存占用较低</li>
 *   <li>{@code InsnNode}仅为视图，但是也提供了对它的前插、后插、替换和删除操作</li>
 *   <li>提供字节码位置映射和指令迭代功能</li>
 *   <li>支持常量池引用的延迟解析和优化</li>
 *   <li>完整的指令序列操作API（添加、替换、复制等）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * InsnList list = new InsnList();
 * list.ldc(1);
 * list.vars(ILOAD, 0);
 * list.insn(IADD);
 * list._return(Type.INT_TYPE);
 *
 * // 迭代指令
 * for (InsnNode node : list) {
 *     System.out.println(node.opcode() + ": " + node.toString());
 * }
 *
 * // 替换指令段
 * InsnList replacement = new InsnList();
 * // ... 构建替换内容 ...
 * list.replace(5, 10, replacement, true);
 * }</pre></p>
 *
 * @author Roj234
 * @see AbstractCodeWriter
 * @see InsnNode
 * @see Label
 */
public final class InsnList extends AbstractCodeWriter implements Iterable<InsnNode> {
	static final InsnList EMPTY = new InsnList();

	public InsnList() {clear();}

	// region readFrom
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

		if (refCount == 0) {
			refPos = ArrayCache.INTS;
			refVal = ArrayCache.OBJECTS;
		} else {
			refPos = Arrays.copyOf(refPos, refCount);
			refVal = Arrays.copyOf(refVal, refCount);
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
	protected void visitPreInsn() {
		pc.add(bci);
		var label = bciR2W.get(bci);
		if (label != null) label(label);
	}

	Label _rel(int pos) {
		boolean before = pos < 0;
		pos += bci;

		Label lbl = bciR2W.get(pos);
		if (lbl == null) lbl = newLabel();
		else if (lbl.isBound()) return lbl;

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
	//region writeTo
	public void writeTo(CodeWriter cw) {
		ConstantPool cp = cw.cpw;
		for (int i = 0; i < refCount; i++) {
			Object c = refVal[i];
			if (c == null) continue;

			int label = refPos[i];
			int block = label >>> 16;
			int offset = label & 0xFFFF;

			DynByteBuf bb = segments.get(block).getData();

			int index;
			switch (c.getClass().getName()) {
				case "roj.asm.MemberDescriptor" -> {
					MemberDescriptor d = (MemberDescriptor) c;
					if (d.owner == null) index = cp.getInvokeDynId(d.modifier, d.name, d.rawDesc);
					else {
						switch (d.modifier >>> 14) {
							case 0 -> index = cp.getMethodRefId(d.owner, d.name, d.rawDesc);
							case 1 -> index = cp.getFieldRefId(d.owner, d.name, d.rawDesc);
							case 2 -> {
								bb.set(offset + 3, TypeHelper.paramSize(d.rawDesc) + 1);
								index = cp.getItfRefId(d.owner, d.name, d.rawDesc);
							}
							case 3 -> index = cp.getItfRefId(d.owner, d.name, d.rawDesc);
							default -> throw new IllegalStateException("unknown flag " + d.modifier);
						}
					}
				}
				case "java.lang.String" -> index = cp.getClassId(c.toString());
				default -> index = cp.fit((Constant) c);
			}

			bb.setShort(offset+1, index);
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
	//endregion
	//region 标签位置处理
	final void indexLabel(Label pos) {
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
		throw new IllegalArgumentException("找不到 "+pos);
	}
	/**
	 * 将字节码位置转换为标准化标签。
	 */
	@Contract(value = "_ -> new", pure = true)
	public final Label labelAt(int pos) {
		Label label = new Label(pos);
		indexLabel(label);
		return monitor(label);
	}
	/**
	 * 开始跟踪该标签位置。
	 *
	 * <p>如果标签已经存在于列表中，直接返回；否则创建新标签并添加到列表。</p>
	 *
	 * @param pos 原始标签位置
	 * @return 被跟踪(其value会随InsnList的操作更新)的标签对象
	 */
	public final Label monitor(Label pos) {
		if (labels.contains(pos)) return pos;

		indexLabel(pos);
		var copy = new Label(pos);
		labels.add(copy);
		return copy;
	}
	//endregion
	//region 常量值容器实现
	int[] refPos = ArrayCache.INTS;
	Object[] refVal = ArrayCache.OBJECTS;
	int refCount;
	final void addRef(Object ref) {
		int pos = (segments.isEmpty() ? 0 : (segments.size()-1) << 16) | codeOb.wIndex();

		int i = refCount;

		if (i == refPos.length) {
			int[] pp = refPos;
			Object[] pp2 = refVal;
			int nextSize = i<100 ? i+10 : i+100;
			refPos = new int[nextSize];
			refVal = new Object[nextSize];
			System.arraycopy(pp, 0, refPos, 0, i);
			System.arraycopy(pp2, 0, refVal, 0, i);
		}

		refPos[i] = pos;
		refVal[i] = ref;
		refCount = i+1;
	}
	final int findRef(Label pos) {
		int target = (pos.block << 16) | pos.offset;
		if (refCount > refPos.length) refCount = refPos.length;
		return Arrays.binarySearch(refPos, 0, refCount, target);
	}
	//endregion
	//region iterate / getter
	@Deprecated
	public int bci() { return length(); }
	public int length() {return codeOb.wIndex()+offset;}

	/**
	 * 获取第一个指令节点。
	 *
	 * @return 列表中的第一个指令节点
	 * @throws IllegalStateException 如果列表为空
	 */
	public final InsnNode first() {return getNodeAt(0);}
	/**
	 * 获取指定字节码索引位置的指令节点。
	 *
	 * <p>创建一个新的<code>InsnNode</code>视图，指向指定位置的指令。
	 * 如果指定位置不是有效的指令开始位置，会抛出异常。</p>
	 *
	 * @param bci 字节码索引位置
	 * @return 指定位置的指令节点
	 * @throws IllegalArgumentException 如果指定位置不是有效的指令开始位置
	 */
	public final InsnNode getNodeAt(int bci) {
		if (!getPcMap().contains(bci)) throw new IllegalArgumentException("bci "+bci+" is not valid");

		Label label = new Label(bci);
		indexLabel(label);

		InsnNode view = new InsnNode(this, null);
		view.setPos(label, segments.get(label.block));
		return view;
	}
	@NotNull
	public final Iterator<InsnNode> iterator() { return since(0); }
	/**
	 * 返回从指定字节码位置开始的节点迭代器。
	 *
	 * <p>返回一个迭代器，从指定的字节码索引位置开始遍历后续的所有指令节点。</p>
	 * 注意：为了性能和内存占用(PCMap)，<b>它不检查起始位置的合法性！</b>
	 *
	 * @param bci 开始的字节码索引位置
	 * @return 从指定位置开始的节点迭代器
	 */
	public final NodeIterator since(int bci) { return new NodeIterator(bci); }

	private BitSet pc;
	private int pcLen;
	public BitSet getPcMap() {
		NodeIterator itr;
		if (pc == null) {
			pc = new BitSet(length());
			itr = since(0);
		} else if (pcLen < length()) { // inserted new nodes
			itr = since(pcLen);
		} else {
			return pc;
		}

		while (itr.hasNext()) {
			InsnNode node = itr.next();
			pc.add(node.bci());
		}
		pcLen = length();

		return pc;
	}

	/**
	 * 迭代只读的节点数据。
	 */
	public final Iterable<Object> nodeDataList() {return () -> new ArrayIterator<>(refVal, 0, refCount);}
	/**
	 * 迭代可写并有位置信息的节点数据。
	 */
	public final Iterable<Map.Entry<Label, Object>> nodeData() { return new DataItr(); }

	public final class NodeIterator extends AbstractIterator<InsnNode> {
		final Label label = newLabel();
		final InsnNode view = new InsnNode(InsnList.this, this);

		public NodeIterator(int bci) {
			if (bci >= length()) stage = ENDED;
			label.setRaw(bci);
			indexLabel(label);
		}

		/**
		 * 获取不共享的当前位置标签副本。
		 *
		 * <p>返回当前迭代位置的标签副本，避免外部修改影响迭代状态。</p>
		 *
		 * @return 当前位置的标签副本
		 */
		@Deprecated
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
			view.setPos(pos, s);
			return true;
		}
	}
	private final class DataItr extends AbstractIterator<Map.Entry<Label, Object>> implements Map.Entry<Label, Object>, Iterable<Map.Entry<Label, Object>> {
		private int i;
		private final Label label = newLabel();

		DataItr() { result = this; }

		@NotNull
		@Override
		public Iterator<Map.Entry<Label, Object>> iterator() { i = 0; stage = refCount == 0 ? ENDED : GOTTEN; return this; }

		@Override
		protected boolean computeNext() {
			if (i+1 == refCount) return false;
			i++;
			return true;
		}

		public Label getKey() {
			label.block = (short) (refPos[i] >>> 16);
			label.offset = (char) refPos[i];
			indexLabel(label);
			return label;
		}
		public Object getValue() { return refVal[i]; }
		public Object setValue(Object value) {
			Object prev = refVal[i];
			if (prev.getClass() != value.getClass()) throw new IllegalArgumentException();
			refVal[i] = value;
			return prev;
		}
	}
	//endregion
	// region 追加
	public final void multiArray(String clz, int dimension) { addRef(clz); codeOb.put(MULTIANEWARRAY).putShort(0).put(dimension); }
	public final void clazz(byte code, String clz) { assertCate(code, Opcodes.CATE_CLASS); addRef(clz); codeOb.put(code).putShort(0); }
	public final void invokeDyn(int idx, String name, String desc, int reserved) {
		addRef(new MemberDescriptor(null, name, desc, idx));
		codeOb.put(INVOKEDYNAMIC).putShort(0).putShort(reserved);
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

	protected final void ldc1(byte code, Constant c) { addSegment(new Ldc(code, c.clone())); }
	protected final void ldc2(Constant c) { addRef(c.clone()); codeOb.put(LDC2_W).putShort(0); }

	/**
	 * 添加新的代码段到指令列表。
	 * 几乎只用于SwitchBlock，除非你还有自定义的Segment
	 * @see SwitchBlock
	 */
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

	/**
	 * 添加指令节点到当前指令列表的末尾。
	 * @param node 要添加的指令节点
	 * @throws NullPointerException 如果节点为null
	 */
	public final void add(InsnNode node) { node.appendTo(this); }
	// endregion
	//region 插入、替换和删除
	public final InsnList copy() { return copySlice(0, length()); }
	public final InsnList copySlice(int from, int to) { return copySlice(new Label(from), new Label(to)); }
	public final InsnList copySlice(Label from, Label to) {
		InsnList target = new InsnList();
		insncopy(this, target, from, to, Label.ZERO, Label.ZERO, true);
		return target;
	}

	public final void replace(int from, int to, InsnList value) {replace(from, to, value, true);}
	public final void replace(int from, int to, InsnList value, boolean clone) {replace(new Label(from), new Label(to), value, clone);}
	public final void replace(Label from, Label to, InsnList value) {replace(from, to, value, true);}
	public final void replace(Label from, Label to, InsnList value, boolean clone) {insncopy(value, this, new Label(0), new Label(value.length()), from, to, clone);}

	public void clear() {
		if (segments.getClass() != ArrayList.class) segments = new ArrayList<>();
		else segments.clear();
		offset = 0;

		StaticSegment b = StaticSegment.emptyWritable();
		segments.add(b);
		codeOb = b.getData();

		pc = null;
		pcLen = 0;

		for (int i = 0; i < refCount; i++)
			refVal[i] = 0;
		refCount = 0;
	}

	/**
	 * 指令插入、替换和删除的底层实现。
	 * <p>
	 * 将{@code dst}指令列表中{@code dstart}-{@code dend}范围的内容,
	 * 替换为{@code src}指令列表中{@code sstart}-{@code send}范围的内容.
	 * <p>
	 * 通过修改这些参数，即可在同一个函数中实现插入、替换与删除操作.
	 * 因为{@code InsnList}使用了复杂的压缩内部表示，所以合并到同一个函数以降低维护复杂度
	 *
	 * <p>此方法处理：</p>
	 * <ul>
	 *   <li>部分段的拆分和合并</li>
	 *   <li>标签位置的重新计算和更新</li>
	 *   <li>常量池引用的重新定位</li>
	 *   <li>字节码位置映射的重置</li>
	 * </ul>
	 *
	 * @param src 源指令列表
	 * @param dst 目标指令列表
	 * @param sstart 源开始位置
	 * @param send 源结束位置
	 * @param dstart 目标开始位置
	 * @param dend 目标结束位置
	 * @param clone 是否克隆源数据（true）或移动（false）
	 */
	public static void insncopy(InsnList src, InsnList dst, Label sstart, Label send, Label dstart, Label dend, boolean clone) {
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
				var bytecode = srcSegments.get(blockFrom++).getDataSlice();
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
				var bytecode = toBlock.getDataSlice();
				bytecode.wIndex(offTo);
				toInsert.add(new StaticSegment().setData(bytecode));
			} else if (toBlock.length() > 0) {
				toInsert.add(toBlock.move(dst, dstartMoved - blockTo, clone));
			}
		}

		int segmentRemoved = 0;

		//处理dst的partial segment
		if (dstart.block != dend.block || dstart.offset != dend.offset || dstart.offset != 0) {
			var tmp = dst.segments.get(dstart.block);

			DynByteBuf bytecode;
			if (dstart.offset != 0) {
				bytecode = tmp.getDataSlice();
				tmp.setData(bytecode.slice(dstart.offset)); // left 可能长度为零
			} else {
				bytecode = ByteList.EMPTY;
			}

			if (dstart.block == dend.block) {
				if (bytecode == ByteList.EMPTY) {
					bytecode = tmp.getDataSlice();
					segmentRemoved++;
				}
				bytecode.rIndex = dend.offset;
				if (bytecode.isReadable()) {
					toInsert.add(new StaticSegment().setData(bytecode));// right
				}
			} else {
				segmentRemoved = dend.block - dstart.block;
				if (dend.offset != 0) {
					tmp = dst.segments.get(dend.block);
					bytecode = tmp.getDataSlice();
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

		if (src.refCount > 0) {
			refSrcStart = Arrays.binarySearch(src.refPos, 0, src.refCount, sstart.block << 16 | sstart.offset);
			if (refSrcStart < 0) refSrcStart = -refSrcStart -1;
			refSrcEnd = Arrays.binarySearch(src.refPos, refSrcStart, src.refCount, send.block << 16 | send.offset);
			if (refSrcEnd < 0) refSrcEnd = -refSrcEnd -1;
		} else {
			refSrcStart = refSrcEnd = 0;
		}

		if (dst.refCount > 0) {
			refDstStart = Arrays.binarySearch(dst.refPos, 0, dst.refCount, dstart.block << 16 | dstart.offset);
			if (refDstStart < 0) refDstStart = -refDstStart -1;
			refDstEnd = Arrays.binarySearch(dst.refPos, refSrcStart, dst.refCount, dend.block << 16 | dend.offset);
			if (refDstEnd < 0) refDstEnd = -refDstEnd -1;
		} else {
			refDstStart = refDstEnd = 0;
		}

		int[] outRefPos;
		Object[] outRefVal;

		int refDstDeleteCount = refDstEnd - refDstStart;
		int refSrcInsertCount = refSrcEnd - refSrcStart;
		int refDeltaCount = refSrcInsertCount - refDstDeleteCount;
		int newRefLen = dst.refCount + refDeltaCount;

		if (dst.refPos.length < newRefLen) {
			outRefPos = Arrays.copyOf(dst.refPos, newRefLen);
			outRefVal = Arrays.copyOf(dst.refVal, newRefLen);
		} else {
			outRefPos = dst.refPos;
			outRefVal = dst.refVal;
		}

		// refPos update part1
		for (int srcPos = refDstEnd; srcPos < dst.refCount; srcPos++) {
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
		System.arraycopy(dst.refVal, refDstEnd, outRefVal, refDstEnd + refDeltaCount, dst.refCount - refDstEnd);
		// copy
		if (clone) {
			for (int srcPos = refSrcStart, dstPos = refDstStart; srcPos < refSrcEnd; srcPos++, dstPos++) {
				outRefVal[dstPos] = copyData(src.refVal[srcPos]);
			}
		} else {
			System.arraycopy(src.refVal, refSrcStart, outRefVal, refDstStart, refSrcInsertCount);
		}
		// clear
		for (int i = dst.refCount + refDeltaCount; i < dst.refCount; i++) outRefVal[i] = null;

		dst.refPos = outRefPos;
		dst.refVal = outRefVal;
		dst.refCount += refDeltaCount;

		Segment lastBlock = dstSegments.isEmpty() ? null : dstSegments.getLast();
		if (!(lastBlock instanceof StaticSegment) || ((StaticSegment) lastBlock).isReadonly()) {
			lastBlock = new StaticSegment();
			dstSegments.add(lastBlock);
		}
		dst.codeOb = lastBlock.getData();
		dst.satisfySegments();

		// 尽管可能只移动了src中的部分内容，但是partial更加危险
		if (!clone) {
			// TODO 目前有些地方故意在重用时选择不clone
			if (!src.segments.isEmpty())
				src.clear();
		}
	}
	private void satisfySegments() {
		if (segments.size() > 0) {
			int segLen = segments.size()+2;
			int[] offSum = AsmCache.getInstance().getOffSumArray(segLen);
			boolean updated = updateOffset(labels, offSum, segLen);
			AsmCache.getInstance().putOffSumArray(offSum);
			offset = offSum[segments.size()-1]; // last block begin
		} else {
			offset = 0;
		}
	}
	private static int alignSegment(Label label, int dir) {return label.block + (label.offset == 0 ? 0 : dir);}
	private static Object copyData(Object o) {
		if (o instanceof Constant) return ((Constant) o).clone();
		if (o instanceof MemberDescriptor) return ((MemberDescriptor) o).copy();
		return o;
	}
	//endregion
	public String toString() {
		StringBuilder sb = new StringBuilder().append("[\n");
		for (InsnNode node : this) {
			sb.append(' ').append(node).append('\n');
		}
		return sb.append(']').toString();
	}
}