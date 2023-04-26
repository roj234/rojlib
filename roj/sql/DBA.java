package roj.sql;

import roj.collect.IntBiMap;
import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.collect.SimpleList;
import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.net.http.srv.error.GreatErrorPage;
import roj.text.CharList;
import roj.text.TextUtil;

import java.sql.*;
import java.util.*;

/**
 * Originally written in PHP
 * @author Roj234
 * @since 2023/2/11 0011 1:12
 */
public final class DBA implements AutoCloseable {
	public static final ThreadLocal<DBA> CMAP = new ThreadLocal<>();

	private static SimpleConnectionPool pool;
	public static void setConnectionPool(SimpleConnectionPool p) { pool = p; }

	private String group, order;
	private final CharList table = new CharList(), field = new CharList(), where = new CharList(), limit = new CharList();
	private List<String> rawField = Collections.emptyList();

	private Connection connection;
	private Statement selectStm;
	private ResultSet set;

	RingBuffer<String> logs = new RingBuffer<>(10);

	public static DBA getInstance() {
		DBA dba = CMAP.get();
		if (dba == null) CMAP.set(dba = new DBA());
		dba.logs.clear();
		return dba.reset();
	}

	static {
		GreatErrorPage.addCustomTag("QUERIES", req -> {
			DBA inst = CMAP.get();
			if (inst == null) return null;

			List<String> values = new SimpleList<>(inst.logs);
			IntBiMap<String> index = new IntBiMap<>(values.size());
			for (int i = 0; i < values.size();) index.putInt(i, String.valueOf(++i));
			return new ListMap(index, values);
		});
	}

	public DBA() {}

	public synchronized void close() throws SQLException {
		reset();
		try {
			if (selectStm != null) {
				selectStm.close();
				selectStm = null;
				set = null;
			}
		} finally {
			if (connection != null) {
				connection.close();
				connection = null;
			}
		}
	}

	public DBA reset() {
		group = order = null;
		table.clear();
		field.clear();
		where.clear();
		limit.clear();
		rawField = Collections.emptyList();
		return this;
	}

	public DBA table(String table) { this.table.clear(); this.table.append('`').append(table).append('`'); return this; }
	public DBA table(String base, String table) { this.table.clear(); this.table.append(base).append(".`").append(table).append('`'); return this; }

	static final Map<String,String> $myWhere = myWhere(
			"eq"          ,  "=?0",
			"neq"         ,  "<>?0",
			"lt"          ,  "<?0",
			"le"          ,  "<=?0",
			"gt"          ,  ">?0",
			"ge"          ,  ">=?0",
			"like"        ,  "LIKE %?0%",
			"notlike"     ,  "NOT LIKE %?0%",
			"leftlike"    ,  "LIKE %?0",
			"rightlike"   ,  "LIKE ?0%",
			"bit"         ,  "&?0 != 0",
			"nbit"        ,  "&?0 = 0",
			"between"     ,  "BETWEEN ?0 AND ?1",
			"notbetween"  ,  "NOT BETWEEN ?0 AND ?1",
			"betweeni"    ,  "BETWEEN ?0 AND ?1",
			"notbetweeni" ,  "NOT BETWEEN ?0 AND ?1",
			"FT"          ,  "MATCH(`?0`) AGAINST(\"?1\" IN BOOLEAN MODE)"
	);
	private static Map<String, String> myWhere(String ... a) {
		MyHashMap<String,String> map = new MyHashMap<>(a.length/2);
		for (int i = 0; i < a.length;) {
			map.put(a[i++],a[i++]);
		}
		return map;
	}

