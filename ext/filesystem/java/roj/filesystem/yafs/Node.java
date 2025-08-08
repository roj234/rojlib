package roj.filesystem.yafs;

import roj.collect.Multimap;

/**
 * @author Roj234
 * @since 2025/07/12 23:37
 */
class Node {
	byte type;

	int nodeId;
	long transactionId;

	long createTime;
	long lastAccess;
	long lastModified;
	int secureId;
	int flags;

	//region file
	short linkCount;

	long allocatedSize;
	long size;
	//endregion
	//region file
	int childFileCount;
	int childFolderCount;
	//endregion

	Multimap<Integer, Attribute> attributes;

	static class Attribute {

	}


}
