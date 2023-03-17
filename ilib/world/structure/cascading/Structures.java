package ilib.world.structure.cascading;

import ilib.world.structure.cascading.api.*;
import roj.collect.MyHashMap;
import roj.math.MathUtils;
import roj.math.MutableInt;
import roj.util.Helpers;

import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class Structures implements StructureGroup {
	private final double[] cdf;
	private final String name;
	private final GroupEntry[] entries;

	public Structures(String name, double[] cdf, GroupEntry[] entries) {
		this.name = name;
		this.cdf = cdf;
		this.entries = entries;
	}

	@Nonnull
	public IStructureData getStructures(@Nonnull World world, @Nonnull GenerateContext context) {
		return entries[MathUtils.cdfRandom(context.getRand(), cdf)].random(context);
	}

	@Nonnull
	public String getName() {
		return this.name;
	}

	public static class Entry {
		final byte dir, i;
		final String nextGroup;
		final double frequency;

		private Entry(byte dir, String nextGroup, double frequency) {
			this.dir = dir;
			this.frequency = frequency;
			int j = 0;
			if (this.dir != 0) {
				for (int i = 0; i < 6; i++) {
					if ((dir & (1 << i)) != 0) {
						j++;
					}
				}
			}
			this.i = (byte) j;
			this.nextGroup = nextGroup;
		}

		public Entry(Entry entry) {
			this(entry.dir, entry.nextGroup, entry.frequency);
		}

		public IGenerationData random(GenerateContext context, MutableInt flags) {
			EnumFacing face = null;

			if (this.i != 0) {
				int i = context.getRand().nextInt(this.i);
				for (int j = 0; j < 6; j++) {
					if ((dir & (1 << j)) != 0 && i-- == 0) {
						face = EnumFacing.VALUES[j];
						if ((flags.getValue() & 1 << j) != 0) {
							return null;
						}
					}
				}
			}

			return new GenerationData(face, context.getCurrPos(), nextGroup);
		}
	}

	public static class GroupEntry {
		final Entry[] entries;
		final IStructure structure;

		public GroupEntry(Entry[] entries, IStructure structure) {
			this.entries = entries;
			this.structure = structure;
		}

		public StructureData random(GenerateContext context) {
			if (entries.length == 0) return new StructureData(structure, new IGenerationData[0]);

			List<Entry> entries1 = new ArrayList<>(entries.length);
			for (Entry entry : entries) {
				if (context.getRand().nextDouble() < entry.frequency) {
					entries1.add(entry);
				}
			}

			List<IGenerationData> datas = Helpers.cast(entries1);

			MutableInt flags = new MutableInt();
			for (int i = 0; i < datas.size(); i++) {
				IGenerationData entry = entries1.get(i).random(context, flags);
				if (entry == null) {
					datas.remove(i--);
				} else {
					datas.set(i, entry);
				}
			}
			return new StructureData(structure, datas.toArray(new IGenerationData[datas.size()]));
		}
	}

	public static Builder builder(String name) {
		return new Builder(name);
	}

	public static class Builder {
		final String name;

		Map<String, Group> groups = new MyHashMap<>();
		Group current;

		public Builder(String name) {
			this.name = name;
		}

		public Builder group(String group, IStructure structure, double frequency) {
			if (groups.containsKey(group)) throw new IllegalArgumentException("Already exists");

			this.groups.put(group, current = new Group(frequency, structure));
			return this;
		}

		public Builder group(String group, IStructure structure) {
			if (groups.containsKey(group)) throw new IllegalArgumentException("Already exists");

			this.groups.put(group, current = new Group(Double.NaN, structure));
			return this;
		}

		public Builder group(IStructure structure, double frequency) {
			return group(String.valueOf(structure.hashCode()), structure, frequency);
		}

		public Builder group(IStructure structure) {
			return group(String.valueOf(structure.hashCode()), structure);
		}

		private Builder next(double frequency, String next, EnumFacing... facings) {
			if (facings == null && next != null) throw new NullPointerException("facings");
			byte flag = 0;
			if (facings != null && facings.length > 1) {
				for (EnumFacing facing : facings) {
					flag |= 1 << facing.ordinal();
					if ((this.current.dir & 1 << facing.ordinal()) != 0) {
						throw new IllegalArgumentException("Duplicate facing " + facing);
					} else {
						this.current.dir |= 1 << facing.ordinal();
					}
				}
			}
			this.current.entries.add(new Entry(flag, next, frequency));

			return this;
		}

		public Builder next(String next, EnumFacing... facings) {
			return next(1, next, facings);
		}

		public Builder next(String next, StructureMap.Direction direction) {
			return next(1, next, direction.toFacings());
		}

		public Builder next(String next, double frequency, StructureMap.Direction direction) {
			return next(frequency, next, direction.toFacings());
		}

		public Builder next(String next, double frequency, EnumFacing... facings) {
			return next(frequency, next, facings);
		}

		public Structures build() {
			double[] pdf = new double[groups.size()];
			GroupEntry[] entries1 = new GroupEntry[groups.size()];

			double sum = 0;

			boolean hasNull = false;

			int i = 0;
			for (Map.Entry<String, Group> entry1 : groups.entrySet()) {
				Group g = entry1.getValue();
				List<Entry> entries = g.entries;

				double d = g.frequency;
				pdf[i] = d;
				if (d == d) {
					sum += d;
				} else {
					hasNull = true;
				}

				entries1[i++] = new GroupEntry(entries.toArray(new Entry[entries.size()]), g.structure);
			}

			if (sum > 1.1) {
				throw new IllegalArgumentException("S[frequency] (" + sum + ") > 1!");
			}

			if (hasNull) {
				double each = (1 - sum) / groups.size();
				for (int j = 0; j < pdf.length; j++) {
					double d = pdf[j];
					if (d != d) {
						pdf[j] = each;
					}
				}
			}


			return new Structures(name, MathUtils.pdf2cdf(pdf), entries1);
		}

		public Builder inherit(Structures group, int index) {
			this.current.entries.addAll(Arrays.asList(group.entries[index].entries));
			return this;
		}

		private static class Group {
			final List<Entry> entries = new LinkedList<>();
			final double frequency;
			final IStructure structure;
			public int dir;

			private Group(double frequency, IStructure structure) {
				this.frequency = frequency;
				this.structure = structure;
			}
		}
	}
}