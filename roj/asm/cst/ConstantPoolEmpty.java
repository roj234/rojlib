package roj.asm.cst;

/**
 * 空着的ConstWriter
 *
 * @author solo6975
 * @since 2021/10/3 23:02
 */
public final class ConstantPoolEmpty extends ConstantPool {
	@Override
	public int getClassId(String className) {
		return 1;
	}

	@Override
	public int getDescId(String name, String desc) {
		return 1;
	}

	@Override
	public int getDoubleId(double i) {
		return 1;
	}

	@Override
	public int getDynId(int table, String name, String desc) {
		return 1;
	}

	@Override
	public int getFieldRefId(String className, String name, String desc) {
		return 1;
	}

	@Override
	public int getFloatId(float i) {
		return 1;
	}

	@Override
	public int getIntId(int i) {
		return 1;
	}

	@Override
	public int getInvokeDynId(int table, String name, String desc) {
		return 1;
	}

	@Override
	public int getItfRefId(String className, String name, String desc) {
		return 1;
	}

	@Override
	public int getLongId(long i) {
		return 1;
	}

	@Override
	public int getMethodHandleId(String className, String name, String desc, byte kind, byte type) {
		return 1;
	}

	@Override
	public int getMethodRefId(String className, String name, String desc) {
		return 1;
	}

	@Override
	public int getModuleId(String className) {
		return 1;
	}

	@Override
	public int getPackageId(String owner) {
		return 1;
	}

	@Override
	public int getUtfId(CharSequence msg) {
		return 1;
	}

	@Override
	public <T extends Constant> T reset(T c) {
		return c;
	}
}