	public DBA where(String k) { this.where.clear(); this.where.append(k); return this; }
	public DBA where(String k, String v) { return where(Collections.singletonMap(k,v), false, true); }
	private DBA andWhere(String k, String v) { return where(Collections.singletonMap(k,v), false, false); }
	public DBA where(Map<String,String> map, boolean value_not_null, boolean clear_where) {
		if (clear_where) where.clear();
		else where.append(" AND ");

		if (map.isEmpty()) return this;

		CharList aa = new CharList();
		CharList bb = new CharList();

		for (Map.Entry<String, String> entry : map.entrySet()) {
			String k = entry.getKey();
			String v = entry.getValue();

			if (value_not_null && v == null) {
				where.append(k).append(',');
				continue;
			}

			int i = k.indexOf('|');
			if (i >= 0) {
				where.append('`').append(k,0,i).append('`');

				String m = k.substring(i+1);
				String matcher = $myWhere.get(m);
				if (matcher == null) throw new IllegalArgumentException("$myWhere: missing " + m);

				bb.clear(); bb.append(matcher);
				if (matcher.contains("?1")) {
					List<String> component = TextUtil.split(v, '|');
					for (int j = 0; j < component.size(); j++) {
						aa.clear();
						bb.replace("?"+j, myescape(aa,component.get(j)));
					}
					if (bb.contains("?"+component.size())) throw new IllegalArgumentException("$myWhere: 缺少组件 " + matcher);
				} else {
					aa.clear();
					bb.replace("?0", myescape(aa, v));
				}

				where.append(bb);
			} else {
				where.append('`').append(k).append("`=");
				myescape(where, v);
			}
			where.append(" AND ");
		}

		aa._free(); bb._free();
		where.setLength(where.length()-5);
		return this;
	}

	public DBA field(String field) { rawField = TextUtil.split(field, ","); fieldString(); return this; }
	public DBA fields(String... field) { rawField = Arrays.asList(field); fieldString(); return this; }
	public DBA fields(List<String> field) { rawField = field; fieldString(); return this; }

	private void fieldString() {
		field.clear();

		if (rawField.isEmpty()) {
			rawField = Collections.emptyList();
			return;
		}

		if (rawField.get(0).trim().equals("*")) {
			field.append("*");
			rawField = Collections.emptyList();
			return;
		}

		CharList sb = field.append('`');
		int i = 0;
		while (true) {
			String s = rawField.get(i).trim();
			rawField.set(i++, s);
			sb.append(s);
			if (i == rawField.size()) break;
			sb.append("`,`");
		}
		sb.append('`');
	}

	public DBA order(String order) {
		//if (!order.endsWith("ASC") && !order.endsWith("DESC"))
		this.order = order; return this;
	}

	public DBA limit(int limit) { this.limit.clear(); this.limit.append(limit); return this; }
	public DBA limit(int limit, int off) { this.limit.clear(); this.limit.append(limit).append(" OFFSET ").append(off); return this; }
	public DBA limit(String limit) { this.limit.clear(); this.limit.append(limit); return this; }

	public DBA group(String group) { this.group = group; return this; }

	private int _fragSize;
	private long _fragOff, _fragTot;
	public DBA select_paged(int fragment) throws SQLException {
		String sql = makeSelect(true).toStringAndFree();
		logs.ringAddLast(sql);

		if (selectStm == null) selectStm = connection().createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
		try (ResultSet set = selectStm.executeQuery(sql)) {
			_fragSize = fragment;
			_fragOff = 0;
			set.next();
			_fragTot = set.getLong(1);
		}

		select_index = null;
		next_page();
		return this;
	}
	public boolean next_page() throws SQLException {
		if (_fragOff >= _fragTot) return false;

		CharList sb = makeSelect(false)
			.append(" LIMIT ").append(Math.min(_fragSize, _fragTot-_fragOff)).append(" OFFSET ").append(_fragOff);
		_fragOff += _fragSize;

		String sql = sb.toStringAndFree();
		logs.ringAddLast(sql);

		set = selectStm.executeQuery(sql);
		return true;
	}

	public DBA select() throws SQLException {
		_fragOff = _fragTot = 0;

		CharList sb = makeSelect(false);
		if (limit.length() > 0) sb.append(" LIMIT ").append(limit);

		String sql = sb.toStringAndFree();
		logs.ringAddLast(sql);

		if (selectStm == null) selectStm = connection().createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
		set = selectStm.executeQuery(sql);
		select_index = null;
		return this;
	}

