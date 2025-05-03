package roj.reflect.litasm;

import roj.collect.IntMap;
import roj.reflect.ReflectionUtils;
import roj.reflect.Unaligned;
import roj.util.DynByteBuf;

import java.lang.annotation.Annotation;

/**
 * @author Roj234
 * @since 2024/10/21 6:33
 */
abstract class Assembler {
	static Assembler getInstance() {
		String arch = System.getProperty("os.arch").toLowerCase();

		if (arch.equals("amd64")) {
			String os = System.getProperty("os.name").toLowerCase();
			return os.contains("windows") ? new win64() : new linux64();
		}

		// !! 这些玩意未经测试，多半是不能用的, 只有win64可堪一用
		if (arch.equals("aarch64") || arch.equals("arm64")) return new aarch64();
		if (arch.equals("riscv64")) return new riscv64();

		throw new IllegalStateException("不支持的系统架构: "+arch);
	}
	private static final class aarch64 extends Assembler {
		// AArch64 calling convention:
		//     Java: x1, x2, x3, x4, x5, x6, x7, x0, stack
		//   Native: x0, x1, x2, x3, x4, x5, x6, x7, stack

		@Override public void javaToCdecl(DynByteBuf buf, Class<?>[] types, Annotation[][] annotations) {
			if (types.length >= 8) {
				// 8th Java argument clashes with the 1st native arg
				buf.putIntLE(0xaa0003e8);  // mov x8, x0
			}

			int index = 0;
			for (int i = 0; i < types.length; i++) {
				Class<?> type = types[i];
				if (type.isPrimitive()) {
					if (index < 8 && type != float.class && type != double.class) {
						// mov x0, x1
						buf.putIntLE((type == long.class ? 0xaa0003e0 : 0x2a0003e0) | index | (index + 1) << 16);
						index++;
					}
				} else if (index < 8) {
					// add x0, x1, #offset
					buf.putIntLE(0x91000000 | index | (index + 1) << 5 | baseOffset(type, annotations[i]) << 10);
					index++;
				} else {
					throw new IllegalArgumentException("Too many object arguments");
				}
			}
		}

		@Override void emitCall(DynByteBuf buf, long address) {
			int a0 = (int) address & 0xffff;
			int a1 = (int) (address >>> 16) & 0xffff;
			int a2 = (int) (address >>> 32) & 0xffff;
			int a3 = (int) (address >>> 48);

			buf.putIntLE(0xd2800009 | a0 << 5);               // movz x9, #0xffff
			if (a1 != 0) buf.putIntLE(0xf2a00009 | a1 << 5);  // movk x9, #0xffff, lsl #16
			if (a2 != 0) buf.putIntLE(0xf2c00009 | a2 << 5);  // movk x9, #0xffff, lsl #32
			if (a3 != 0) buf.putIntLE(0xf2e00009 | a3 << 5);  // movk x9, #0xffff, lsl #48

			buf.putIntLE(0xd61f0120);                         // br x9
		}

		@Override String platform() {return "aarch64";}
	}
	private static final class riscv64 extends Assembler {
		// RISCV64 calling convention:
		//     Java: x10, x11, x12, x13, x14, x15, x16, x17, stack
		//   Native: x10, x11, x12, x13, x14, x15, x16, x17, stack

		@Override void javaToCdecl(DynByteBuf buf, Class<?>[] types, Annotation[][] annotations) {}

		@Override void emitCall(DynByteBuf buf, long address) {
			long imm = address >> 17;
			long upper = imm, lower = imm;
			lower = (lower << 52) >> 52;
			upper -= lower;

			int a0 = (int)(upper);
			int a1 = (int)(lower);
			int a2 = (int)((address >> 6) & 0x7ff);
			int a3 = (int)((address) & 0x3f);

			int zr = 0; // x0
			int t0 = 5; // x5

			buf.putIntLE(0b0110111 | (t0 << 7) | (a0 << 12));                              // lui t0, a0
			buf.putIntLE(0b0010011 | (t0 << 7) | (0b000 << 12) | (t0 << 15) | (a1 << 20)); // addi t0, t0, a1
			buf.putIntLE(0b0010011 | (t0 << 7) | (0b001 << 12) | (t0 << 15) | (11 << 20)); // slli t0, t0, 11
			buf.putIntLE(0b0010011 | (t0 << 7) | (0b000 << 12) | (t0 << 15) | (a2 << 20)); // addi t0, t0, a2
			buf.putIntLE(0b0010011 | (t0 << 7) | (0b001 << 12) | (t0 << 15) | ( 6 << 20)); // slli t0, t0, 6
			buf.putIntLE(0b1100111 | (zr << 7) | (0b000 << 12) | (t0 << 15) | (a3 << 20)); // jalr a3(t0)
		}

