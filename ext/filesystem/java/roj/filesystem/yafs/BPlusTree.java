package roj.filesystem.yafs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2025/07/13 02:11
 */
public class BPlusTree {
	private static final int BLOCK_SIZE = 512; // 磁盘块大小（字节）
	private static final int ORDER = 4;        // B+树的阶（每个节点最多ORDER-1个键）
	private final RandomAccessFile file;
	private long rootAddress = 0;              // 根节点在文件中的位置（字节偏移）

	// 节点类型标记
	private static final byte LEAF_NODE = 0;
	private static final byte INTERNAL_NODE = 1;

	// 元数据块结构（文件开头）
	private static final int META_BLOCK_SIZE = 8; // long类型（8字节）存储根节点地址

	public BPlusTree(String filename) throws IOException {
		file = new RandomAccessFile(filename, "rw");
		if (file.length() == 0) {
			// 初始化新文件：创建空叶节点作为根
			rootAddress = META_BLOCK_SIZE;
			LeafNode root = new LeafNode();
			writeNode(root, rootAddress);
			writeRootAddress();
		} else {
			// 读取现有根节点地址
			file.seek(0);
			rootAddress = file.readLong();
		}
	}

	// 写入根节点地址到元数据块
	private void writeRootAddress() throws IOException {
		file.seek(0);
		file.writeLong(rootAddress);
	}

	// 节点抽象类
	private abstract static class Node {
		int keyCount = 0;
		int[] keys = new int[ORDER];
	}

	// 叶节点类
	private class LeafNode extends Node {
		long next = -1;       // 下一个叶节点的文件地址
		int[] values = new int[ORDER]; // 与键关联的值

		// 序列化叶节点
		byte[] serialize() {
			byte[] data = new byte[BLOCK_SIZE];
			int pos = 0;

			// 节点类型标记 (1字节)
			data[pos++] = LEAF_NODE;

			// 键数量 (4字节)
			System.arraycopy(intToBytes(keyCount), 0, data, pos, 4);
			pos += 4;

			// 键数组 (每个键4字节)
			for (int i = 0; i < keyCount; i++) {
				System.arraycopy(intToBytes(keys[i]), 0, data, pos, 4);
				pos += 4;
			}

			// 值数组 (每个值4字节)
			for (int i = 0; i < keyCount; i++) {
				System.arraycopy(intToBytes(values[i]), 0, data, pos, 4);
				pos += 4;
			}

			// 下一个叶节点地址 (8字节)
			System.arraycopy(longToBytes(next), 0, data, pos, 8);
			return data;
		}

		// 从字节数组反序列化
		void deserialize(byte[] data) {
			int pos = 1; // 跳过节点类型标记

			// 读取键数量
			keyCount = bytesToInt(data, pos);
			pos += 4;

			// 读取键
			for (int i = 0; i < keyCount; i++) {
				keys[i] = bytesToInt(data, pos);
				pos += 4;
			}

			// 读取值
			for (int i = 0; i < keyCount; i++) {
				values[i] = bytesToInt(data, pos);
				pos += 4;
			}

			// 读取下一个叶节点地址
			next = bytesToLong(data, pos);
		}
	}

	// 内部节点类
	private class InternalNode extends Node {
		long[] children = new long[ORDER]; // 子节点文件地址

		// 序列化内部节点
		byte[] serialize() {
			byte[] data = new byte[BLOCK_SIZE];
			int pos = 0;

			data[pos++] = INTERNAL_NODE;
			System.arraycopy(intToBytes(keyCount), 0, data, pos, 4);
			pos += 4;

			// 键数组
			for (int i = 0; i < keyCount; i++) {
				System.arraycopy(intToBytes(keys[i]), 0, data, pos, 4);
				pos += 4;
			}

			// 子节点地址（比键多一个）
			for (int i = 0; i <= keyCount; i++) {
				System.arraycopy(longToBytes(children[i]), 0, data, pos, 8);
				pos += 8;
			}
			return data;
		}

