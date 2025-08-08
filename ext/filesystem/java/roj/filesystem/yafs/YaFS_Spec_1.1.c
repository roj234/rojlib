YaFS数据结构定义：
这些数据结构都是保存在磁盘上的，不一定直接映射到内存中。

// 文件系统描述块，格式化后几乎不再改变，最后一个簇有一份副本
struct YaFileSystem {// at cluster 0
	u32 crc32
	u32 magic = 'YaFS'
	u32 version = 2
	u32 clusters     // 总计簇数量
	u32 clusterShift // 簇大小 例如12 = 4K 不低于512B(9)或当前设备的块大小

	// firstXX均为簇号
	u32 firstVolatileTable
	u32 firstFileTable
	u32 firstFreeIdTable
	u32 firstBitmapTable
	u32 firstTransactionsTable // 可以为0, 禁用事务日志
	u32 firstBadClusterTable // 可以为0, 还不存在坏簇
	u32 firstSecureTable // 可以为0, 禁用权限管理

	u64 formatTime // 格式化时间

	// 剩余部分保留供以后版本使用
}

// 易变数据表
// 仅在挂载和卸载时写入
// 将各种index，size等集中到一块，同时位置可变，实现磨损均衡，防止单点故障
struct VolatileTable {
	u32 crc32
	u32 magic = 'DYNA'
	u32 version = 1
	u32 next     // 下一个同类表的簇号, 或0表示不存在
	u32 clusters // 当前表占用的簇数量

	// 对于VolatileTable, 只有最新(next为0)的值有效, 并且clusters必须为1

	u32 counter; // 当前位置的易变数据已经写入了多少次，实现可以通过这个决定何时移位

	bool isMounted; // 如果为真，那么上次没有正常卸载，需要故障恢复

	u32 usedClusters  // 已经使用的簇数量
	u32 usedNodeCount // 使用Node数
	u32 freeNodeCount // 空闲Node数

	// 卷标
	volatile char volumeName[256]

	// 每个文件表已分配的Node数量
	// 文件表能直接映射到结构体，为了直接访问&快速格式化，需要跟踪大小
	// 变长数组，直到某项为0时终止
	u32 nodeCount[...]

	// 每个安全标识表已分配的安全标识数量
	// 理由和定义同上
	u32 sidCount[...]
}

// 文件表
// 支持偏移访问，不需要常驻内存，可以简单的用LRU或者父目录缓存常用Node
// 还支持修改NodeId => 修改每个NAME结构的父目录
struct FileTable {
	u32 magic = 'FILE'
	u32 version = 3
	u32 next
	u32 clusters

	// 小文件可以放在Node小的文件表，减少空间浪费，放不下的时候移动到大表
	enum NodeSize (u32) {
		512,
		4K
	} nodeSize

	// 每个表的第一个Node，会被表头占用20字节
	// 第一个表的第一个Node，也就是index 0，必须也只能是根目录
	// Node序号：高8位是文件表序号，低24位是这个文件表中的列表索引
	// 芜湖，O(1)
	struct Node {
		bitenum NodeType (u8) {
			END(0), DELETED, FILE, FOLDER
		} type

		u64 transactionId // 已提交的事务ID，用于错误恢复

		u64 createTime
		u64 lastAccess
		u64 lastModified
		u32 secureId // 安全标识表项的索引
		bitenum NodeFlag (u32) {
			READONLY, // 节点级只读，在不开启权限管理时的后备措施
			SPARSE,   // 只有具备这个标记时，数据才能不连续，否则是错误
			LINK,     // 符号链接
		} flags

		// 如果是END或DELETED类型，那么这里的结构没有意义

		match type
		case FILE => FileInfo
		case FOLDER => FolderInfo

		Attribute attributes[...] // 到当前簇结束全是属性
	} nodes[size] // 同样存储在易变数据表中

	// 文件
	struct FileInfo {
		u16 linkCount // PATH（硬链接）数量

		u64 allocatedSize // 占用空间
		u64 size // 实际大小
	}

	// 文件夹
	struct FolderInfo {
		// 子文件的数量
		u32 childFileCount
		// 子文件夹的数量
		u32 childFolderCount
	}

	// 关于属性，因为Node是定长的，放不下时，硬链接创建会失败，索引也可能提前退化为B+
	struct Attribute {
		enum Type (u8) {
			END(0), // 结束，当前Node没有更多属性了
			NAME,
			DATA, EXTENT,
			TREE, ITREE,
			CHILDREN
		} type

		// 文件名称，表示一个文件，和node分开以实现硬链接
		// FOLDER类型只能存在一个, FILE能存在多个
		struct NAME {
			bitenum PathFlag (u32) {
				READONLY,     // 文件级只读
				HIDDEN,       // 隐藏
				COPY_ON_WRITE // 在尝试写入时构造一个新的Node，并清除此标记
			} flags

			u32 parent    // 父目录索引号
			u16 nameLength // 文件名长度

			// 使用合适的编码减少空间占用
			enum Charset (u8) {
				UTF_8,
				UTF_16LE,
				UNISHOX_COMPRESS
			} charset
			char name[nameLength]
		}

