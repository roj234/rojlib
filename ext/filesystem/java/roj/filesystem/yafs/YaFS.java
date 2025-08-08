package roj.filesystem.yafs;

import roj.collect.IntList;
import roj.collect.IntMap;
import roj.collect.LFUCache;
import roj.crypt.CRC32;
import roj.util.Bitmap;
import roj.math.MathUtils;
import roj.filesystem.BlockDevice;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * 🥈 Yet another File System (亚文件系统) <br>
 * 你的倒数第②个文件系统
 * @author Roj234
 * @since 2025/07/12 23:33
 */
public class YaFS {
	private static final int NAME_MAX = 1024;

	private final BlockDevice device;
	private final DynByteBuf buffer = DynByteBuf.allocateDirect();
	private int clusters;
	private int clusterSize;

	private Table SecureTable;
	private Table FreeIdTable;
	private Table BadClusterTable;
	private Table BitmapTable;
	private Table FileTable;
	private Table TransactionLog;
	private Bitmap bitmap;

	private IntMap<Node> openedNodes = new IntMap<>();
	private LFUCache<Integer, Node> nodeCache = new LFUCache<>(8192);
	private LFUCache<Integer, Long> blockCache = new LFUCache<>(512);

	public YaFS(BlockDevice device) {
		this.device = device;
		this.buffer.ensureCapacity(4096);
	}

	private DynByteBuf getBuffer() {
		var buf = buffer;
		buf.clear();
		return buf;
	}

	public void format(int clusterShift) throws IOException {
		int blockSize = device.getBlockSize();
		int clusterSize = 1 << clusterShift;
		if (clusterSize < blockSize) throw new IOException("cluster size must larger than block size");

		long deviceSize = device.getCapacity();
		long clusters = deviceSize >> clusterShift;
		if (clusters > 0xFFFFFFFFL) throw new IOException("too many clusters");
		if (clusters < 1024) throw new IOException("too less clusters");

		int clustersInt = (int)clusters;
		int endOffset = clustersInt;

		int backupTableOffset = --endOffset;

		int transactionTableClusters = (int) MathUtils.clamp(deviceSize >>> 10, 4096, 4194304) >> clusterShift;
		int transactionTableOffset = endOffset -= transactionTableClusters;

		int bitmapTableClusters = Math.max(1<<clusterShift, (int) ((deviceSize >> 3) / 10)) >> clusterShift;
		int bitmapTableOffset = endOffset -= bitmapTableClusters;

		int freeIdTableClusters = 16;
		int freeIdTableOffset = endOffset -= freeIdTableClusters;

		int fileTableOffset = clustersInt * 7 / 10;
		int fileTableClusters = endOffset - fileTableOffset;
		if (fileTableClusters < 0) throw new IOException("no enough space");

		// 初始化YaFileSystem结构体
		var buf = getBuffer();
		buf.putInt(0)
			.putAscii("YaFS")
			.putInt(10000)
			.putInt(clustersInt)
			.putInt(clusterShift)

			.putInt(backupTableOffset) // backup
			.putInt(fileTableOffset) // $Files
			.putInt(bitmapTableOffset) // $Bitmap
			.putInt(freeIdTableOffset) // $FreeId
			.putInt(transactionTableOffset) // $Transaction
			.putInt(0) // $BadCluster
			.putInt(1) // $Secure

			.putLong(System.currentTimeMillis()); // formatTime

		var volumeLabel = "新加卷喵";
		int i = DynByteBuf.byteCountUTF8(volumeLabel);
		buf.putUTFData(volumeLabel).putZero(256 - i);

		buf.putInt(0, CRC32.crc32(buf, 4, buf.readableBytes()-4));
		device.write(0, clusterSize, buf);

		buf.putInt(20, 0).putInt(0, CRC32.crc32(buf, 4, buf.readableBytes()-4));
		device.write(deviceSize - clusterSize, clusterSize, buf);

		initPT(buf, 1, 1, clusterSize, "SECU");
		initPT(buf, bitmapTableOffset, bitmapTableClusters, clusterSize, "BTMP");
		initPT(buf, transactionTableOffset, transactionTableClusters, clusterSize, "LOGS");
		initPT(buf, freeIdTableOffset, freeIdTableClusters, clusterSize, "FNID");
		initPT(buf, fileTableOffset, fileTableClusters, clusterSize, "FILE");
	}
	private void initPT(DynByteBuf buf, int clusterIndex, int clusters, int clusterSize, String fourcc) throws IOException {
		buf.clear();
		buf.putAscii(fourcc).putInt(10000).putInt(0).putInt(clusters);
		device.write(clusterIndex, clusterSize, buf);
	}

