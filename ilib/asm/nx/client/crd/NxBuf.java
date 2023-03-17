package ilib.asm.nx.client.crd;

import ilib.asm.util.MCHooksClient;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.BitSet;

/**
 * @author Roj233
 * @since 2022/4/23 17:39
 */
@Nixim("net.minecraft.client.renderer.BufferBuilder")
class NxBuf extends BufferBuilder {
	NxBuf() {super(0);}

	@Shadow("field_178999_b")
	private IntBuffer rawIntBuffer;
	@Shadow("field_179000_c")
	private FloatBuffer rawFloatBuffer;
	@Shadow("field_178997_d")
	private int vertexCount;
	@Shadow("field_179004_l")
	private double xOffset;
	@Shadow("field_179005_m")
	private double yOffset;
	@Shadow("field_179002_n")
	private double zOffset;
	@Shadow("field_179011_q")
	private VertexFormat vertexFormat;

	@Shadow("func_181665_a")
	private static float getDistanceSq(FloatBuffer fb, float x, float y, float z, int i, int o) {
		return 0;
	}

	@Shadow("func_181664_j")
	private int getBufferSize() {
		return 0;
	}

	@Copy
	public static void heapSort(int[] key, float[] val, int len) {
		int end = len - 1;
		for (int p = end >> 1; p > 0; p--) {//创建大根堆，a[0]不参与建堆
			heap_push(key, val, p, end);
		}
		while (end > 0) {//a[0]不参与排序
			int tmp = key[1];
			key[1] = key[end];
			key[end] = tmp;
			heap_push(key, val, 1, --end);
		}
	}

	@Copy
	private static void heap_push(int[] arr, float[] val, int p, int end) {
		int ch, tmp;
		while (true) {
			ch = p << 1;
			if (ch > end) break;
			// ch为两个子节点中较大的下标
			if (ch < end && val[arr[ch]] >= val[arr[ch + 1]]) ch++;
			if (val[arr[ch]] < val[arr[p]]) {
				tmp = arr[ch];
				arr[ch] = arr[p];
				arr[p] = tmp;
			} else {break;}
			p = ch;
		}
	}

	@Inject("/")
	public void sortVertexData(float cameraX, float cameraY, float cameraZ) {
		int quads = vertexCount / 4;
		if (quads < 1) return;

		MCHooksClient mx = MCHooksClient.get();

		float[] dist = mx.data3;
		int[] indexes = mx.data4;
		if (dist.length < quads) {
			if (quads < 99999) {
				dist = mx.data3 = new float[quads];
				indexes = mx.data4 = new int[quads];
			} else {
				System.err.println("Many vertexes: " + quads);
				dist = new float[quads];
				indexes = new int[quads];
			}
		}

		int size = vertexFormat.getSize();
		FloatBuffer fb = rawFloatBuffer;
		for (int i = 0; i < quads; ++i) {
			indexes[i] = i;
			dist[i] = getDistanceSq(fb, (float) (cameraX + xOffset), (float) (cameraY + yOffset), (float) (cameraZ + zOffset), size >> 2, i * size);
		}

		heapSort(indexes, dist, quads);

		BitSet set = new BitSet();
		int[] tmp = new int[size];

		IntBuffer ints = rawIntBuffer;
		ints.limit(ints.capacity());
		IntBuffer other = ints.duplicate();
		for (int i = set.nextClearBit(0); i < quads; i = set.nextClearBit(i + 1)) {
			int id = indexes[i];
			if (id != i) {
				ints.position(id * size);
				ints.get(tmp);

				int id2 = id;
				for (int j = indexes[id]; id2 != i; j = indexes[j]) {
					other.limit(j * size + size).position(j * size);

					ints.position(id2 * size);
					ints.put(other);

					set.set(id2);
					id2 = j;
				}

				ints.position(i * size);
				ints.put(tmp);
			}

			set.set(i);
		}

		ints.position(this.getBufferSize()).limit(ints.capacity());
	}
}
