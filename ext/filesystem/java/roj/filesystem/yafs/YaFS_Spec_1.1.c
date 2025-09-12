YaFS���ݽṹ���壺
��Щ���ݽṹ���Ǳ����ڴ����ϵģ���һ��ֱ��ӳ�䵽�ڴ��С�

// �ļ�ϵͳ�����飬��ʽ���󼸺����ٸı䣬���һ������һ�ݸ���
struct YaFileSystem {// at cluster 0
	u32 crc32
	u32 magic = 'YaFS'
	u32 version = 2
	u32 clusters     // �ܼƴ�����
	u32 clusterShift // �ش�С ����12 = 4K ������512B(9)��ǰ�豸�Ŀ��С

	// firstXX��Ϊ�غ�
	u32 firstVolatileTable
	u32 firstFileTable
	u32 firstFreeIdTable
	u32 firstBitmapTable
	u32 firstTransactionsTable // ����Ϊ0, ����������־
	u32 firstBadClusterTable // ����Ϊ0, �������ڻ���
	u32 firstSecureTable // ����Ϊ0, ����Ȩ�޹���

	u64 formatTime // ��ʽ��ʱ��

	// ʣ�ಿ�ֱ������Ժ�汾ʹ��
}

// �ױ����ݱ�
// ���ڹ��غ�ж��ʱд��
// ������index��size�ȼ��е�һ�飬ͬʱλ�ÿɱ䣬ʵ��ĥ����⣬��ֹ�������
struct VolatileTable {
	u32 crc32
	u32 magic = 'DYNA'
	u32 version = 1
	u32 next     // ��һ��ͬ���Ĵغ�, ��0��ʾ������
	u32 clusters // ��ǰ��ռ�õĴ�����

	// ����VolatileTable, ֻ������(nextΪ0)��ֵ��Ч, ����clusters����Ϊ1

	u32 counter; // ��ǰλ�õ��ױ������Ѿ�д���˶��ٴΣ�ʵ�ֿ���ͨ�����������ʱ��λ

	bool isMounted; // ���Ϊ�棬��ô�ϴ�û������ж�أ���Ҫ���ϻָ�

	u32 usedClusters  // �Ѿ�ʹ�õĴ�����
	u32 usedNodeCount // ʹ��Node��
	u32 freeNodeCount // ����Node��

	// ���
	volatile char volumeName[256]

	// ÿ���ļ����ѷ����Node����
	// �ļ�����ֱ��ӳ�䵽�ṹ�壬Ϊ��ֱ�ӷ���&���ٸ�ʽ������Ҫ���ٴ�С
	// �䳤���飬ֱ��ĳ��Ϊ0ʱ��ֹ
	u32 nodeCount[...]

	// ÿ����ȫ��ʶ���ѷ���İ�ȫ��ʶ����
	// ���ɺͶ���ͬ��
	u32 sidCount[...]
}

// �ļ���
// ֧��ƫ�Ʒ��ʣ�����Ҫ��פ�ڴ棬���Լ򵥵���LRU���߸�Ŀ¼���泣��Node
// ��֧���޸�NodeId => �޸�ÿ��NAME�ṹ�ĸ�Ŀ¼
struct FileTable {
	u32 magic = 'FILE'
	u32 version = 3
	u32 next
	u32 clusters

	// С�ļ����Է���NodeС���ļ������ٿռ��˷ѣ��Ų��µ�ʱ���ƶ������
	enum NodeSize (u32) {
		512,
		4K
	} nodeSize

	// ÿ����ĵ�һ��Node���ᱻ��ͷռ��20�ֽ�
	// ��һ����ĵ�һ��Node��Ҳ����index 0������Ҳֻ���Ǹ�Ŀ¼
	// Node��ţ���8λ���ļ�����ţ���24λ������ļ����е��б�����
	// �ߺ���O(1)
	struct Node {
		bitenum NodeType (u8) {
			END(0), DELETED, FILE, FOLDER
		} type

		u64 transactionId // ���ύ������ID�����ڴ���ָ�

