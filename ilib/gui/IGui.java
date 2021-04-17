package ilib.gui;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import java.awt.*;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface IGui {
	ResourceLocation getTexture();

	TileEntity getTileEntity();

	int getWidth();

	int getHeight();

	int getTop();

	int getLeft();

	default List<Rectangle> getCoveredAreas(List<Rectangle> areas) {
		areas.add(new Rectangle(getLeft(), getTop(), getWidth(), getHeight()));
		return areas;
	}

	default void componentRequestUpdate() {}
}
