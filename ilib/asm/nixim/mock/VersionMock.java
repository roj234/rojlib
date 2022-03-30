package ilib.asm.nixim.mock;

import ilib.net.mock.MockingUtil;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.PacketBuffer;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import java.io.IOException;

/**
 * @author solo6975
 * @since 2022/3/31 20:12
 */
@Nixim("net.minecraft.network.handshake.client.C00Handshake")
class VersionMock {
    @Shadow("field_149600_a")
    private int protocolVersion;
    @Shadow("field_149598_b")
    private String ip;
    @Shadow("field_149599_c")
    private int port;
    @Shadow("field_149597_d")
    private EnumConnectionState requestedState;
    @Shadow("hasFMLMarker")
    private boolean hasFMLMarker;

    @Inject("<init>")
    public VersionMock(String a, int b, EnumConnectionState c) {
        this.hasFMLMarker = false;
        this.protocolVersion = MockingUtil.mockProtocol;
        this.ip = a;
        this.port = b;
        this.requestedState = c;
    }

    @Inject("func_148840_b")
    public void writePacketData(PacketBuffer buf) throws IOException {
        buf.writeVarInt(MockingUtil.mockProtocol);
        if (MockingUtil.mockIp != null) ip = MockingUtil.mockIp;
        if (MockingUtil.mockPort >= 0) port = MockingUtil.mockPort;
        if (MockingUtil.mockFMLMarker) {
            buf.writeString(this.ip + "\u0000FML\u0000");
        } else {
            buf.writeString(this.ip);
        }
        buf.writeShort(this.port);
        buf.writeVarInt(this.requestedState.getId());
    }
}
