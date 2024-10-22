package roj.asmx.nixim;

import roj.asm.Opcodes;

/**
 * @author Roj234
 * @since 2023/10/9 0009 19:30
 */
public class Pcd {
	public static final ThreadLocal<Boolean> REVERSE = new ThreadLocal<>();

	public static final int
		SHADOW = 0x01000000, COPY = 0x02000000, INJECT = 0x04000000,
		REAL_ADD_FINAL = 0x10000000, REAL_DEL_FINAL = 0x20000000;

	public String name, desc;
	public String mapOwner, mapName;
	public int mode;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Pcd pcd = (Pcd) o;

		if (!desc.equals(pcd.desc)) return false;
		if (REVERSE.get() != null) {
			if (!mapOwner.equals(pcd.mapOwner)) return false;
			return mapName.equals(pcd.mapName);
		}
		return name.equals(pcd.name);
	}

	@Override
	public int hashCode() {
		int result = desc.hashCode();
		if (REVERSE.get() != null) {
			result = 31 * result + mapOwner.hashCode();
			result = 31 * result + mapName.hashCode();
		} else {
			result = 31 * result + name.hashCode();
		}
		return result;
	}

	@Override
	public String toString() {
		boolean m = desc.startsWith("(");
		return Opcodes.showModifiers(mode&0xFF, m? Opcodes.ACC_SHOW_METHOD : Opcodes.ACC_SHOW_FIELD)+
			(m?"方法":"字段")+" 0x"+Integer.toHexString(mode&0xFF00)+
			"["+name+(mapName!=null&&!mapName.equals(name)?(" => "+(mapOwner!=null?mapOwner+'.':"")+mapName):"")+"] "+desc;
	}
}