	private static final long UNIT = 1+Bitmap.UNIT_MASK;
	public void mount() throws IOException {
		var buf = getBuffer();
		device.read(0, device.getBlockSize(), buf);
		int crc32 = buf.readInt();
		if (!buf.readAscii(4).equals("YaFS")) throw new IOException("magic error");
		if (buf.readInt() != 10000) throw new IOException("version error");
		clusters = buf.readInt();
		clusterSize = 1 << buf.readInt();

		int backupCluster = buf.readInt();
		int fileTable = buf.readInt();
		int freeId = buf.readInt();
		int bitmap = buf.readInt();
		int transaction = buf.readInt();
		int badCluster = buf.readInt();
		int secure = buf.readInt();

		buf.rIndex += 8;
		buf.rIndex += 256;

		if (crc32 != CRC32.crc32(buf, 4, buf.rIndex - 4)) throw new IOException("checksum error");

		this.bitmap = Bitmap.create(clusters);
		this.bitmap.alloc(0, UNIT);

		this.FileTable = new Table(fileTable, "FILE");
		this.FreeIdTable = new Table(freeId, "FNID");

		this.BitmapTable = new Table(bitmap, "BTMP");
		this.BadClusterTable = badCluster == 0 ? null : new Table(badCluster, "BCST");

		this.TransactionLog = transaction == 0 ? null : new Table(transaction, "LOGS");
		this.SecureTable = secure == 0 ? null : new Table(secure, "SECU");

		this.BadClusterTable.iter();
	}

	public void recover() throws IOException {
		// 首先提交事务日志中的修改
		// 然后根据文件表、坏簇表的状态重建空闲节点，位图，和安全描述表
	}

	public void unmount() throws IOException {
		// 关闭所有打开的文件，并提交事务

	}

	public static class QueryMetadata {
		// INPUT
		public String pathname;
		public boolean lock;

		public int userId;
		public int groupId;

		public int permission;

		// OUTPUT
		public int mode;
		public int nodeId;
		public int linkCount;

		public long lastModified;
		public long lastAccessed;
		public long creation;
		public int flags;

		public long fileSize;
		public long allocationSize;

		public long childFileCount;
		public long childFolderCount;
	}
	public int queryMetadata(QueryMetadata open) throws IOException {
		return 0;
	}

	public static class ListDirectoryOptions {
		int sortMode;
		boolean showHidden;
	}
	public static class ListDirectory {

	}
	public ListDirectory newLister(int nodeId, ListDirectoryOptions options) throws IOException {
		return null;
	}

	public int listNextChildrenFD(ListDirectory context) throws IOException {
		return 0;
	}

	public long readFD(int fd, long offset, DynByteBuf data) throws IOException {
		return 0;
	}
	public long writeFD(int fd, long offset, DynByteBuf data) throws IOException {
		return 0;
	}

	public void closeFD(int fd) throws IOException {

	}

	class Table {
		final IntList tables = new IntList();

		public Table(int cluster, String magic) throws IOException {
			while (cluster != 0) {
				DynByteBuf buf = getBuffer();
				device.read((long) cluster * clusterSize, clusterSize, buf);

				if (!buf.readAscii(4).equals(magic)) throw new IOException("magic error");
				if (buf.readInt() != 10000) throw new IOException("version error");
				int nextCluster = buf.readInt();
				int length = buf.readInt();

				tables.add(cluster);
				tables.add(length);

				bitmap.alloc(cluster, cluster+UNIT*length);

				cluster = nextCluster;
			}
		}

		public void iter() {

		}

		public void add() {

		}
	}
}