		u64 createTime
		u64 lastAccess
		u64 lastModified
		u32 secureId // ��ȫ��ʶ���������
		bitenum NodeFlag (u32) {
			READONLY, // �ڵ㼶ֻ�����ڲ�����Ȩ�޹���ʱ�ĺ󱸴�ʩ
			SPARSE,   // ֻ�о߱�������ʱ�����ݲ��ܲ������������Ǵ���
			LINK,     // ��������
		} flags

		// �����END��DELETED���ͣ���ô����Ľṹû������

		match type
		case FILE => FileInfo
		case FOLDER => FolderInfo

		Attribute attributes[...] // ����ǰ�ؽ���ȫ������
	} nodes[size] // ͬ���洢���ױ����ݱ���

	// �ļ�
	struct FileInfo {
		u16 linkCount // PATH��Ӳ���ӣ�����

		u64 allocatedSize // ռ�ÿռ�
		u64 size // ʵ�ʴ�С
	}

	// �ļ���
	struct FolderInfo {
		// ���ļ�������
		u32 childFileCount
		// ���ļ��е�����
		u32 childFolderCount
	}

	// �������ԣ���ΪNode�Ƕ����ģ��Ų���ʱ��Ӳ���Ӵ�����ʧ�ܣ�����Ҳ������ǰ�˻�ΪB+
	struct Attribute {
		enum Type (u8) {
			END(0), // ��������ǰNodeû�и���������
			NAME,
			DATA, EXTENT,
			TREE, ITREE,
			CHILDREN
		} type

		// �ļ����ƣ���ʾһ���ļ�����node�ֿ���ʵ��Ӳ����
		// FOLDER����ֻ�ܴ���һ��, FILE�ܴ��ڶ��
		struct NAME {
			bitenum PathFlag (u32) {
				READONLY,     // �ļ���ֻ��
				HIDDEN,       // ����
				COPY_ON_WRITE // �ڳ���д��ʱ����һ���µ�Node��������˱��
			} flags

			u32 parent    // ��Ŀ¼������
			u16 nameLength // �ļ�������

			// ʹ�ú��ʵı�����ٿռ�ռ��
			enum Charset (u8) {
				UTF_8,
				UTF_16LE,
				UNISHOX_COMPRESS
			} charset
			char name[nameLength]
		}

		// ������ݣ���û��SPARSE���ʱ��������������
		// FILE���ܴ������16��EXTENT�������Ƭ̫�࣬����Node�ռ䲻�㣬��תΪTREE
		struct EXTENT {
			u32 cluster // �����ϵĴغ�
			u32 offset  // �ڵ�ǰ�ļ��е�ƫ�ƣ��Դ�Ϊ��λ
			u16 length  // ռ�õĴ�����
		}

		// ���ļ��У�FOLDER����0-1������TREE����
		// ��ౣ��256����������Node�ռ䲻�㣬��תΪTREE
		struct CHILDREN {
				enum Sort (u8) {
					UNSORTED,      // û���κ���ʽ��˳��
					UNICODE,       // ʹ��Unicode����������
					LAST_MODIFIED, // ʹ���޸�ʱ���������
					LOCALIZED      // ʹ�ò���ϵͳ�ı��ػ���ʽ��������(��͸��)
				} sortType
				u8 length
				u32 childrenIndices[length]
		}

		// ֱ�����ݣ�FILE�д���0-1������EXTENT��TREE����
		struct DATA {
			u16 length
			u8 data[length] // ֱ�Ӵ����Node�ڵ�����
		}

		// �ڲ�B+������
		// �����ļ�, �洢�غ�
		// �����ļ���, �洢���ļ�
		struct ITREE {
			u16 length  // ��������
			u8 data[length] // �洢��Node�ڲ�������
		}

		// �ⲿB+������
		struct TREE {
			u32 cluster // ������ʼ�غ�
			u16 length  // ����ռ�õĴ�
		}
	}
}

