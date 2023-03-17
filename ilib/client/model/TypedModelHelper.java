/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: BatteryPower.java
 */
package ilib.client.model;

import ilib.api.registry.IRegistry;
import ilib.api.registry.Indexable;
import ilib.client.GeneratedModelRepo;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.client.model.ModelLoader;

import java.util.Collection;

public class TypedModelHelper {

	public static void itemTypedModel(Item item, ResourceLocation model, String texturePath, IRegistry<?> wrapper) {
		itemTypedModel(item, model, texturePath, wrapper.values());
	}

	public static void itemTypedModel(Item item, ResourceLocation model, String texturePath, Indexable[] values) {
		itemTypedModel(item, model, texturePath, values, true);
	}

	public static void itemTypedModel(Item item, ResourceLocation model, String textureDirectory, Indexable[] values, boolean register) {
		BlockStateBuilder modelBuilder = new BlockStateBuilder(true).addVariant("type");

		String base = model.getNamespace() + ":items/" + textureDirectory + '/';

		for (Indexable t : values) {
			modelBuilder.setVariantTexture("type", t.getName(), base + t.getName());
			if (register) {
				ModelLoader.setCustomModelResourceLocation(item, t.getIndex(), new ModelResourceLocation(model, "type" + '=' + t.getName()));
			}
		}

		GeneratedModelRepo.addModel("assets/" + model.getNamespace() + "/blockstates/" + model.getPath() + ".json", modelBuilder.build());
	}

	public static void typeModelMerged(ResourceLocation model, String texturePath, Collection<String> values) {
		typeModelMerged(new BlockStateBuilder(true), "layer0", model, texturePath, values);
	}

	public static void typeModelMerged(BlockStateBuilder b, String textureType, ResourceLocation model, String textureDirectory, Collection<String> values) {
		b.addVariant("type");

		String base = model.getNamespace() + ":items/" + textureDirectory + '/';

		for (String t : values) {
			b.setVariantTexture("type", t, textureType, base + t);
		}

		GeneratedModelRepo.addModel("assets/" + model.getNamespace() + "/blockstates/" + model.getPath() + ".json", b.build());
	}
}
