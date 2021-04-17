package ilib.client.model;

import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.data.Type;

/**
 * @author Roj234
 * @since 2020/6/13 11:04
 */
public class BlockStateBuilder {
	public final CMapping jsonData, defaults, variants;

	public final boolean isItem;

	public BlockStateBuilder(String json, boolean item) {
		try {
			jsonData = JSONParser.parses(json).asMap();
		} catch (ParseException e) {
			throw new RuntimeException("Illegal model data ", e);
		}
		defaults = jsonData.getOrCreateMap("defaults");
		variants = jsonData.getOrCreateMap("variants");
		isItem = item;
	}

	public BlockStateBuilder(boolean item) {
		try {
			jsonData = JSONParser.parses(
									 item ? "{\n" + "  \"forge_marker\": 1,\n" + "  \"defaults\": {\n" + "    \"model\": \"builtin/generated\",\n" + "    \"transform\": \"forge:default-item\"\n" + "  },\n" + "  \"variants\": {}\n" + "}" : "{\n" + "  \"forge_marker\": 1,\n" + "  \"defaults\": {},\n" + "  \"variants\": {}\n" + "}")
								 .asMap();
		} catch (ParseException e) {
			throw new RuntimeException("It can't happen! ", e);
		}
		defaults = jsonData.get("defaults").asMap();
		variants = jsonData.get("variants").asMap();
		isItem = item;
	}

	public CMapping getDefault() {
		return defaults;
	}

	public String build() {
		return jsonData.toShortJSON();
	}

	public BlockStateBuilder addVariant4D() {
		return addVariant4D("facing");
	}

	public BlockStateBuilder addVariant4D(String k) {
		CMapping map2 = new CMapping();
		map2.put("y", 180);
		CMapping map3 = new CMapping();
		map3.put("y", 270);
		CMapping map4 = new CMapping();
		map4.put("y", 90);
		return addVariant(k).addVariantValue(k, "north").addVariantValue(k, "south", map2).addVariantValue(k, "west", map3).addVariantValue(k, "east", map4);
	}

	public BlockStateBuilder merge(BlockStateBuilder another) {
		this.variants.merge(another.variants, false, true);
		return this;
	}

	public BlockStateBuilder addVariant(String key) {
		variants.getOrCreateMap(key);
		return this;
	}

	public BlockStateBuilder addVariantValue(String key, String value) {
		variants.getOrCreateMap(key).getOrCreateMap(value);
		return this;
	}

	public BlockStateBuilder addVariantValue(String key, String tag, CEntry entry) {
		variants.getOrCreateMap(key).put(tag, entry);
		return this;
	}

	public BlockStateBuilder addVariantValue(String key, String tag, String json) throws ParseException {
		variants.getOrCreateMap(key).put(tag, JSONParser.parses(json));
		return this;
	}

	public BlockStateBuilder setSingleVariantValue(String key, String tag, CEntry entry) {
		CList vList = variants.getOrCreateList(key);
		CMapping map;
		if (vList.size() == 0) {
			vList.add(map = new CMapping());
		} else {
			map = vList.get(0).asMap();
		}
		map.put(tag, entry);
		return this;
	}

	public BlockStateBuilder addDefaultEntry(String name, String json) {
		try {
			defaults.put(name, JSONParser.parses(json));
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return this;
	}

	public BlockStateBuilder setDefaultModel(String model) {
		if (model == null) {
			defaults.remove("model");
		} else {
			defaults.put("model", model);
		}
		return this;
	}

    /*public BlockStateBuilder parent(String parent) {
        if(parent == null) {
            defaults.remove("parent");
        } else {
            defaults.put("parent", parent);
        }
        return this;
    }*/

	public BlockStateBuilder setDefaultTexture(String texture) {
		return setDefaultTexture("all", texture);
	}

	public BlockStateBuilder setDefaultTexture(String textureName, String texture) {
		CMapping map = defaults.getOrCreateMap("textures");

		if (texture == null) {
			map.remove(textureName);
		} else {
			map.put(textureName, texture);
		}

		return this;
	}

	public CMapping getVariantMap(String key, String value) {
		return variants.getOrCreateMap(key).getOrCreateMap(value);
	}

	public BlockStateBuilder setVariantModel(String key, String value, String model) {
		getVariantMap(key, value).put("model", model);
		return this;
	}

	public BlockStateBuilder setVariantTexture(String key, String value, String texture) {
		return setVariantTexture(key, value, isItem ? "layer0" : "all", texture);
	}

	public BlockStateBuilder setVariantTexture(String key, String value, String textureName, String texture) {
		CMapping variant = getVariantMap(key, value);
		if (!variant.containsKey("textures")) {
			variant.put("textures", new CMapping());
		}
		variant.get("textures").asMap().put(textureName, texture);
		return this;
	}

	public BlockStateBuilder setMCVariantModel(String key, String model) {
		variants.getOrCreateMap(key).put("model", model);
		return this;
	}

	public BlockStateBuilder setMCVariantTexture(String key, String textureName, String texture) {
		variants.getOrCreateMap(key).getOrCreateMap("textures").put(textureName, texture);
		return this;
	}

	public BlockStateBuilder uvLock() {
		defaults.put("uvlock", true);
		return this;
	}

	public boolean hasVariant(String key, String value) {
		return variants.containsKey(key, Type.MAP) && variants.get(key).asMap().containsKey(value, Type.MAP);
	}

	public BlockStateBuilder inventory() {
		variants.getOrCreateList("inventory").add(new CMapping());
		return this;
	}

	public BlockStateBuilder normal() {
		variants.getOrCreateList("normal").add(new CMapping());
		return this;
	}
}