// ������־
// ͨ����˵������ʽд�����, ����Ҫ��פ�ڴ�
// ���ļ�ϵͳ������ж��ʱ���й��ϻָ�
struct Transactions {
	u32 magic = 'LOGS'
	u32 version = 2
	u32 next     // ����������־����Ϊ0
	u32 clusters

	// ÿ��LogList���ȹ̶�Ϊ4KB
	// ��һ���ᱻ��ͷռ��16B
	// ����־д��ʱ����ص�ͷ����д�룬ʵ���豣֤��ͷд��ǰ�����ύ
	struct LogList {
		u32 crc32
		bool commit // ��һ��(���ǵ�ǰ)�����Ƿ����ύ (�ļ���ȸ���д�����)
		u64 transactionId // ��һ��LogItem������Id

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
			u64 timestamp // ���ڸ����޸�ʱ���

			match type
			case DELETE => struct {}
			case RENAME, CREATE, HARDLINK => {
				u16 nameLen
				char newName[nameLen]
			}
			case WRITE => {
				u32 cluster // �����ݿ�ʼ�Ĵغ�
				u32 offset  // ���ļ��е�ƫ�ƣ��Դ�Ϊ��λ
				u64 length  // д�����ݵ��ֽڳ���
			}
			case TRUNCATE => {
				u32 offset  // ���ļ��е�ƫ�ƣ��Դ�Ϊ��λ
				u64 length  // ��ĩβɾȥ���ֽڳ���
			}
			case SECURITY => {
				u32 newSId  // �޸�Ȩ��
			}
			case REINDEX => {
				u32 newNodeId // ��������ʱ�ĸ���NodeId����
			}
		}

		} [...] // ֱ����4KB��ĩβ��������ΪENDʱ��ֹ
	} [length]
}

// ��ȫ��ʶ��
// ֧��ƫ�Ʒ��ʣ�����Ҫ��פ�ڴ�
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
		u16 refcnt  // ���Ϊ0�������
	} [size] // �����������ױ����ݱ���
}

// ����ID��
// ���ڹ���ʱ��ȡ��ж��ʱд��
struct FreeId {
	u32 magic = 'FNID'
	u32 version = 1
	u32 next
	u32 clusters

	// ʹ��RLE������ɾ��ʱ��ʡ�ռ�
	struct {
		// ��Ϊ��Ŀ¼�����ܱ�ɾ����������Ҳ���������⺬��
		// ���nodeIdΪ0��������[count]��(����ĥ�����)
		// ���nodeId��count��Ϊ0����ô������֮ǰ���� (��������������δ����)

		u32 nodeId
		u16 count
	} freeId[...]
}

// ����λͼ
// �����ڹ��ϻָ�����������������Ѿ���������ʹ��λͼ����
struct BadCluster {
	u32 magic = 'BCST'
	u32 version = 1
	u32 next
	u32 clusters

	// RLE
	struct {
		u32 offset // ��ʼ�غ�, ���Ϊ0����������֮ǰ���� (��������������δ����)
		u16 count  // ����
	} blocks[...]
}

// ��ʹ��λͼ
// ���ڹ���ʱ��ȡ��ж��ʱд��
struct Bitmap {
	u32 magic = 'BTMP'
	u32 version = 1
	u32 next
	u32 clusters

	struct {
		// ��256��Ϊ��λ����ʹ��λͼ

		// ����YaFS��32λ�Ĵ���ţ�����startId����8λ���
		// ����startId��8λ�Ϳ���ӵ�����⺬��
		// Ϊ0x01ʱ��������֮ǰ���� (��������������δ����)
		// Ϊ0x02ʱ������startId[��24λ]��������(����ĥ�����)
		// Ϊ0x03ʱ��bitmap��Ϊclusters[8]���洢8�������Ĵ����
		// Ϊ0x04ʱ��bitmap��ΪRLE[4]���洢4��RLE����
		// ���ౣ��
		// ������Ӹ��Ӷȣ���0x02��0x03��0x04���Բ��ڱ����㷨��ʹ��

		u32 startId
		u32 bitmap[8]
	} blocks[...]
}
