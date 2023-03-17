package ilib.misc;

import roj.config.data.CMapping;
import roj.config.serial.Serializers;

import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author solo6975
 * @since 2022/1/15 16:32
 */
public class LoottableBuilder {
	public static final String LOOT_TYPE_EMPTY = "minecraft:empty";
	public static final String LOOT_TYPE_ENTITY = "minecraft:entity";
	public static final String LOOT_TYPE_BLOCK = "minecraft:block";
	public static final String LOOT_TYPE_CHEST = "minecraft:chest";
	public static final String LOOT_TYPE_FISHING = "minecraft:fishing";
	public static final String LOOT_TYPE_ADVANCEMENT_REWARD = "minecraft:advancement_reward";
	public static final String LOOT_TYPE_BARTER = "minecraft:barter";
	public static final String LOOT_TYPE_COMMAND = "minecraft:command";
	public static final String LOOT_TYPE_SELECTOR = "minecraft:selector";
	public static final String LOOT_TYPE_ADVANCEMENT_ENTITY = "minecraft:advancement_entity";
	public static final String LOOT_TYPE_GENERIC = "minecraft:generic";
	public static final String ENTRY_TYPE_ITEM = "minecraft:item";
	public static final String ENTRY_TYPE_TAG = "minecraft:tag";
	public static final String ENTRY_TYPE_LOOT_TABLE = "minecraft:loot_table";
	public static final String ENTRY_TYPE_GROUP = "minecraft:group";
	public static final String ENTRY_TYPE_ALTERNATIVES = "minecraft:alternatives";
	public static final String ENTRY_TYPE_SEQUENCE = "minecraft:sequence";
	public static final String ENTRY_TYPE_DYNAMIC = "minecraft:dynamic";
	public static final String ENTRY_TYPE_EMPTY = "minecraft:empty";

	public ResourceLocation id;
	public String lootType;
	public List<Pool> pools;

	public static class Pool {
		public int rolls;
		public List<Map<String, Object>> entries = new ArrayList<>(), conditions = new ArrayList<>();

		public Pool rolls(int rolls) {
			this.rolls = rolls;
			return this;
		}

		@SafeVarargs
		public final Pool entries(Map<String, Object>... entries) {
			this.entries = Arrays.asList(entries);
			return this;
		}

		public Pool addEntry(Map<String, Object> entry) {
			this.entries.add(entry);
			return this;
		}

		@SafeVarargs
		public final Pool conditions(Map<String, Object>... conditions) {
			this.conditions = Arrays.asList(conditions);
			return this;
		}

		public Pool addCondition(Map<String, Object> condition) {
			this.conditions.add(condition);
			return this;
		}
	}

	Serializers ser = new Serializers(Serializers.GENERATE);
	{
		ser.register(Pool.class, 0);
	}

	private void save() {
		//put(identifier.getNamespace() + ":assets/lootables/" + identifier.getPath() + ".json"), this);
	}

	public String genJson() {
		if (id == null) return "";
		CMapping json = new CMapping();

		if (lootType == null) {
			json.put("type", LOOT_TYPE_BLOCK);
			CMapping obj = new CMapping();
			obj.put("rolls", 1);

			CMapping entry = new CMapping();
			entry.put("type", ENTRY_TYPE_ITEM);
			entry.put("name", id.toString());
			obj.getOrCreateList("entries").add(entry);

			CMapping condition = new CMapping();
			condition.put("condition", "minecraft:survives_explosion");
			obj.getOrCreateList("conditions").add(condition);

			json.getOrCreateList("pools").add(obj);
			return json.toShortJSON();
		}

		json.put("type", lootType);
		json.put("pools", ser.serialize(pools));
		return json.toString();
	}
}