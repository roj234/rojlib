package ilib.tile;

import ilib.ImpLib;
import ilib.api.Syncable;
import ilib.net.IMessage;
import ilib.net.MyChannel;
import ilib.net.NetworkHelper;
import ilib.net.packet.MsgSyncField;
import ilib.net.packet.MsgSyncFields;
import ilib.util.BlockHelper;
import roj.collect.SimpleList;
import roj.util.EmptyArrays;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;

/**
 * @author Roj233
 * @since 2022/4/15 11:12
 */
public class FieldSyncer {
	public static final byte CLIENT = 2, SERVER = 1, GUI = 0;

	private SimpleList<EntityPlayerMP> players;

	private final TileEntity owner;

	private int[] fields, prevFields;
	private int fieldCount;

	private long client, server, gui;
	private int counts;

	public <T extends TileEntity & Syncable> FieldSyncer(T sync) {
		this(sync, sync.getFieldCount1());
	}

	public <T extends TileEntity & Syncable> FieldSyncer(T sync, int fieldAmount) {
		this.owner = sync;
		this.fields = fieldAmount == 0 ? EmptyArrays.INTS : new int[fieldAmount];
		this.prevFields = fieldAmount == 0 ? EmptyArrays.INTS : new int[fieldAmount];
	}

	public void openGui(EntityPlayerMP player) {
		if (players == null) return;
		if (!players.contains(player)) {
			// initial capacity=1
			players.ensureCapacity(1);
			players.add(player);

			if (fieldCount == 0) {
				BlockHelper.sendTileUpdate(owner);
			} else {
				MsgSyncFields pkt = new MsgSyncFields(this, owner.getPos(), GUI);
				NetworkHelper.IL.sendTo(pkt, player);
			}
		}
	}

	public void closeGui(EntityPlayerMP player) {
		if (players != null) players.remove(player);
	}

	public void update() {
		if (!owner.getWorld().isRemote && players == null) {
			players = new SimpleList<>();
			return;
		}

		long modified = 0;
		int[] fields = this.fields;
		int[] prevFields = this.prevFields;

		long mask = owner.getWorld().isRemote ? client : server | gui;
		((Syncable) owner).getFieldInfo(fields);
		for (int i = 0; i < fieldCount; i++) {
			if (((1L << i) & mask) != 0 && fields[i] != prevFields[i]) {
				prevFields[i] = fields[i];
				modified |= 1L << i;
			}
		}
		if (owner.getWorld().isRemote && players == null) {
			players = new SimpleList<>();
			return;
		}
		if (modified == 0) return;

		MyChannel ch = NetworkHelper.IL;
		BlockPos pos = owner.getPos();
		int idx = getIdx(modified);
		if (owner.getWorld().isRemote) {
			if ((modified & client) != 0) {
				if (idx >= 0) {
					MsgSyncField pkt = new MsgSyncField(idx, prevFields[idx]);
					pkt.pos = pos;
					ch.sendToServer(pkt);
				} else {
					ch.sendToServer(new MsgSyncFields(this, pos, CLIENT));
				}
			}
		} else {
			if ((modified & server) != 0) {
				IMessage p;
				if (idx >= 0) {
					MsgSyncField pkt = new MsgSyncField(idx, prevFields[idx]);
					pkt.pos = pos;
					p = pkt;
				} else {
					p = new MsgSyncFields(this, pos, SERVER);
				}
				ch.sendToAllTrackingChunk(p, owner.getWorld(), pos.getX(), pos.getZ());
			}
			if ((modified & gui) != 0) {
				SimpleList<EntityPlayerMP> players = this.players;
				for (int i = players.size() - 1; i >= 0; i--) {
					if (players.get(i).connection == null) players.remove(i);
				}

				if (idx >= 0) {
					MsgSyncField pkt = new MsgSyncField(idx, prevFields[idx]);
					for (int i = 0; i < players.size(); i++) {
						EntityPlayerMP p = players.get(i);
						pkt.wid = p.currentWindowId;
						ch.sendTo(pkt, p);
					}
				} else {
					MsgSyncFields pkt = new MsgSyncFields(this, pos, GUI);
					for (int i = 0; i < players.size(); i++) {
						ch.sendTo(pkt, players.get(i));
					}
				}
			}
		}
	}

