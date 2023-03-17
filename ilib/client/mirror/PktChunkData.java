package ilib.client.mirror;

import ilib.ImpLib;
import ilib.net.IMessage;
import ilib.net.IMessageHandler;
import ilib.net.MessageContext;
import io.netty.buffer.Unpooled;
import roj.collect.SimpleList;
import roj.util.Helpers;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class PktChunkData implements IMessage, IMessageHandler<PktChunkData> {
	private int dimensionId, chunkX, chunkZ;

	private int availableSections;
	private byte[] buffer;
	private List<NBTTagCompound> tileTags;

	private boolean fullChunk;

	public PktChunkData() {}

	public PktChunkData(int dimensionId, Chunk chunk, int sectionFilter) {
		this.dimensionId = dimensionId;
		this.chunkX = chunk.x;
		this.chunkZ = chunk.z;
		this.fullChunk = sectionFilter == 0xFFFF;

		boolean skyLight = chunk.getWorld().provider.hasSkyLight();
		availableSections = exportChunkData(chunk, skyLight, sectionFilter);

		this.tileTags = new SimpleList<>();
		for (Map.Entry<BlockPos, TileEntity> entry : chunk.getTileEntityMap().entrySet()) {
			BlockPos pos = entry.getKey();
			if ((sectionFilter & 1 << (pos.getY() >> 4)) == 0) continue;

			tileTags.add(entry.getValue().getUpdateTag());
		}
	}

	public PacketBuffer getReadBuffer() {
		return new PacketBuffer(Unpooled.wrappedBuffer(buffer));
	}

	public int exportChunkData(Chunk c, boolean skylight, int changed) {
		int flag = 0;
		int capacity = 0;

		ExtendedBlockStorage[] array = c.getBlockStorageArray();
		for (int i = 0; i < array.length; i++) {
			ExtendedBlockStorage storage = array[i];
			if (storage != Chunk.NULL_BLOCK_STORAGE && !storage.isEmpty() && (changed & 1 << i) != 0) {
				flag |= 1 << i;
				capacity += storage.getData().getSerializedSize();
				capacity += storage.getBlockLight().getData().length;
				if (skylight) capacity += storage.getSkyLight().getData().length;
			}
		}

		if (changed == 0xFFFF) {
			capacity += c.getBiomeArray().length;
		}

		buffer = new byte[capacity];
		PacketBuffer buf = new PacketBuffer(Unpooled.wrappedBuffer(buffer));
		buf.clear();

		for (int i = 0; i < array.length; i++) {
			ExtendedBlockStorage storage = array[i];
			if ((flag & 1 << i) != 0) {
				storage.getData().write(buf);
				buf.writeBytes(storage.getBlockLight().getData());
				if (skylight) buf.writeBytes(storage.getSkyLight().getData());
			}
		}

		if (changed == 0xFFFF) {
			buf.writeBytes(c.getBiomeArray());
		}

		return flag;
	}

	public int getChunkX() {
		return this.chunkX;
	}

	public int getChunkZ() {
		return this.chunkZ;
	}

	public int getExtractedSize() {
		return this.availableSections;
	}

	public boolean isFullChunk() {
		return this.fullChunk;
	}

	public List<NBTTagCompound> getTileTags() {
		return this.tileTags;
	}

	@Override
	public void fromBytes(PacketBuffer buf) {
		this.dimensionId = buf.readVarInt();
		this.chunkX = buf.readVarInt();
		this.chunkZ = buf.readVarInt();
		this.fullChunk = buf.readBoolean();
		this.availableSections = buf.readVarInt();

		int bufSize = buf.readVarInt();
		if (bufSize > 2097152) {
			throw new RuntimeException("Chunk Packet trying to allocate too much memory on read.");
		} else {
			this.buffer = new byte[bufSize];
			buf.readBytes(buffer);

			int count = buf.readVarInt();
			NBTTagCompound[] tags = new NBTTagCompound[count];

			for (int i = 0; i < count; ++i) {
				try {
					tags[i] = buf.readCompoundTag();
				} catch (IOException e) {
					Helpers.athrow(e);
				}
			}

			this.tileTags = Arrays.asList(tags);
		}
	}

	@Override
	public void toBytes(PacketBuffer buf) {
		buf.writeVarInt(dimensionId).writeVarInt(chunkX).writeVarInt(chunkZ).writeBoolean(fullChunk);
		buf.writeVarInt(availableSections).writeVarInt(buffer.length).writeBytes(buffer);
		buf.writeVarInt(tileTags.size());
		for (int i = 0; i < tileTags.size(); i++) {
			buf.writeCompoundTag(tileTags.get(i));
		}
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void onMessage(PktChunkData msg, MessageContext ctx) {
		WorldClient world = ClientHandler.getWorld(dimensionId);
		if (world == null) {
			ImpLib.logger().warn("Null world #" + dimensionId);
			return;
		}
		world.getChunkProvider().loadChunk(chunkX, chunkZ).read(getReadBuffer(), availableSections, fullChunk);
	}
}
