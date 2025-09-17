package roj.plugins.minecraft.server.data;

import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.collect.Hasher;
import roj.compiler.plugins.asm.ASM;
import roj.config.NbtParser;
import roj.config.mapper.ObjectMapperFactory;
import roj.config.mapper.Optional;
import roj.io.ByteInputStream;
import roj.plugins.minecraft.server.MinecraftServer;
import roj.util.Helpers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/3/19 17:00
 */
public final class Block {
	public static final HashMap<String, Block> byName = new HashMap<>();
	public static final Registry<Block> REGISTRY = new Registry<>("block", 1024);
	public static final Registry<Block> STATE_ID = new Registry<>("block_state_id", 16384);
	private static HashSet<Object> _tmp;
	static {
		try (var in = MinecraftServer.INSTANCE.getResource("assets/Blocks_1.19.2.nbt")) {
			var conv = ObjectMapperFactory.SAFE.serializer(BlockInfo.class);
			var nbt = new NbtParser();

			Comparator<PropertyInfo> propertyCmp = (o1, o2) -> o1.name.compareTo(o2.name);
			var intern = new HashSet<>(Hasher.array(Object[].class));
			_tmp = new HashSet<>();

			var mdi = new ByteInputStream(in);
			int i = 0;
			while (true) {
				conv.reset();
				nbt.parse(mdi, 1, conv);
				if (!conv.finished()) break;

				BlockInfo info = conv.get();
				PropertyInfo[] prop = info.properties;
				boolean isNew = false;
				if (prop != null) {
					Arrays.sort(prop, propertyCmp);
					isNew = prop == (prop = (PropertyInfo[]) intern.intern(prop));
					if (isNew) {
						for (int j = 0; j < prop.length; j++) prop[j] = (PropertyInfo) _tmp.intern(prop[j]);
					}
				}

				Block block = new Block(info.name, info.color, info.opacity, info.full, prop);
				REGISTRY.register(block, i++);
				byName.put(info.name, block);

				int allDefault = STATE_ID.nextId();
				if (prop != null) {
					iterateStates(block, new HashMap<>(), prop, 0);
					STATE_ID.remove(allDefault);

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
			_tmp = null;
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}
	@SuppressWarnings("unchecked")
	private static void iterateStates(Block base, Map<String, String> map, PropertyInfo[] properties, int i) {
		PropertyInfo prop = properties[i++];
		for (String choice : prop.choices) {
			map.put(prop.name, choice);

			if (i == properties.length) {
				Map<String, String> exist = (Map<String, String>) _tmp.find(map);
				if (map == exist) {
					if (map.size() == 1) exist = Collections.singletonMap(prop.name, choice);
					else exist = ASM.TARGET_JAVA_VERSION >= 10 ? Map.copyOf(exist) : new HashMap<>(exist);
					_tmp.add(exist);
				}
				STATE_ID.register(new Block(base, exist), STATE_ID.nextId());
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
	static final class PropertyInfo {
		String name, type;
		String[] choices;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			PropertyInfo info = (PropertyInfo) o;

			if (!name.equals(info.name)) return false;
			if (!type.equals(info.type)) return false;
			// Probably incorrect - comparing Object[] arrays with Arrays.equals
			return Arrays.equals(choices, info.choices);
		}
		@Override
		public int hashCode() {
			int result = name.hashCode();
			result = 31 * result + type.hashCode();
			result = 31 * result + Arrays.hashCode(choices);
			return result;
		}
	}

	public static Block getBlock(String name) { return byName.getOrDefault(name, AIR); }
	public static Block getBlock(int id) {
		Block block = REGISTRY.getById(id);
		return block == null ? AIR : block;
	}

	public static final Block AIR = getBlock("minecraft:air");

	public final String identifier;
	public final int color;
	public final byte flags;
	private final PropertyInfo[] properties;
	private final Map<String, String> propertyValue;
	private Block prev, next;

	private Block(Block base, Map<String, String> properties) {
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
	private static Map<String, String> createDefaultState(PropertyInfo[] props) {
		if (props.length == 1) {
			var prop = props[0];
			return Collections.singletonMap(prop.name, prop.choices[0]);
		}

		HashMap<String, String> map = new HashMap<>(props.length);
		for (PropertyInfo p : props) map.put(p.name, p.choices[0]);

		return ASM.TARGET_JAVA_VERSION >= 10 ? Map.copyOf(map) : map;
	}

	public String getKey() { return identifier; }
	public Map<String, String> getPropertyValue() { return propertyValue; }

	public Block getPrev() { return prev; }
	public Block getNext() { return next; }

	@Override
	public String toString() { return propertyValue.isEmpty() ? identifier : identifier+propertyValue; }

	public boolean isCollible() {
		return true;
	}

	public boolean changesMovement() {
		return false;
	}
}