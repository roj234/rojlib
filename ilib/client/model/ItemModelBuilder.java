package ilib.client.model;

import roj.config.data.CMapping;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class ItemModelBuilder {
	public final CMapping jsonData;

	public ItemModelBuilder() {
		jsonData = new CMapping();
		jsonData.put("parent", "builtin/generated");
	}

	public String build() {
		return jsonData.toShortJSON();
	}

	public ItemModelBuilder setModel(String model) {
		jsonData.put("model", model);
		return this;
	}

	public ItemModelBuilder parent(String parent) {
		jsonData.put("parent", parent);
		return this;
	}

	public ItemModelBuilder setTexture(String texture) {
		return setTexture("all", texture);
	}

	public ItemModelBuilder setTexture(String textureName, String texture) {
		jsonData.getOrCreateMap("textures").put(textureName, texture);
		return this;
	}

	private CMapping newOverride() {
		CMapping mapping = new CMapping();
		jsonData.getOrCreateList("overrides").add(mapping);
		return mapping;
	}

	public ItemModelBuilder setOverrides(CMapping predicate, String model) {
		CMapping map = newOverride();
		map.put("predicate", predicate);
		map.put("model", model);
		return this;
	}

	public ItemModelBuilder setOverrides(String predicate, float value, String model) {
		CMapping map = new CMapping();
		map.put(predicate, value);
		return setOverrides(map, model);
	}
}