	private static int getIdx(long l) {
		int i = 0;
		while (l != 0) {
			if ((l & 1) != 0) return l == 1 ? i : -1;
			l >>>= 1;
			i++;
		}
		return -1;
	}

	public FieldSyncer register(int k, int dir) {
		switch (dir) {
			case GUI:
				gui |= 1L << k;
				counts += 0x1;
				break;
			case SERVER:
				server |= 1L << k;
				counts += 0x100;
				break;
			case CLIENT:
				client |= 1L << k;
				counts += 0x10000;
				break;
		}
		fieldCount = Math.max(++k, fieldCount);
		if (fields.length < k) {
			fields = Arrays.copyOf(fields, k);
			prevFields = Arrays.copyOf(prevFields, k);
		}
		return this;
	}

	public FieldSyncer register(int from, int to, int dir) {
		switch (dir) {
			case GUI:
				long mask = 0;
				counts += (to - from);
				while (from < to) {
					mask |= 1L << from++;
				}
				gui |= mask;
				break;
			case SERVER:
				mask = 0;
				counts += (to - from) * 0x100;
				while (from < to) {
					mask |= 1L << from++;
				}
				server |= mask;
				break;
			case CLIENT:
				mask = 0;
				counts += (to - from) * 0x10000;
				while (from < to) {
					mask |= 1L << from++;
				}
				client |= mask;
				break;
		}
		if (fields.length <= to) {
			fields = Arrays.copyOf(fields, to);
			prevFields = Arrays.copyOf(prevFields, to);
		}
		fieldCount = Math.max(to, fieldCount);
		return this;
	}

	public void setField(int k, int v, int dir) {
		switch (dir) {
			case CLIENT:
				if ((client & (1L << k)) == 0) {
					ImpLib.logger().warn(owner + ".SetField(" + k + "," + v + ")来自无效的 CLIENT (多半是尝试数据包攻击)");
					return;
				}
				break;
			case SERVER:
				if ((server & (1L << k)) == 0) {
					ImpLib.logger().warn(owner + " 字段定义无效");
					return;
				}
				break;
			case GUI:
				if ((gui & (1L << k)) == 0) {
					ImpLib.logger().warn(owner + " 字段定义无效");
					return;
				}
				break;
		}
		fields[k] = v;
		((Syncable) owner).setFieldInfo(k, v);
		prevFields[k] = v;
	}

	public void setFields(int[] data, byte dir) {
		int count = (counts >>> (dir << 3)) & 0xFF;
		if (data.length != count) {
			ImpLib.logger().warn(owner + " 字段定义无效");
			return;
		}

		long set;
		switch (dir) {
			case CLIENT:
				set = client;
				break;
			case SERVER:
				set = server;
				break;
			default:
				set = gui;
				break;
		}

		Syncable tile = (Syncable) this.owner;
		int i = 0, k = 0;
		while (set != 0) {
			if ((set & 1) != 0) {
				tile.setFieldInfo(k, fields[k] = data[i]);
				prevFields[k] = data[i];
				i++;
			}
			k++;
			set >>>= 1;
		}
	}

	public int[] getFields(byte dir) {
		int[] arr = new int[(counts >>> (dir << 3)) & 0xFF];
		long set;
		switch (dir) {
			case CLIENT:
				set = client;
				break;
			case SERVER:
				set = server;
				break;
			default:
				set = gui;
				break;
		}

		int i = 0, j = 0;
		while (set != 0) {
			if ((set & 1) != 0) {
				arr[i++] = prevFields[j];
			}
			j++;
			set >>>= 1;
		}

		return arr;
	}

	public int getField(int i) {
		return fields[i];
	}

	public int getPrevField(int i) {
		return prevFields[i];
	}
}