		@Override String platform() {return "riscv64";}
	}
	private static class win64 extends Assembler {
		// x64 calling convention (Windows):
		//     Java: rdx,  r8,  r9, rdi, rsi, vm_stack
		//   Native: rcx, rdx,  r8,  r9, cdecl_stack

		private static final int[] MOVE_INT_ARG = {
			0x89d1,    // mov  ecx, edx
			0x4489c2,  // mov  edx, r8d
			0x4589c8,  // mov  r8d, r9d
			0x4189f9,  // mov  r9d, edi
		};

		private static final int[] MOVE_LONG_ARG = {
			0x4889d1,  // mov  rcx, rdx
			0x4c89c2,  // mov  rdx, r8
			0x4d89c8,  // mov  r8, r9
			0x4989f9,  // mov  r9, rdi
		};

		private static final int[] MOVE_OBJ_ARG = {
			0x488d4a,  // lea  rcx, [rdx+N]
			0x498d50,  // lea  rdx, [r8+N]
			0x4d8d41,  // lea  r8, [r9+N]
			0x4c8d4f,  // lea  r9, [rdi+N]
		};

		static final class Mov {
			int read, write, add;
			Mov before;
			boolean byref;

			Mov(int from, int to) {
				read = from;
				write = to;
			}

			@Override
			public String toString() {return "mov "+read+"(%rsp) + "+add+" => "+write+"(%rsp)";}
		}

		@Override void javaToCdecl(DynByteBuf buf, Class<?>[] types, Annotation[][] annotations) {
			int argno = 0;
			boolean hasFloat = false;

			for (argno = 0; argno < types.length; argno++) {
				Class<?> type = types[argno];
				if (argno >= 4) {
					if (type == float.class || type == double.class) hasFloat = true;
				} else {
					if (type == float.class || type == double.class) throw new UnsatisfiedLinkError("不会，请把float放到栈上");

					if (type.isPrimitive()) {
						emit(buf, (type == long.class ? MOVE_LONG_ARG : MOVE_INT_ARG)[argno]);
					} else {
						emit(buf, MOVE_OBJ_ARG[argno]);
						buf.put(asByte(baseOffset(type, annotations[argno])));
					}
				}
			}

			// 从右向左入栈
			if (argno >= 5) {
				if (argno >= 6) {
					int rsp = 48;
					int jsp = hasFloat ? (((types.length - 5)>>>1) << 4) + 8 : 24;

					for (int i = 5; i < types.length-1; i++) {
						var type = types[i];
						jsp += type.isPrimitive() && type != long.class && type != double.class ? 8 : 16;
					}

					var regmap = new IntMap<Mov>();
					for (int i = 5; i < types.length; i++) {
						var type = types[i];

						if (jsp == 24) {
							// 'sp' + 2
							jsp = 8; // mov %r13, hh(%rsp) => 4C896C24[hh]
							if (i != types.length - 1) throw new AssertionError();
						}

						var item = new Mov(jsp, rsp);
						if (!type.isPrimitive()) item.add = baseOffset(type, annotations[i]);
						regmap.putInt(rsp, item);

						jsp -= type.isPrimitive() && type != long.class && type != double.class ? 8 : 16;
						rsp += 8;
					}

					writeRegmap(buf, regmap);
				}
				if (!types[4].isPrimitive()) {
					var off = baseOffset(types[4], annotations[4]);
					if (off > 255) buf.putMedium(0x4881C6).putIntLE(off);
					else buf.putMedium(0x4883C6).put(off);    // rsi += $off
				}
				buf.putInt(0x48897424).put(40);            // [rsp+40] = rsi
			}
		}

		private static void writeRegmap(DynByteBuf buf, IntMap<Mov> regmap) {
			for (Mov value : regmap.values()) {
				var beingWritten = regmap.get(value.read);
				if (beingWritten != null) {
					while (beingWritten.before != null) beingWritten = beingWritten.before;
					beingWritten.before = value;
					value.byref = true;
				}
			}

			for (Mov map : regmap.values())
				if (!map.byref)
					writeReloc(buf, map);
		}

