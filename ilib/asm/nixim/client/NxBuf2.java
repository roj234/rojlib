package ilib.asm.nixim.client;

import org.lwjgl.BufferUtils;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.io.NIOUtil;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.math.MathHelper;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * @author Roj233
 * @since 2022/4/23 17:39
 */
@Nixim("net.minecraft.client.renderer.BufferBuilder")
class NxBuf2 extends BufferBuilder {
    NxBuf2() { super(0); }

    @Shadow("field_179001_a")
    private ByteBuffer byteBuffer;
    @Shadow("field_181676_c")
    private ShortBuffer rawShortBuffer;
    @Shadow("field_178999_b")
    private IntBuffer rawIntBuffer;
    @Shadow("field_179000_c")
    private FloatBuffer rawFloatBuffer;
    @Shadow("field_178997_d")
    private int vertexCount;
    @Shadow("field_179011_q")
    private VertexFormat vertexFormat;

    @Inject("func_181670_b")
    private void growBuffer(int plus) {
        ByteBuffer bb = byteBuffer;
        if (MathHelper.roundUp(plus, 4) / 4 > rawIntBuffer.remaining() || vertexCount * vertexFormat.getSize() + plus > bb.capacity()) {
            int newCap = bb.capacity() + MathHelper.roundUp(plus, 262144);
            int pos = rawIntBuffer.position();
            ByteBuffer newBB = BufferUtils.createByteBuffer(newCap);
            bb.position(0);
            newBB.put(bb).rewind();
            byteBuffer = newBB;
            rawFloatBuffer = newBB.asFloatBuffer().asReadOnlyBuffer();
            (rawIntBuffer = newBB.asIntBuffer()).position(pos);
            (rawShortBuffer = newBB.asShortBuffer()).position(pos<<1);
            NIOUtil.clean(bb);
        }
    }
}
