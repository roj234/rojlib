package ilib.world.structure.cascading;

import ilib.world.structure.cascading.api.IGenerationData;
import ilib.world.structure.cascading.api.IStructure;
import ilib.world.structure.cascading.api.IStructureData;
import ilib.world.structure.cascading.api.StructureGroup;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 新一代结构生成系统
 *
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class StructureMap {
	protected Map<String, StructureGroup> categories;
	protected String start;
	protected int maxDepth;

	/**
	 * 创建一个
	 *
	 * @param start 起始结构
	 * @param list 结构列表
	 */
	public StructureMap(String start, StructureGroup... list) {
		this.categories = new HashMap<>();
		for (StructureGroup st : list) {
			categories.put(st.getName(), st);
		}
		this.start = start;
	}

	public StructureMap(String start, Collection<StructureGroup> list) {
		this.categories = new HashMap<>();
		for (StructureGroup st : list) {
			categories.put(st.getName(), st);
		}
		this.start = start;
	}

	/**
	 * 在world的pos开始生成
	 *
	 * @param world world
	 * @param loc pos
	 * @param rand random
	 */
	public void generate(World world, BlockPos loc, Random rand) {
		cascade(world, new GenerateContext(start, loc, rand), maxDepth);
	}

	/**
	 * 内部递归
	 *
	 * @param world world
	 * @param context 上下文
	 * @param deep 递归深度
	 */
	protected void cascade(World world, GenerateContext context, int deep) {
		final String groupName = context.getGroupName();

		StructureGroup group = categories.get(groupName);
		if (group == null) throw new RuntimeException("Structure " + groupName + " was not found.");

		IStructureData sData = group.getStructures(world, context);

		if (sData == null) return;

		final IStructure structure = sData.getStructure();

		context.offset(structure);

		structure.generate(world, context);

		context.afterGenerate(structure);

		for (IGenerationData data : sData.getNearby()) {
			if (data != null) {
				GenerateContext.Snapshot snapshot = context.makeSnapShot(data);

				cascade(world, context, deep - 1);
				context.restore(snapshot);
			}
		}
	}

	public enum Direction {
		NS, WE, XY, UD;

		public EnumFacing[] toFacings() {
			switch (this) {
				case NS:
					return new EnumFacing[] {EnumFacing.NORTH, EnumFacing.SOUTH};
				case UD:
					return EnumFacing.Plane.VERTICAL.facings();
				case WE:
					return new EnumFacing[] {EnumFacing.WEST, EnumFacing.EAST};
				case XY:
					return EnumFacing.HORIZONTALS;
			}
			throw new IllegalArgumentException();
		}
	}
}