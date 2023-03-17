package ilib.capabilities;

import net.minecraft.entity.EntityLivingBase;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class EntitySize {
	public float size;

	public final float defHeight, defWidth;

	public double relHeight, relWidth;

	public boolean transformed;

	public EntitySize(EntityLivingBase entity) {
		this.defWidth = entity.width;
		this.defHeight = entity.height;
		this.size = 1.0F;
	}

	public float getScale() {
		return size;
	}
}