		// 反序列化内部节点
		void deserialize(byte[] data) {
			int pos = 1; // 跳过节点类型
			keyCount = bytesToInt(data, pos);
			pos += 4;

			for (int i = 0; i < keyCount; i++) {
				keys[i] = bytesToInt(data, pos);
				pos += 4;
			}

			for (int i = 0; i <= keyCount; i++) {
				children[i] = bytesToLong(data, pos);
				pos += 8;
			}
		}
	}

	// 辅助函数：int转字节数组
	private static byte[] intToBytes(int value) {
		return new byte[] {
				(byte)(value >>> 24),
				(byte)(value >>> 16),
				(byte)(value >>> 8),
				(byte)value
		};
	}

	// 辅助函数：字节数组转int
	private static int bytesToInt(byte[] bytes, int offset) {
		return ((bytes[offset] & 0xFF) << 24) |
				((bytes[offset+1] & 0xFF) << 16) |
				((bytes[offset+2] & 0xFF) << 8) |
				(bytes[offset+3] & 0xFF);
	}

	// 辅助函数：long转字节数组
	private static byte[] longToBytes(long value) {
		byte[] result = new byte[8];
		for (int i = 7; i >= 0; i--) {
			result[i] = (byte)(value & 0xFF);
			value >>= 8;
		}
		return result;
	}

	// 辅助函数：字节数组转long
	private static long bytesToLong(byte[] bytes, int offset) {
		long value = 0;
		for (int i = 0; i < 8; i++) {
			value = (value << 8) | (bytes[offset+i] & 0xFF);
		}
		return value;
	}

	// 从文件读取节点
	private Node readNode(long address) throws IOException {
		file.seek(address);
		byte[] data = new byte[BLOCK_SIZE];
		file.readFully(data);

		if (data[0] == LEAF_NODE) {
			LeafNode node = new LeafNode();
			node.deserialize(data);
			return node;
		} else {
			InternalNode node = new InternalNode();
			node.deserialize(data);
			return node;
		}
	}

	// 写入节点到文件
	private void writeNode(Node node, long address) throws IOException {
		file.seek(address);
		if (node instanceof LeafNode) {
			file.write(((LeafNode)node).serialize());
		} else {
			file.write(((InternalNode)node).serialize());
		}
	}

	// 插入键值对
	public void insert(int key, int value) throws IOException {
		InsertResult result = insert(rootAddress, key, value);
		if (result != null) {
			// 创建新根节点
			InternalNode newRoot = new InternalNode();
			newRoot.keyCount = 1;
			newRoot.keys[0] = result.splitKey;
			newRoot.children[0] = rootAddress;
			newRoot.children[1] = result.newChildAddress;

			// 更新根节点地址
			rootAddress = file.length();
			writeNode(newRoot, rootAddress);
			writeRootAddress();
		}
	}

	// 递归插入辅助方法
	private InsertResult insert(long nodeAddress, int key, int value) throws IOException {
		Node node = readNode(nodeAddress);

		if (node instanceof LeafNode) {
			LeafNode leaf = (LeafNode) node;
			return insertIntoLeaf(leaf, nodeAddress, key, value);
		} else {
			InternalNode internal = (InternalNode) node;
			int idx = Arrays.binarySearch(internal.keys, 0, internal.keyCount, key);
			if (idx < 0) idx = -idx - 1; // 转换为插入位置

			// 递归插入子节点
			InsertResult result = insert(internal.children[idx], key, value);

			if (result != null) {
				// 处理分裂结果
				if (internal.keyCount < ORDER - 1) {
					// 内部节点有空间
					insertIntoInternal(internal, idx, result.splitKey, result.newChildAddress);
					writeNode(internal, nodeAddress);
					return null;
				} else {
					// 内部节点分裂
					return splitInternalNode(internal, nodeAddress, idx, result);
				}
			}
			return null;
		}
	}

