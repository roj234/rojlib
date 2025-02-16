package roj.plugin;

import java.io.File;

/**
 * @author Roj234
 * @since 2025/3/11 0011 0:14
 */
public interface PermissionHolder {
	PermissionHolder NONE = new PermissionHolder() {
		@Override public int getId() {return 0;}
		@Override public String getName() {return "guest";}
		@Override public String getGroupName() {return "guest";}
		@Override public int getPermissionFlags(String permission) {return 0;}
	};

	int getId();
	String getName();
	String getGroupName();

	default boolean hasPermission(String permission) {return getPermissionFlags(permission) > 0;}
	int getPermissionFlags(String permission);

	default boolean isReadable(String path) {return (getPermissionFlags("io/"+path.replace(File.separatorChar, '/'))&(FILE_READ|FILE_WRITE)) != 0;}
	default boolean isWritable(String path) {return (getPermissionFlags("io/"+path.replace(File.separatorChar, '/'))&FILE_WRITE) != 0;}

	int FILE_READ = 1, FILE_WRITE = 2;
}
