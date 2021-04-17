package ilib.asm.nx.mock;

import ilib.net.mock.MockingUtil;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.PacketBuffer;

import java.io.IOException;

/**
 * @author solo6975
 * @since 2022/3/31 20:12
 */
@Nixim("net.minecraft.network.handshake.client.C00Handshake")
class VersionMock {
	@Shadow
	private String ip;
	@Shadow
	private int port;
	@Shadow
	private EnumConnectionState requestedState;

	@Inject
	public void writePacketData(PacketBuffer buf) throws IOException {
		buf.writeVarInt(MockingUtil.mockProtocol);
		if (MockingUtil.mockIp != null) ip = MockingUtil.mockIp;
		buf.writeString(MockingUtil.mockFMLMarker ? ip.concat("\u0000FML\u0000") : ip)
		   .writeShort(MockingUtil.mockPort >= 0 ? MockingUtil.mockPort :port);
		buf.writeVarInt(requestedState.getId());
	}
}