	// 插入到叶节点
	private InsertResult insertIntoLeaf(LeafNode leaf, long address, int key, int value) {
		int idx = Arrays.binarySearch(leaf.keys, 0, leaf.keyCount, key);
		if (idx >= 0) {
			// 键已存在，更新值
			leaf.values[idx] = value;
			return null;
		}

		idx = -idx - 1; // 插入位置

		if (leaf.keyCount < ORDER - 1) {
			// 节点有空间
			System.arraycopy(leaf.keys, idx, leaf.keys, idx+1, leaf.keyCount - idx);
			System.arraycopy(leaf.values, idx, leaf.values, idx+1, leaf.keyCount - idx);
			leaf.keys[idx] = key;
			leaf.values[idx] = value;
			leaf.keyCount++;
			return null;
		} else {
			// 叶节点分裂
			return splitLeafNode(leaf, address, key, value);
		}
	}

	// 分裂叶节点
	private InsertResult splitLeafNode(LeafNode leaf, long address, int key, int value) {
		// 创建新叶节点
		LeafNode newLeaf = new LeafNode();
		int splitPoint = (ORDER + 1) / 2;
		int totalItems = leaf.keyCount + 1;

		// 临时存储所有键值（包括新键）
		int[] tempKeys = Arrays.copyOf(leaf.keys, totalItems);
		int[] tempValues = Arrays.copyOf(leaf.values, totalItems);

		// 插入新键值
		int insertIdx = Arrays.binarySearch(tempKeys, 0, leaf.keyCount, key);
		if (insertIdx < 0) insertIdx = -insertIdx - 1;
		System.arraycopy(tempKeys, insertIdx, tempKeys, insertIdx+1, leaf.keyCount - insertIdx);
		System.arraycopy(tempValues, insertIdx, tempValues, insertIdx+1, leaf.keyCount - insertIdx);
		tempKeys[insertIdx] = key;
		tempValues[insertIdx] = value;

		// 分配键值
		leaf.keyCount = splitPoint;
		newLeaf.keyCount = totalItems - splitPoint;

		System.arraycopy(tempKeys, 0, leaf.keys, 0, splitPoint);
		System.arraycopy(tempValues, 0, leaf.values, 0, splitPoint);
		System.arraycopy(tempKeys, splitPoint, newLeaf.keys, 0, newLeaf.keyCount);
		System.arraycopy(tempValues, splitPoint, newLeaf.values, 0, newLeaf.keyCount);

		// 更新链表指针
		newLeaf.next = leaf.next;
		try {
			leaf.next = file.length(); // 新节点地址将在写入时确定
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// 创建分裂结果
		InsertResult result = new InsertResult();
		result.splitKey = newLeaf.keys[0]; // 新节点的第一个键作为分裂键
		result.newChildAddress = leaf.next; // 新节点地址

		return result;
	}

	// 处理内部节点插入
	private void insertIntoInternal(InternalNode node, int idx, int splitKey, long newChildAddress) {
		// 移动键和子节点指针
		System.arraycopy(node.keys, idx, node.keys, idx+1, node.keyCount - idx);
		System.arraycopy(node.children, idx+1, node.children, idx+2, node.keyCount - idx);

		// 插入新键和指针
		node.keys[idx] = splitKey;
		node.children[idx+1] = newChildAddress;
		node.keyCount++;
	}

	// 分裂内部节点
	private InsertResult splitInternalNode(InternalNode node, long address, int idx, InsertResult childResult) {
		// 创建临时数组容纳所有键和指针
		int totalKeys = node.keyCount + 1;
		int[] tempKeys = new int[totalKeys];
		long[] tempChildren = new long[totalKeys + 1];

		// 复制现有数据
		System.arraycopy(node.keys, 0, tempKeys, 0, idx);
		System.arraycopy(node.children, 0, tempChildren, 0, idx+1);

		// 插入新键和指针
		tempKeys[idx] = childResult.splitKey;
		tempChildren[idx+1] = childResult.newChildAddress;

		System.arraycopy(node.keys, idx, tempKeys, idx+1, node.keyCount - idx);
		System.arraycopy(node.children, idx+1, tempChildren, idx+2, node.keyCount - idx);

		// 确定分裂点
		int splitPoint = totalKeys / 2;
		int promoteKey = tempKeys[splitPoint];

		// 创建新内部节点
		InternalNode newNode = new InternalNode();
		newNode.keyCount = totalKeys - splitPoint - 1;

		// 分配键和指针
		node.keyCount = splitPoint;
		System.arraycopy(tempKeys, splitPoint+1, newNode.keys, 0, newNode.keyCount);
		System.arraycopy(tempChildren, splitPoint+1, newNode.children, 0, newNode.keyCount + 1);

		// 创建结果
		InsertResult result = new InsertResult();
		result.splitKey = promoteKey;
		try {
			result.newChildAddress = file.length(); // 新节点地址
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return result;
	}

	// 查找键值
	public Integer search(int key) throws IOException {
		return search(rootAddress, key);
	}

	private Integer search(long nodeAddress, int key) throws IOException {
		Node node = readNode(nodeAddress);

		if (node instanceof LeafNode) {
			LeafNode leaf = (LeafNode) node;
			int idx = Arrays.binarySearch(leaf.keys, 0, leaf.keyCount, key);
			return idx >= 0 ? leaf.values[idx] : null;
		} else {
			InternalNode internal = (InternalNode) node;
			int idx = Arrays.binarySearch(internal.keys, 0, internal.keyCount, key);
			if (idx < 0) idx = -idx - 1;
			return search(internal.children[idx], key);
		}
	}

	// 范围查询 [start, end]
	public List<Integer> rangeQuery(int start, int end) throws IOException {
		List<Integer> results = new ArrayList<>();
		long leafAddress = findLeaf(rootAddress, start);

		while (leafAddress != -1) {
			LeafNode leaf = (LeafNode) readNode(leafAddress);
			for (int i = 0; i < leaf.keyCount; i++) {
				if (leaf.keys[i] > end) return results;
				if (leaf.keys[i] >= start) {
					results.add(leaf.values[i]);
				}
			}
			leafAddress = leaf.next;
		}
		return results;
	}

	// 查找包含某个键的叶节点
	private long findLeaf(long nodeAddress, int key) throws IOException {
		Node node = readNode(nodeAddress);
		if (node instanceof LeafNode) {
			return nodeAddress;
		} else {
			InternalNode internal = (InternalNode) node;
			int idx = Arrays.binarySearch(internal.keys, 0, internal.keyCount, key);
			if (idx < 0) idx = -idx - 1;
			return findLeaf(internal.children[idx], key);
		}
	}

	// 关闭文件
	public void close() throws IOException {
		file.close();
	}

	// 插入结果辅助类
	private static class InsertResult {
		int splitKey;          // 分裂后提升的键
		long newChildAddress;  // 新子节点地址
	}

	public static void main(String[] args) throws IOException {
		BPlusTree tree = new BPlusTree("bplustree.dat");

		// 插入示例数据
		tree.insert(10, 100);
		tree.insert(20, 200);
		tree.insert(5, 50);
		tree.insert(15, 150);
		tree.insert(25, 250);
		tree.insert(30, 300);

		// 查找测试
		System.out.println("Search 15: " + tree.search(15)); // 150
		System.out.println("Search 30: " + tree.search(30)); // 300
		System.out.println("Search 99: " + tree.search(99)); // null

		// 范围查询测试
		System.out.println("Range [12, 28]: " + tree.rangeQuery(12, 28)); // [150, 200, 250]

		tree.close();
	}
}