		// 间接数据，在没有SPARSE标记时，必须是连续的
		// FILE中能存在最多16个EXTENT，如果碎片太多，或者Node空间不足，就转为TREE
		struct EXTENT {
			u32 cluster // 磁盘上的簇号
			u32 offset  // 在当前文件中的偏移，以簇为单位
			u16 length  // 占用的簇数量
		}

		// 子文件夹，FOLDER存在0-1个，和TREE互斥
		// 最多保存256项，如果超过或Node空间不足，就转为TREE
		struct CHILDREN {
				enum Sort (u8) {
					UNSORTED,      // 没有任何显式的顺序
					UNICODE,       // 使用Unicode码点进行排序
					LAST_MODIFIED, // 使用修改时间进行排序
					LOCALIZED      // 使用操作系统的本地化方式进行排序(不透明)
				} sortType
				u8 length
				u32 childrenIndices[length]
		}

		// 直接数据，FILE中存在0-1个，和EXTENT、TREE互斥
		struct DATA {
			u16 length
			u8 data[length] // 直接存放在Node内的数据
		}

		// 内部B+树索引
		// 对于文件, 存储簇号
		// 对于文件夹, 存储子文件
		struct ITREE {
			u16 length  // 索引长度
			u8 data[length] // 存储在Node内部的索引
		}

		// 外部B+树索引
		struct TREE {
			u32 cluster // 索引起始簇号
			u16 length  // 索引占用的簇
		}
	}
}

// 事务日志
// 通常来说它会流式写入磁盘, 不需要常驻内存
// 在文件系统非正常卸载时进行故障恢复
struct Transactions {
	u32 magic = 'LOGS'
	u32 version = 2
	u32 next     // 对于事务日志必须为0
	u32 clusters

	// 每个LogList长度固定为4KB
	// 第一个会被表头占用16B
	// 当日志写满时，会回到头重新写入，实现需保证从头写入前必须提交
	struct LogList {
		u32 crc32
		bool commit // 上一个(不是当前)事务是否已提交 (文件表等更改写入磁盘)
		u64 transactionId // 第一个LogItem的事务Id

		struct LogItem {
			enum Type (u8) {
				END(0),
				DELETE,
				RENAME,
				CREATE,
				HARDLINK,
				WRITE,
				TRUNCATE,
				SECURITY,
				REINDEX
			} type

			u32 nodeId
			u64 timestamp // 用于更新修改时间等

			match type
			case DELETE => struct {}
			case RENAME, CREATE, HARDLINK => {
				u16 nameLen
				char newName[nameLen]
			}
			case WRITE => {
				u32 cluster // 新数据开始的簇号
				u32 offset  // 在文件中的偏移，以簇为单位
				u64 length  // 写入数据的字节长度
			}
			case TRUNCATE => {
				u32 offset  // 在文件中的偏移，以簇为单位
				u64 length  // 从末尾删去的字节长度
			}
			case SECURITY => {
				u32 newSId  // 修改权限
			}
			case REINDEX => {
				u32 newNodeId // 磁盘整理时的更新NodeId操作
			}
		}

		} [...] // 直到这4KB的末尾，或类型为END时终止
	} [length]
}

// 安全标识表
// 支持偏移访问，不需要常驻内存
struct SecureTable {
	u32 magic = 'SECU'
	u32 version = 1
	u32 next
	u32 clusters

	struct SID {
		u32 owner
		u16 ownerPerm
		u16 groupPerm
		u16 otherPerm
		u16 refcnt  // 如果为0代表空闲
	} [size] // 数量保存在易变数据表中
}

// 空闲ID表
// 仅在挂载时读取，卸载时写入
struct FreeId {
	u32 magic = 'FNID'
	u32 version = 1
	u32 next
	u32 clusters

	// 使用RLE在批量删除时节省空间
	struct {
		// 因为根目录不可能被删除，所以这也可以有特殊含义
		// 如果nodeId为0，则跳过[count]项(用于磨损均衡)
		// 如果nodeId和count均为0，那么在这项之前结束 (这项和其后的所有项都未分配)

		u32 nodeId
		u16 count
	} freeId[...]
}

// 坏簇位图
// 仅用于故障恢复，正常这里的数据已经保存在已使用位图表中
struct BadCluster {
	u32 magic = 'BCST'
	u32 version = 1
	u32 next
	u32 clusters

	// RLE
	struct {
		u32 offset // 起始簇号, 如果为0代表在这项之前结束 (这项和其后的所有项都未分配)
		u16 count  // 长度
	} blocks[...]
}

// 已使用位图
// 仅在挂载时读取，卸载时写入
struct Bitmap {
	u32 magic = 'BTMP'
	u32 version = 1
	u32 next
	u32 clusters

	struct {
		// 以256簇为单位的已使用位图

		// 由于YaFS是32位的簇序号，所以startId右移8位存放
		// 这样startId高8位就可以拥有特殊含义
		// 为0x01时，在这项之前结束 (这项和其后的所有项都未分配)
		// 为0x02时，跳过startId[低24位]个空闲项(用于磨损均衡)
		// 为0x03时，bitmap变为clusters[8]，存储8个单独的簇序号
		// 为0x04时，bitmap变为RLE[4]，存储4个RLE编码
		// 其余保留
		// 这会增加复杂度，但0x02，0x03和0x04可以不在编码算法中使用

		u32 startId
		u32 bitmap[8]
	} blocks[...]
}
