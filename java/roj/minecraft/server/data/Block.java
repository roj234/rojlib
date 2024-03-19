package roj.minecraft.server.data;

import roj.collect.MyHashMap;
import roj.config.NBTParser;
import roj.config.auto.Optional;
import roj.config.auto.Serializer;
import roj.config.auto.Serializers;
import roj.io.MyDataInputStream;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/3/19 0019 17:00
 */
public final class Block {
	private static final String[] INVERT = {"false", "true"};
	public static final MyHashMap<String, Block> byName = new MyHashMap<>();
	public static final Registry<Block> REGISTRY = new Registry<>("block");
	public static final Registry<Block> STATE_ID = new Registry<>("block_state_id");
	static {
		try (InputStream in = Block.class.getClassLoader().getResourceAsStream("META-INF/minecraft/Blocks_1.19.2.nbt")) {
			Serializer<BlockInfo> conv = Serializers.SAFE.serializer(BlockInfo.class);
			NBTParser par = new NBTParser();

			MyDataInputStream mdi = new MyDataInputStream(in);
			int i = 0;
			while (true) {
				conv.reset();
				par.parse(mdi, 1, conv);
				if (!conv.finished()) break;

				BlockInfo info = conv.get();
				Block block = new Block(info.name, info.color, info.opacity, info.full, info.properties);
				REGISTRY.register(block, i++);
				byName.put(info.name, block);

				int allDefault = STATE_ID.nextId();
				if (info.properties != null) {
					Arrays.sort(info.properties, (o1, o2) -> o1.name.compareTo(o2.name));
					iterateStates(block, new MyHashMap<>(), info.properties, 0);
					STATE_ID.REGISTRY.remove(allDefault);

					Block prev = block;
					for (int j = allDefault+1; j < STATE_ID.nextId(); j++) {
						Block next = STATE_ID.getById(j);

						prev.next = next;
						next.prev = prev;

						prev = next;
					}
				}
				STATE_ID.register(block, allDefault);
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}
	private static void iterateStates(Block base, Map<String, String> map, PropertyInfo[] properties, int i) {
		PropertyInfo prop = properties[i++];
		for (String choice : prop.choices) {
			map.put(prop.name, choice);

			if (i == properties.length) {
				STATE_ID.register(new Block(base, new MyHashMap<>(map)), STATE_ID.nextId());
			} else {
				iterateStates(base, map, properties, i);
			}
		}
	}
	@Optional
	static final class BlockInfo {
		String name;
		int color;
		PropertyInfo[] properties;
		// 0 or 1
		byte opacity, full;
	}
	@Optional
	static final class PropertyInfo {
		String name, type;
		String[] choices;
	}

	public static Block getBlock(String name) { return byName.getOrDefault(name, AIR); }
	public static Block getBlock(int id) { return REGISTRY.REGISTRY.getOrDefault(id, AIR); }

	public static final Block AIR = getBlock("minecraft:air");

	public final String identifier;
	public final int color;
	public final byte flags;
	private final PropertyInfo[] properties;
	private final Map<String, String> propertyValue;
	private Block prev, next;

	private Block(Block base, MyHashMap<String, String> properties) {
		this.identifier = base.identifier;
		this.color = base.color;
		this.flags = base.flags;
		this.properties = base.properties;
		this.propertyValue = properties;
	}
	private Block(String name, int color, byte opacity, byte full, PropertyInfo[] properties) {
		identifier = name;
		this.color = color;
		this.flags = (byte) ((opacity*1) | (full*2));
		this.properties = properties;
		this.propertyValue = properties == null ? Collections.emptyMap() : createDefaultState(properties);
	}
	private static Map<String, String> createDefaultState(PropertyInfo[] properties) {
		MyHashMap<String, String> map = new MyHashMap<>(properties.length);
		for (PropertyInfo p : properties) {
			if (p.type.equals("Boolean")) p.choices = INVERT;
			map.put(p.name, p.choices[0]);
		}
		return map;
	}

	public String getKey() { return identifier; }
	public Map<String, String> getPropertyValue() { return propertyValue; }

	public Block getNext() { return next; }
	public Block getPrev() { return prev; }

	@Override
	public String toString() { return propertyValue.isEmpty() ? identifier : identifier+propertyValue; }

	public boolean isCollible() {
		return true;
	}

	public boolean changesMovement() {
		return false;
	}
}