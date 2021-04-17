package ilib.asm.nx.client.model;

import com.google.common.collect.Maps;
import com.google.gson.*;

import net.minecraft.client.renderer.block.model.BlockPart;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Type;
import java.util.*;

/**
 * @author Roj233
 * @since 2022/5/6 0:31
 */
public class ModelBlockDeserializer implements JsonDeserializer<ModelBlock> {
	static int v;

	public ModelBlock deserialize(JsonElement el, Type type, JsonDeserializationContext ctx) throws JsonParseException {
		JsonObject self = el.getAsJsonObject();

		ItemCameraTransforms tr = ItemCameraTransforms.DEFAULT;
		if (self.has("display")) {
			tr = ctx.deserialize(self.get("display"), ItemCameraTransforms.class);
		}

		List<ItemOverride> ovr = this.func_187964_a(ctx, self);

		String p = this.func_178326_c(self);
		return new ModelBlock(p.isEmpty() ? null : new ResourceLocation(p), func_178325_a(ctx, self), func_178329_b(self), func_178328_a(self), true, tr, ovr);
	}

	protected List<ItemOverride> func_187964_a(JsonDeserializationContext ctx, JsonObject object) {
		test:
		if (object.has("overrides")) {
			JsonArray array = JsonUtils.getJsonArray(object, "overrides");
			if (array.size() == 0) break test;

			List<ItemOverride> parts = new ArrayList<>(array.size());
			for (int i = 0; i < array.size(); i++) {
				parts.add(ctx.deserialize(array.get(i), ItemOverride.class));
			}
			return parts;
		}

		v++;
		return Collections.emptyList();
	}

	private Map<String, String> func_178329_b(JsonObject object) {
		test:
		if (object.has("textures")) {
			JsonObject tex = object.getAsJsonObject("textures");
			if (tex.size() == 0) break test;
			Iterator<Map.Entry<String, JsonElement>> var4 = tex.entrySet().iterator();

			Map<String, String> map = Maps.newHashMap();
			while (var4.hasNext()) {
				Map.Entry<String, JsonElement> entry = var4.next();
				map.put(entry.getKey(), entry.getValue().getAsString());
			}
			return map;
		}

		v++;
		return Collections.emptyMap();
	}

	private String func_178326_c(JsonObject object) {
		return JsonUtils.getString(object, "parent", "");
	}

	protected boolean func_178328_a(JsonObject object) {
		return JsonUtils.getBoolean(object, "ambientocclusion", true);
	}

	protected List<BlockPart> func_178325_a(JsonDeserializationContext ctx, JsonObject object) {
		test:
		if (object.has("elements")) {
			JsonArray array = JsonUtils.getJsonArray(object, "elements");
			if (array.size() == 0) break test;

			List<BlockPart> parts = new ArrayList<>(array.size());
			for (int i = 0; i < array.size(); i++) {
				parts.add(ctx.deserialize(array.get(i), BlockPart.class));
			}
			return parts;
		}

		v++;
		return Collections.emptyList();
	}
}