	private CharList makeSelect(boolean count) {
		CharList sb = IOUtil.ddLayeredCharBuf().append("SELECT ").append(count ? "COUNT(*)" : field.length()==0?"*":field);
		if (table.length() > 0) sb.append(" FROM ").append(table);
		if (where.length() > 0) sb.append(" WHERE ").append(where);
		if (order != null) sb.append(" ORDER BY ").append(order);
		if (group != null) sb.append(" GROUP BY ").append(group);
		return sb;
	}

	public DBA compiled() throws SQLException {
		throw new SQLException("未实现");
	}

	public DBA transBegin() throws SQLException {
		if (!connection().getAutoCommit()) throw new SQLException("事务已经开始");
		connection.setAutoCommit(false);
		//connection.setHoldability();
		//connection.setTransactionIsolation();
		return this;
	}
	public DBA transEnd(boolean commit) throws SQLException {
		if (connection().getAutoCommit()) throw new SQLException("事务没有开始");
		if (commit) connection.commit();
		else connection.rollback();
		connection.setAutoCommit(true);
		return this;
	}
	public boolean isInTrans() throws SQLException { return !connection().getAutoCommit(); }

	/**
	 * 读取一条结果
	 */
	public List<String> getone() throws SQLException {
		List<String> result = new SimpleList<>(set.getMetaData().getColumnCount());
		return getone(result) ? result : null;
	}
	/**
	 * 读取一条结果，并存入list
	 */
	public boolean getone(List<String> list) throws SQLException {
		ResultSet set = this.set;
		if (!set.next()) {
			set.close();
			if (next_page()) return getone(list);
			return false;
		}

		int col = set.getMetaData().getColumnCount();
		for (int i = 1; i <= col; i++) list.add(set.getString(i));
		return true;
	}
	/**
	 * 以关联数组的形式返回一条查询结果
	 */
	public Map<String, String> getone_colkey() throws SQLException {
		List<String> list = getone();
		return list == null ? null : new ListMap(select_field_names(), list);
	}

	private IntBiMap<String> select_index;
	public IntBiMap<String> select_field_names() throws SQLException {
		if (select_index != null) return select_index;
		ResultSetMetaData meta = set.getMetaData();
		int count = meta.getColumnCount();
		IntBiMap<String> index = new IntBiMap<>(count);
		for (int i = 0; i < count;) {
			index.putInt(i, meta.getColumnLabel(++i));
		}
		return select_index = index;
	}

	/**
	 * 读取所有结果中的第一个字段
	 */
	public List<String> getrows_onefield() throws SQLException { return getrows_onefield(0, new SimpleList<>()); }
	/**
	 * 读取所有结果中的第index个字段, 并存入list
	 */
	public List<String> getrows_onefield(int index, List<String> result) throws SQLException {
		index++;
		try {
			while (set.next()) {
				result.add(set.getString(index));
			}
		} finally {
			set.close();
		}
		return result;
	}

	/**
	 * 读取所有结果,并存入list
	 */
	public List<List<String>> getrows(List<List<String>> list) throws SQLException {
		try {
			while (true) {
				List<String> a = getone();
				if (a == null) break;

				list.add(a);
			}
		} finally {
			set.close();
		}
		return list;
	}
	/**
	 * 读取所有结果,作为关联数组存入list
	 */
	public List<Map<String,String>> getrows_colkey() throws SQLException {
		return getrows_colkey(new SimpleList<>());
	}
	/**
	 * 读取所有结果,作为关联数组存入list
	 */
	public List<Map<String,String>> getrows_colkey(List<Map<String,String>> list) throws SQLException {
		try {
			while (true) {
				Map<String, String> a = getone_colkey();
				if (a == null) break;

				list.add(a);
			}
		} finally {
			set.close();
		}
		return list;
	}
	/**
	 * 读取所有结果,并以第key个值作为主键,value作为子键存入map
	 */
	public Map<String, List<String>> getrows_index(Map<String, List<String>> map, int key) throws SQLException {
		try {
			while (true) {
				List<String> a = getone();
				if (a == null) break;

				map.put(set.getString(key+1), a);
			}
		} finally {
			set.close();
		}
		return map;
	}
	/**
	 * 读取所有结果,并以第key个值作为主键,value作为关联数组存入map
	 */
	public Map<String, Map<String, String>> getrows_index_colkey(Map<String, Map<String, String>> map, int key) throws SQLException {
		try {
			while (true) {
				Map<String, String> a = getone_colkey();
				if (a == null) break;

				map.put(set.getString(key+1), a);
			}
		} finally {
			set.close();
		}
		return map;
	}

