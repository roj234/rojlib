package roj.plugins.minecraft.server.data;

import roj.collect.XashMap;
import roj.plugins.minecraft.server.MinecraftServer;
import roj.text.TextReader;
import roj.util.Helpers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Roj234
 * @since 2024/3/20 8:01
 */
public final class Item {
	public static final XashMap<String, Item> byName = Helpers.cast(XashMap.noCreation(Item.class, "name").createSized(512));
	public static final Item[] byId = new Item[1153];
	static {
		try (TextReader in = TextReader.from(MinecraftServer.INSTANCE.getResource("assets/Items_1.19.2.txt"), StandardCharsets.UTF_8)) {
			int i = 0;
			while (true) {
				String item = in.readLine();
				if (item == null) break;
				Item val = new Item(item, i);
				byName.put(item, val);
				byId[i++] = val;
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}
	public static Item getItem(String name) { return byName.getOrDefault(name, AIR); }
	public static Item getItem(int i) { return byId[i]; }

	public static final Item AIR = getItem("minecraft:air");

	public final String name;
	public final int id;

	private Item next;

	private Item(String name, int i) {
		this.name = name;
		this.id = i;
	}

	public String getName() { return name; }
	public int getId() { return id; }
}