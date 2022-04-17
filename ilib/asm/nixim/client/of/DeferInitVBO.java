package ilib.asm.nixim.client.of;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;

/**
 * @author Roj233
 * @since 2022/4/26 16:02
 */
@Nixim("net.minecraft.client.renderer.vertex.VertexBuffer")
public class DeferInitVBO extends VertexBuffer {
    @Shadow("field_177365_a")
    private int glBufferId;
    @Shadow("field_177364_c")
    private int count;

    public DeferInitVBO(VertexFormat _lvt_1_) {
        super(_lvt_1_);
    }

    @Inject("/")
    public void drawArrays(int mode) {
        if (glBufferId < 0) return;
        GlStateManager.glDrawArrays(mode, 0, this.count);
    }
}
