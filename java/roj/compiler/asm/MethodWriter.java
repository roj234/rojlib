package roj.compiler.asm;

import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.LineNumberTable;
import roj.asm.tree.attr.LocalVariableTable;
import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.type.IType;
import roj.asm.visitor.*;
import roj.collect.Hasher;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.compiler.asm.node.LazyIINC;
import roj.compiler.asm.node.LazyLoadStore;
import roj.compiler.context.GlobalContext;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.List;
import java.util.Objects;

import static roj.asm.Opcodes.TRAIT_JUMP;
import static roj.asm.Opcodes.assertTrait;

/**
 * @author Roj233
 * @since 2022/2/24 19:19
 */
public class MethodWriter extends CodeWriter {
	private final ConstantData owner;

	private final SimpleList<TryCatchEntry> trys = new SimpleList<>();

	public LineNumberTable lines;
	private LocalVariableTable lvt1, lvt2;

	protected final MyHashSet<Label> __attributeLabel = new MyHashSet<>(Hasher.identity());

	public void addLVTEntry(LocalVariableTable.Item v) {
		lvt1.list.add(v);
		if (v.type.genericType() != IType.STANDARD_TYPE) {
			lvt2.list.add(v);
		}
	}

	public void __addLabel(Label l) {labels.add(l);}
	public Label __attrLabel() {
		Label label = label();
		__attributeLabel.add(label);
		return label;
	}

	public MethodWriter(ConstantData unit, MethodNode mn, boolean generateLVT) {
		this.owner = unit;
		this.init(new ByteList(),unit.cp,mn,(byte)0);
		if (generateLVT) {
			lvt1 = new LocalVariableTable("LocalVariableTable");
			lvt2 = new LocalVariableTable("LocalVariableTypeTable");
		}
		//addSegment(StaticSegment.emptyWritable());
	}
	protected boolean skipFirstSegmentLabels() {return false;}

	public TryCatchEntry addException(Label str, Label end, Label proc, String s) {
		TryCatchEntry entry = new TryCatchEntry(Objects.requireNonNull(str, "start"), Objects.requireNonNull(end, "end"), Objects.requireNonNull(proc, "handler"), s);
		if (!str.equals(end)) {
			trys.add(entry);
		} else {
			GlobalContext.debugLogger().debug("无意义的异常处理器: "+entry);
		}
		return entry;
	}

	@Override
	public void visitExceptions() {
		super.visitExceptions();
		for (TryCatchEntry entry : trys) {
			visitException(entry.start,entry.end,entry.handler,entry.type);
		}
		trys.clear();
	}

	@Override
	public void visitAttributes() {
		super.visitAttributes();

		if (lvt1 != null && !lvt1.isEmpty()) {
			visitAttribute(lvt1);
			if (!lvt2.isEmpty()) visitAttribute(lvt2);
		}
		if (lines != null && !lines.isEmpty()) visitAttribute(lines);
	}

	public void load(Variable v) {
		if (v.slot != 0) varLoad(v.type.rawType(), v.slot);
		else addSegment(new LazyLoadStore(v, false));
	}
	public void store(Variable v) {
		if (v.slot != 0) varStore(v.type.rawType(), v.slot);
		else addSegment(new LazyLoadStore(v, true));
	}
	public void iinc(Variable v, int delta) {
		if (v.slot != 0) iinc(v.slot, delta);
		else addSegment(new LazyIINC(v, delta));
	}

	public void jump(byte code, Label target) { assertTrait(code, TRAIT_JUMP); addSegment(new JumpSegmentAO(code, target)); }

	public int nextSegmentId() {return segments.isEmpty() ? 1 : segments.size();}
	public void replaceSegment(int id, Segment segment) {
		segments.set(id, segment);

		for (Label label : labels) {
			if (label.getBlock() == id && label.getOffset() != 0) throw new IllegalStateException("Cannot handle "+label);
			if (label.isRaw()) throw new IllegalStateException("raw label "+label+" is not supported");
		}
	}
	public void replaceSegment(int id, MethodWriter mw) {
		boolean firstIsEmpty;
		if (mw.bw.readableBytes() > 8) {
			segments.set(id, new StaticSegment(mw.bw.slice(8, mw.bw.readableBytes()-8)));
			firstIsEmpty = false;
		} else {
			firstIsEmpty = true;
		}

		List<Segment> toInsert = mw.segments;
		if (!toInsert.isEmpty()) {
			toInsert.remove(0);
			if (toInsert.get(toInsert.size()-1).length() == 0)
				toInsert.remove(toInsert.size()-1);

			/*int blockMoved = segments.size();
			for (int i = 0; i < toInsert.size(); i++) {
				toInsert.set(i, toInsert.get(i).move(this, blockMoved, false));
			}*/

			if (firstIsEmpty) segments.set(id, toInsert.remove(0));

			segments.addAll(id+1, toInsert);

			for (Label label : labels) {
				if (label.getBlock() == id && label.getOffset() != 0) throw new IllegalStateException("Cannot handle this!");
				if (label.getBlock() > id) label.__move(toInsert.size());
				if (label.isRaw()) throw new IllegalStateException("raw label "+label+" is not supported");
			}
		}

		for (Label label : mw.labels) label.__move(id);
		labels.addAll(mw.labels);
		mw.labels.clear();
	}
	public Segment getSegment(int i) {return segments.get(i);}
	public boolean isJumpingTo(Label point) {return !segments.isEmpty() && segments.get(segments.size()-1) instanceof JumpSegment js && js.target == point;}

	public MethodWriter fork() {return new MethodWriter(owner, mn, lvt1 != null);}

	// for insertBefore()
	public DynByteBuf writeTo() {
		if (getState() < 2) visitExceptions();
		var b = bw;
		b.remove(0, 8);
		b.wIndex(b.wIndex()-2);
		return b;
	}

	public void writeTo(MethodWriter cw) {
		if (bw.wIndex() > 8) cw.codeOb.put(bw, 8, bw.wIndex()-8);

		if (cw.segments.isEmpty()) cw._addFirst();
		List<Segment> tarSeg = cw.segments;
		int offset = tarSeg.size();

		if (!segments.isEmpty()) {
			for (int i = 1; i < segments.size(); i++) {
				Segment seg = segments.get(i);
				//if (seg.length() == 0 && i != segments.size()-1) continue;

				Segment move = seg.move(cw, offset, true);
				tarSeg.add(move);
				cw._addOffset(move.length());
			}

			cw.codeOb = ((StaticSegment) tarSeg.get(tarSeg.size() - 1)).getData();
		}

		cw.labels.addAll(__attributeLabel);
		for (Label label : __attributeLabel) {
			label.__move(offset);
		}
		__attributeLabel.clear();
		labels.clear();
	}
}