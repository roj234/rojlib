package roj.plugins.minecraft.server.data.world;

import org.jetbrains.annotations.Range;
import roj.collect.*;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.plugins.minecraft.server.data.Registry;
import roj.plugins.minecraft.server.util.Utils;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

/**
 * @author Roj234
 * @since 2024/3/20 5:36
 */
public final class Cube<T> {
	int bits;
	BitArray palette;

	private static final XashMap.Builder<Object, Ref<?>> REF_BUILDER = Helpers.cast(XashMap.builder(Object.class, Ref.class, "block", "next", Hasher.defaul()));
	private final XashMap<T, Ref<T>> blockRef = Helpers.cast(REF_BUILDER.createSized(1 << bits));
	private final ArrayList<Ref<T>> byId = new ArrayList<>(1 << bits);
	private BitSet emptySlot;

	final Registry<T> registry;
	short pack;

	public Cube(@Range(from = 2, to = 15) int globalRegistryThresholdBits, @Range(from = 0, to = 7) int xyzBits, Registry<T> registry) {
		this.pack = (short) ((xyzBits&7) | (globalRegistryThresholdBits << 5));
		this.registry = registry;
		setAll(registry.getById(0));
	}

	private int LEN() { return 1 << ((pack&7) * 3); }
	private int THR() { return pack>>>5; }

	public void compact() {
		if (bits == 0) return;

		int nBits;
		BitArray newPalette;
		if (bits > THR()) {
			Int2IntMap idMap = countBlockTypes();
			nBits = MathUtils.getMin2PowerOf(idMap.size());
			if (nBits >= 1 << THR()) return;

			assert emptySlot == null && byId.isEmpty() && blockRef.isEmpty();

			for (Int2IntMap.Entry entry : idMap.selfEntrySet()) {
				short id = (short) entry.getIntKey();
				Ref<T> ref = new Ref<>(registry.getById(id));
				ref.id = id;
				ref.count = (short) entry.getIntValue();
				byId.add(ref);
				blockRef.add(ref);
			}

			newPalette = new BitArray(nBits, LEN());
			for (int i = LEN()-1; i >= 0; i--) {
				int id = palette.get(i);
				newPalette.set(i, blockRef.get(registry.getById(id)).id);
			}
		} else if (blockRef.size() < (1 << (bits-1))) {
			nBits = MathUtils.getMin2PowerOf(blockRef.size());
			if (nBits < 4) {
				if (bits == 4) return;
				nBits = 4;
			}

			emptySlot = null;
			byId.clear();

			Int2IntMap idMap = new Int2IntMap(blockRef.size());
			int i = 0;
			for (Ref<T> ref : blockRef) {
				idMap.put(ref.id, i);
				ref.id = (short) i++;
				byId.add(ref);
			}

			newPalette = new BitArray(nBits, LEN());
			for (i = LEN()-1; i >= 0; i--)
				newPalette.set(i, idMap.getOrDefaultInt(palette.get(i), 0));
		} else {
			return;
		}

		palette = newPalette;
		bits = nBits;
	}

	private Int2IntMap countBlockTypes() {
		Int2IntMap idMap = new Int2IntMap(1 << THR());
		for (int i = LEN()-1; i >= 0; i--) {
			idMap.putIntIfAbsent(palette.get(i), idMap.size());
		}
		return idMap;
	}

	Ref<T> getBlock(int id) { return byId.get(id); }
	public int getBits() { return bits; }
	public boolean isDirty() { return (pack&16) != 0; }
	public void clearDirty() { pack &= ~16; }

	public void setAll(T block) {
		bits = 0;
		palette = null;
		emptySlot = null;
		byId.clear();
		blockRef.clear();

		Ref<T> ref = blockRef.computeIfAbsent(block);
		ref.count = (short) LEN();
		byId.add(ref);
	}

	public T getBlock(int x, int y, int z) {
		if (palette == null) return byId.get(0).block;

		int id = linearId(x, y, z);
		id = palette.get(id);
		return bits > THR() ? registry.getById(id) : byId.get(id).block;
	}

	public T setBlock(int x, int y, int z, T block) {
		int id = linearId(x, y, z);
		int prev = palette == null ? 0 : palette.get(id);
		T prevBlock;

		int blockId;
		if (bits > THR()) {
			blockId = registry.getId(block);
			if (prev == blockId) return block;
			prevBlock = registry.getById(prev);
		} else {
			Ref<T> prevRef = byId.get(prev);
			prevBlock = prevRef.block;
			if (prevBlock == block) return block;
			if (--prevRef.count == 0) {
				blockRef.removeKey(prevBlock);
				byId.set(prev, null);

				if (emptySlot == null) emptySlot = new BitSet(byId.size());
				emptySlot.add(prev);
			}

			Ref<T> ref = blockRef.computeIfAbsent(block);
			if (ref.count++ == 0) {
				blockId = emptySlot == null ? -1 : emptySlot.first();
				if (blockId < 0) {
					blockId = byId.size();
					byId.add(ref);
				} else {
					emptySlot.remove(blockId);
					byId.set(blockId, ref);
				}
				ref.id = (short) blockId;

				if (blockId >= 1 << bits) resize(bits+1);
			} else {
				blockId = ref.id;
			}
		}

		palette.set(id, blockId);

		packet = null;
		pack |= 16;
		return prevBlock;
	}

	private void resize(int nBits) {
		if (nBits < 4 && nBits != 0) nBits = 4;

		if (nBits > THR()) {
			nBits = Integer.numberOfTrailingZeros(MathUtils.getMin2PowerOf(registry.nextId()));
			BitArray nPalette = new BitArray(nBits, LEN());
			for (int i = LEN()-1; i >= 0; i--) {
				nPalette.set(i, registry.getId(byId.get(palette.get(i)).block));
			}

			blockRef.clear();
			byId.clear();
			emptySlot = null;
			palette = nPalette;
		} else {
			BitArray nPalette = new BitArray(nBits, LEN());
			if (palette != null) nPalette.putAll(palette, 0, LEN(), false);
			palette = nPalette;
		}
		bits = nBits;
	}

	private int linearId(int x, int y, int z) {
		int len = pack&7;
		return (y << len | z) << len | x;
	}

	private byte[] packet;
	public void toMCChunkData_Full(DynByteBuf o) {
		byte[] p = packet;
		block:
		if (p == null) {
			synchronized (this) {
				if ((p = packet) != null) break block;

				ByteList buf = IOUtil.getSharedByteBuf().put(bits);
				if (bits == 0) {
					p = packet = buf.putVarInt(registry.getId(byId.get(0).block)).putVarInt(0).toByteArray();
					break block;
				} else if (bits <= THR()) {
					buf.putVarInt(byId.size());
					for (int i = 0; i < byId.size(); i++) {
						Ref<T> ref = byId.get(i);
						buf.putVarInt(ref == null ? 0 : registry.getId(ref.block));
					}
				}

				Utils.writeUncompressedLongArray(buf, palette);
				p = packet = buf.toByteArray();
			}
		}
		o.put(p);
	}
}