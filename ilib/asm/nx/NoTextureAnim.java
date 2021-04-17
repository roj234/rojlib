package ilib.asm.nx;

import ilib.Config;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;

import java.util.List;

@Nixim("/")
abstract class NoTextureAnim extends TextureMap {
	NoTextureAnim() {
		super(null);
	}

	@Shadow
	private List<TextureAtlasSprite> listAnimatedSprites;

	@Inject("/")
	public void updateAnimations() {
		List<TextureAtlasSprite> tex = listAnimatedSprites;
		for (int i = 0; i < tex.size(); i++) {
			TextureAtlasSprite sprite = tex.get(i);
			if (Config.noAnim.contains(sprite.getIconName())) {
				GlStateManager.bindTexture(getGlTextureId());

				sprite.updateAnimation();
			}
		}
	}
}
