package ilib.world.structure.cascading;

import ilib.math.Section;
import ilib.world.structure.cascading.api.IStructure;
import ilib.world.structure.cascading.api.StructureGroup;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * @author Roj234
 * @since 2020/9/19 22:11
 */
public class GeneratableStructureMap extends StructureMap implements IStructure {
	GenerateContext context;

	/**
	 * 创建一个
	 *
	 * @param start 起始结构
	 * @param list 结构列表
	 */
	public GeneratableStructureMap(String start, StructureGroup... list) {
		super(start, list);
	}

	public GeneratableStructureMap(String start, Collection<StructureGroup> list) {
		super(start, list);
	}


	/**
	 * 生成结构
	 */
	@Override
	public void generate(@Nonnull World world, @Nonnull GenerateContext context) {
		cascade(world, this.context = new GenerateContext(context), maxDepth);
	}

	/**
	 * 获取结构所占用的空间
	 *
	 * @param offset 生成的位置
	 *
	 * @return the Rectangle
	 */
	@Nonnull
	@Override
	public Section getSection(@Nonnull BlockPos offset) {
		Section box = this.context.getMaxBox(offset);
		this.context = null;
		return box;
	}
}