	public ResultSet result() { return set; }

	private int getsyscount(String $lx) throws SQLException {
		try (Statement stm = connection().createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)) {
			ResultSet rs = stm.executeQuery("SELECT "+$lx+"()");
			return rs.next() ? rs.getInt(0) : 0;
		}
	}

	/**
	 *	返回使用SQL_CALC_FOUND_ROWS，统计总记录数
	 */
	public int found_rows() throws SQLException { return getsyscount("found_rows"); }

	private int lastAffected;
	/**
	 *	返回update,insert,delete上所影响的条数
	 */
	public int affected_rows() { return lastAffected; }

	public int update(List<CharSequence> values) throws SQLException {
		if (table.length() == 0) throw new SQLException("table not defined");
		if (field.length() == 0) throw new SQLException("field not defined");

		if (values.size() < rawField.size()) throw new SQLException("field数量少于values长度");

		CharList sb = IOUtil.ddLayeredCharBuf();
		sb.append("UPDATE ").append(table).append(" SET ");
		int i = 0;
		while (true) {
			sb.append('`').append(rawField.get(i)).append("`=\"");
			myescape(sb, values.get(i));
			if (++i == values.size()) break;
			sb.append(",");
		}

		if (where.length() > 0) sb.append(" WHERE ").append(where);
		if (order != null) sb.append(" ORDER BY ").append(order);
		if (limit.length() > 0) {
			if (limit.contains("OFFSET")) {
				throw new SQLException("OFFSET 不能和 UPDATE 共用");
			}
			sb.append(" LIMIT ").append(limit);
		}

		return CRUD(sb);
	}

	public boolean insert(List<CharSequence> values) throws SQLException {
		if (table.length() == 0) throw new SQLException("table not defined");
		if (field.length() == 0) throw new SQLException("field not defined");

		CharList sb = IOUtil.ddLayeredCharBuf();
		sb.append("INSERT INTO ").append(table).append(" (").append(field).append(") VALUES (");
		int i = 0;
		while (true) {
			myescape(sb, values.get(i));
			if (++i == values.size()) break;
			sb.append(",");
		}
		sb.append(")");

		return CRUD(sb) > 0;
	}
	public int insertMulti(Iterator<List<CharSequence>> manyValues, boolean atomicInsert) throws SQLException {
		if (table.length() == 0) throw new SQLException("table not defined");
		if (field.length() == 0) throw new SQLException("field not defined");

		Statement stm = connection().createStatement();
		if (atomicInsert) transBegin();

		CharList sb = IOUtil.ddLayeredCharBuf();
		sb.append("INSERT INTO ").append(table).append(" (").append(field).append(") VALUES ");

		int headerLen = sb.length();
		boolean success = false;
		int successCount = 0;

		int j = 0;
		try {
			while (true) {
				List<CharSequence> values = manyValues.next();

				sb.append('(');
				int i = 0;
				while (true) {
					myescape(sb, values.get(i));
					if (++i == values.size()) break;
					sb.append(',');
				}
				sb.append(')');

				if (sb.length() > 262144 || !manyValues.hasNext()) {
					String sql = sb.toString();
					logs.ringAddLast(sql);
					successCount += selectStm.executeUpdate(sql);
					sb.setLength(headerLen);
					if (!manyValues.hasNext()) break;
					continue;
				}

				sb.append(",\n");
			}
		} finally {
			sb._free();
			selectStm.close();

			if (atomicInsert) transEnd(success);
		}

		return lastAffected = successCount;
	}

	public int delete() throws SQLException {
		if (table.length() == 0) throw new SQLException("table not defined");

		CharList sb = IOUtil.ddLayeredCharBuf();
		sb.append("DELETE FROM ").append(table);

		if (where.length() > 0) sb.append(" WHERE ").append(where);
		if (order != null) sb.append(" ORDER BY ").append(order);
		if (limit.length() > 0) {
			if (limit.contains("OFFSET")) {
				throw new SQLException("OFFSET 不能和 UPDATE 共用");
			}
			sb.append(" LIMIT ").append(limit);
		}

		return CRUD(sb);
	}

	// region util

	private int CRUD(CharList sb) throws SQLException {
		String sql = sb.toStringAndFree();
		logs.ringAddLast(sql);

		try (Statement st = connection().createStatement()) {
			return lastAffected = st.executeUpdate(sql);
		}
	}

	public Connection connection() throws SQLException {
		if (connection == null) {
			connection = pool.getConnection(1000);
			if (connection == null) throw new SQLException("failed to get connection in 1000ms");
		}
		return connection;
	}

	private CharList myescape(CharList sb, CharSequence seq) {
		if (TextUtil.isNumber(seq) != -1) return sb.append(seq);
		return ITokenizer.addSlashes(sb.append('"'), seq).append('"');
	}

	// endregion
	// region management

	/**
	 返回此数据库所有的表
	 */
	public List<String> get_tables_name(String database) throws SQLException {
		return reset()
			.table("information_schema","TABLES")
			.fields("TABLE_NAME")
			.where("TABLE_SCHEMA", database)
			.select()
			.getrows_onefield();
	}
	/**
	 * 列出数据库中的表的详细信息
	 */
	public List<Map<String, String>> get_tables_info(String database) throws SQLException {
		return reset()
			.table("information_schema","TABLES")
			.field("`TABLE_NAME` as `name`,`ENGINE` as `engine`,`TABLE_ROWS` as `length`,`DATA_LENGTH` as `data_length`,`INDEX_LENGTH` as `index_length`,`CREATE_TIME` as `time`,`UPDATE_TIME` as `modify_time`")
			.where("TABLE_SCHEMA", database)
			.andWhere("TABLE_TYPE", "BASE TABLE")
			.select()
			.getrows_colkey();
	}

	/**
	 返回某表中所有字段
	 */
	public List<String> get_fields_name(String database, String table) throws SQLException {
		return reset()
			.table("information_schema","COLUMNS")
			.fields("COLUMN_NAME")
			.where("TABLE_SCHEMA", database)
			.andWhere("TABLE_NAME", table)
			.select()
			.getrows_onefield();
	}
	/**
	 *	获取表中每个字段的详细数据
	 *
	 *		name => 名字
	 *		dtype => 数据类型
	 *		comment => 注释
	 *		type => 详细类型
	 *		default_val => 默认值
	 *		can_null => 能否为NULL
	 *		max => 数字型的最大大小
	 */
	public List<Map<String, String>> get_fields_info(String database, String table) throws SQLException {
		return reset()
			.table("information_schema","COLUMNS")
			.field("`COLUMN_NAME` as `name`,`DATA_TYPE` as `dtype`,`COLUMN_COMMENT` as `comment`,`COLUMN_TYPE` as `type`,`COLUMN_DEFAULT` as `default_val`,`IS_NULLABLE` as `can_null`,`CHARACTER_MAXIMUM_LENGTH` as `max`")
			.where("TABLE_SCHEMA", database)
			.andWhere("TABLE_NAME", table)
			.order("`ORDINAL_POSITION`")
			.select()
			.getrows_colkey();
	}
	// endregion
}