		private static void writeReloc(DynByteBuf buf, Mov map) {
			if (map.before != null) writeReloc(buf, map.before);
			if (map.read == map.write && map.add == 0) return;

			var src = map.read;
			if (src > 127) buf.putInt(0x488B8424).putIntLE(src);
			else buf.putInt(0x488B4424).put(src);       // rax = [rsp + $src]

			var add = map.add;
			if (add != 0) {
				if (add > 255) buf.putShort(0x4805).putIntLE(add);
				else buf.putMedium(0x4883C0).put(add);  // rax += $add
			}

			var dst = map.write;
			if (dst > 127) buf.putInt(0x48898424).putIntLE(dst);
			else buf.putInt(0x48894424).put(dst);       // [rsp + $dst] = rax
		}

		@Override void emitCall(DynByteBuf buf, long address) {
			buf.putShort(0x48b8).putLongLE(address);          // rax = address
			buf.putShort(0xffe0);                             // jmp rax
		}

		@Override String platform() {return "win64";}
	}
	static class linux64 extends win64 {
		// x64 calling convention (Linux, macOS):
		//     Java: rsi, rdx, rcx,  r8,  r9, rdi, stack
		//   Native: rdi, rsi, rdx, rcx,  r8,  r9, stack

		private static final int SAVE_LAST_ARG =
			0x4889f8;  // mov  rax, rdi

		private static final int[] MOVE_INT_ARG = {
			0x89f7,    // mov  edi, esi
			0x89d6,    // mov  esi, edx
			0x89ca,    // mov  edx, ecx
			0x4489c1,  // mov  ecx, r8d
			0x4589c8,  // mov  r8d, r9d
			0x4189c1,  // mov  r9d, eax
		};

		private static final int[] MOVE_LONG_ARG = {
			0x4889f7,  // mov  rdi, rsi
			0x4889d6,  // mov  rsi, rdx
			0x4889ca,  // mov  rdx, rcx
			0x4c89c1,  // mov  rcx, r8
			0x4d89c8,  // mov  r8, r9
			0x4989c1,  // mov  r9, rax
		};

		private static final int[] MOVE_OBJ_ARG = {
			0x488d7e,  // lea  rdi, [rsi+N]
			0x488d72,  // lea  rsi, [rdx+N]
			0x488d51,  // lea  rdx, [rcx+N]
			0x498d48,  // lea  rcx, [r8+N]
			0x4d8d41,  // lea  r8, [r9+N]
			0x4c8d48,  // lea  r9, [rax+N]
		};

		@Override
		public void javaToCdecl(DynByteBuf buf, Class<?>[] types, Annotation[][] annotations) {
			if (types.length >= 6) {
				// 6th Java argument clashes with the 1st native arg
				emit(buf, SAVE_LAST_ARG);
			}

			int index = 0;
			for (int i = 0; i < types.length; i++) {
				Class<?> type = types[i];
				if (type.isPrimitive()) {
					if (index < 6 && type != float.class && type != double.class) {
						emit(buf, (type == long.class ? MOVE_LONG_ARG : MOVE_INT_ARG)[index++]);
					}
				} else if (index < 6) {
					emit(buf, MOVE_OBJ_ARG[index++]);
					buf.put(asByte(baseOffset(type, annotations[i])));
				} else {
					throw new IllegalArgumentException("Too many object arguments");
				}
			}
		}

		@Override String platform() {return "linux64";}
	}

	abstract void javaToCdecl(DynByteBuf buf, Class<?>[] types, Annotation[][] annotations);
	abstract void emitCall(DynByteBuf buf, long address);
	abstract String platform();

	protected static int baseOffset(Class<?> type, Annotation[] annotations) {
		for (Annotation annotation : annotations) {
			if (annotation instanceof ObjectField field) {
				if (field.value().isEmpty()) return 0;
				return (int) ReflectionUtils.fieldOffset(type, field.value());
			}
		}

		if (type.isArray() && type.getComponentType().isPrimitive()) {
			return Unaligned.U.arrayBaseOffset(type);
		}

		throw new IllegalArgumentException("你忘记打注解了喵: "+type);
	}

	protected static byte asByte(int value) {
		if (value > 255) throw new IllegalArgumentException("Not in the byte range: " + value);
		return (byte) value;
	}

	protected static void emit(DynByteBuf buf, int code) {
		if ((code >>> 24) != 0) buf.put(code >>> 24);
		if ((code >>> 16) != 0) buf.put(code >>> 16);
		if ((code >>> 8) != 0) buf.put(code >>> 8);
		if (code != 0) buf.put(code);
	}
}

