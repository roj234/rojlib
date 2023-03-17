package ilib.client.renderer.entity;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerArrow;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerElytra;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * @author Roj233
 * @since 2022/4/27 18:17
 */
public class RenderNPCPlayer<T extends EntityLivingBase> extends RenderLivingBase<T> {
	protected ResourceLocation texture;

	public RenderNPCPlayer(RenderManager man) {
		super(man, new ModelPlayer(0.0F, false), 0.5F);
		addLayer(new LayerBipedArmor(this));
		addLayer(new LayerHeldItem(this));
		addLayer(new LayerArrow(this));
		addLayer(new LayerElytra(this));
	}

	public RenderNPCPlayer<T> setTexture(ResourceLocation texture) {
		this.texture = texture;
		return this;
	}

	@Override
	protected void preRenderCallback(EntityLivingBase entity, float partialTickTime) {
		GlStateManager.scale(0.9375F, 0.9375F, 0.9375F);
	}

	@Override
	protected ResourceLocation getEntityTexture(T entity) {
		return texture;
	}
}

