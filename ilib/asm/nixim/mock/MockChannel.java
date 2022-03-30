package ilib.asm.nixim.mock;

import ilib.net.mock.MockingUtil;
import net.minecraft.network.INetHandler;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

/**
 * @author solo6975
 * @since 2022/3/31 21:41
 */
@Nixim("net.minecraftforge.fml.common.network.internal.FMLProxyPacket")
class MockChannel extends FMLProxyPacket {
    public MockChannel(SPacketCustomPayload original) {
        super(original);
    }

    @Inject(value = "func_148833_a")
    public void processPacket(INetHandler h) {
        if (!MockingUtil.interceptPacket(this, h)) {
            super.processPacket(h);
        }
    }
}
