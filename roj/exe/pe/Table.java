package roj.exe.pe;

/**
 * @author Roj233
 * @since 2022/1/18 20:23
 */
public enum Table {
	EXPORT_TABLE, IMPORT_TABLE, RESOURCE_TABLE, EXCEPTION_TABLE, CERTIFICATE_TABLE, BASE_RELOCATION_TABLE, DEBUG, ARCHITECTURE, GLOBAL_POINTER, THREAD_LOCAL_STORAGE_TABLE, LOAD_CONFIG_TABLE,
	BOUND_IMPORT_TABLE, IMPORT_ADDRESS_TABLE, DELAY_LOAD_IMPORT_TABLE, CLR_RUNTIME_HEADER;

	public static final Table[] VALUES = values();
}
