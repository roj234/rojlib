package roj.plugins.minecraft.server.data;

import roj.collect.Int2IntMap;
import roj.collect.SimpleList;

import java.util.List;
import java.util.Random;

/**
 * @author Roj234
 * @since 2024/3/19 17:06
 */
public class BlockSet {
	private final Int2IntMap groupId = new Int2IntMap();
	private final List<List<Block>> blockList = new SimpleList<>();

	public int[] createState(Random rnd) {
		int[] array = new int[blockList.size()];
		for (int i = 0; i < array.length; i++) array[i] = rnd.nextInt(blockList.get(i).size());
		return array;
	}
	public Block getNextState(int[] state, int groupId, Random rnd) {
		List<Block> blocks = blockList.get(groupId);
		return blocks.get((state[groupId] += rnd.nextInt(blocks.size())) % blocks.size());
	}

	public void addBlock(String name) { addBlock(Block.getBlock(name)); }
	public void addBlock(Block block) {
		Integer i = groupId.get(block.color);
		if (i == null) {
			groupId.put(block.color, blockList.size());
			blockList.add(SimpleList.asModifiableList(block));
		} else {
			blockList.get(i).add(block);
		}
	}
	public void addBlock(String name, boolean allState) {
		Block block = Block.getBlock(name);
		while (block.getNext() != null) {
			addBlock(block);
			block = block.getNext();
		}
		addBlock(block);
	}
}