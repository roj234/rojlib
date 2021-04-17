package ilib.world.structure.cascading.api;

import ilib.math.Section;
import ilib.world.structure.cascading.GenerateContext;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2020/9/19 21:08
 */
public interface IStructure {
	/**
	 * 生成结构
	 */
	void generate(@Nonnull World world, @Nonnull GenerateContext context);

	/**
	 * 获取结构所占用的空间
	 *
	 * @param offset 生成的位置
	 *
	 * @return the Rectangle
	 */
	@Nonnull
	Section getSection(@Nonnull BlockPos offset);
}
