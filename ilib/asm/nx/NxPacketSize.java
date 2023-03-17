package ilib.asm.nx;

import ilib.Config;
import ilib.ImpLib;
import ilib.asm.rpl.VarIntEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import roj.asm.nixim.Dynamic;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author Roj234
 * @since 2021/1/16 15:06
 */
@Nixim("net.minecraft.network.PacketBuffer")
class NxPacketSize extends PacketBuffer {
	public NxPacketSize(ByteBuf wrapped) {
		super(wrapped);
	}

	@Inject
	public static int getVarIntSize(int input) {
		return VarIntEncoder.getVarIntLength(input);
	}

	@Inject
	public byte[] readByteArray(int maxLength) {
		int i = this.readVarInt();
		if (i > maxLength) {
			final DecoderException o = new DecoderException("ByteArray with size " + i + " is bigger than allowed " + maxLength);
			if (!Config.packetBufferInfinity) {
				throw o;
			} else {
				ImpLib.logger().error(o);
			}
			return new byte[0];
		} else {
			byte[] abyte = new byte[i];
			this.readBytes(abyte);
			return abyte;
		}
	}

	@Inject
	public int[] readVarIntArray(int maxLength) {
		int i = this.readVarInt();
		if (i > maxLength) {
			final DecoderException o = new DecoderException("VarIntArray with size " + i + " is bigger than allowed " + maxLength);
			if (!Config.packetBufferInfinity) {
				throw o;
			} else {
				ImpLib.logger().error(o);
			}
			return new int[0];
		} else {
			int[] aint = new int[i];

			for (int j = 0; j < aint.length; ++j) {
				aint[j] = this.readVarInt();
			}

			return aint;
		}
	}

	@Inject
	@Dynamic("client")
	public long[] readLongArray(@Nullable long[] array, int maxLength) {
		int i = this.readVarInt();
		if (array == null || array.length != i) {
			if (i > maxLength) {
				final DecoderException o = new DecoderException("LongArray with size " + i + " is bigger than allowed " + maxLength);
				if (!Config.packetBufferInfinity) {
					throw o;
				} else {
					ImpLib.logger().error(o);
				}
				return array == null ? new long[0] : array;
			}

			array = new long[i];
		}

		for (int j = 0; j < array.length; ++j) {
			array[j] = this.readLong();
		}

		return array;
	}

	@Inject
	public String readString(int maxLength) {
		int i = this.readVarInt();
		if (i > maxLength * 4) {
			final DecoderException o = new DecoderException("The received encoded string buffer length is longer than maximum allowed (" + i + " > " + maxLength * 4 + ")");
			if (!Config.packetBufferInfinity) {
				throw o;
			} else {
				ImpLib.logger().error(o);
			}
			return "";
		} else if (i < 0) {
			final DecoderException o = new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
			if (!Config.packetBufferInfinity) {
				throw o;
			} else {
				ImpLib.logger().error(o);
			}
			return "";
		} else {
			String s = this.toString(this.readerIndex(), i, StandardCharsets.UTF_8);
			this.readerIndex(this.readerIndex() + i);
			if (s.length() > maxLength) {
				final DecoderException o = new DecoderException("The received string length is longer than maximum allowed (" + i + " > " + maxLength + ")");
				if (!Config.packetBufferInfinity) {
					throw o;
				} else {
					ImpLib.logger().error(o);
				}
			}
			return s;
		}
	}

	@Inject
	public NBTTagCompound readCompoundTag() {
		int i = this.readerIndex();
		byte b0 = this.readByte();
		if (b0 == 0) {
			return null;
		} else {
			this.readerIndex(i);

			try {
				return CompressedStreamTools.read(new ByteBufInputStream(this), new NBTSizeTracker(Config.nbtMaxLength));
			} catch (IOException var4) {
				throw new EncoderException(var4);
			}
		}
	}
}
