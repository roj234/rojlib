package ilib.asm.util;

import ilib.client.mirror.render.world.MyVisGraph;
import org.apache.commons.lang3.tuple.MutablePair;
import org.lwjgl.BufferUtils;
import roj.io.NIOUtil;
import roj.reflect.FieldAccessor;
import roj.reflect.ReflectionUtils;
import roj.util.EmptyArrays;
import roj.util.FastLocalThread;
import roj.util.Helpers;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import net.minecraftforge.event.terraingen.BiomeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/4/22 17:25
 */
@SideOnly(Side.CLIENT)
public class MCReplaces extends BlockPos.MutableBlockPos {
    public static class Worker extends FastLocalThread {
        MCReplaces me = new MCReplaces();
        public Worker(Runnable w) {
            super(w);
        }
    }

    public final List<BakedQuad>[] tmpArray = Helpers.cast(new List<?>[7]);

    private ByteBuffer audioBuf;
    private IntBuffer audioBuf2 = BufferUtils.createIntBuffer(8);

    public static ByteBuffer createByteBuffer(int length) {
        MCReplaces $ = get();

        ByteBuffer buf = $.audioBuf;
        if (buf == null || buf.capacity() < length) {
            if (buf != null) NIOUtil.clean(buf);
            $.audioBuf = buf = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
        }
        return (ByteBuffer) buf.clear().limit(length);
    }

    public static IntBuffer createIntBuffer(int length) {
        MCReplaces $ = get();

        IntBuffer buf = $.audioBuf2;
        if (buf.capacity() < length) {
            NIOUtil.clean(buf);
            $.audioBuf2 = buf = BufferUtils.createIntBuffer(length);
        }
        return (IntBuffer) buf.clear().limit(length);
    }

    public static boolean debugRenderAllSide;

    public static final BlockRenderLayer[] BlockRenderLayerValues = BlockRenderLayer.values();
    public static BlockRenderLayer[] values() {
        return BlockRenderLayerValues;
    }

    private static final ThreadLocal<MCReplaces> myPos = ThreadLocal.withInitial(MCReplaces::new);
    private static MCReplaces curr;

    public static MCReplaces get() {
        Thread t = Thread.currentThread();
        if (t.getClass() == Worker.class)
            return ((Worker) t).me;

        MCReplaces p = MCReplaces.curr;
        if (p == null || p.owner != t) {
            return curr = myPos.get();
        }
        return p;
    }

    public final Thread owner = Thread.currentThread();

    public final MutablePair<?, ?> pair = MutablePair.of(null, null);
    public final float[] data = new float[4];
    public final float[] data2 = new float[6];
    public final float[][] normals = new float[4][4];
    public float[] data3 = EmptyArrays.FLOATS;
    public int[] data4;

    public final MyVisGraph graph = new MyVisGraph();

    private static Field biomeField, colorField;
    static {
        try {
            biomeField = BiomeEvent.class.getDeclaredField("biome");
            biomeField.setAccessible(true);
            colorField = BiomeEvent.BiomeColor.class.getDeclaredField("originalColor");
            colorField.setAccessible(true);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    private final FieldAccessor biomeAcc = ReflectionUtils.access(biomeField),
                                colorAcc = ReflectionUtils.access(colorField);

    public void setEvent(BiomeEvent e, Biome b, int color) {
        biomeAcc.setInstance(e);
        colorAcc.setInstance(e);

        biomeAcc.setObject(b);
        colorAcc.setInt(color);

        biomeAcc.clearInstance();
        colorAcc.clearInstance();
    }
}
