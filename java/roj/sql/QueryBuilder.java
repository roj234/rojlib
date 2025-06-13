package roj.sql;

import roj.asmx.injector.Inject;
import roj.asmx.injector.Weave;
import roj.asmx.launcher.Autoload;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.*;
import roj.config.Tokenizer;
import roj.plugins.web.error.GreatErrorPage;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.sql.*;
import java.util.*;

/**
 * Originally written in PHP
 * @author Roj234
 * @since 2023/2/11 1:12
 */
public final class QueryBuilder implements AutoCloseable {
	public static final ThreadLocal<QueryBuilder> INSTANCE = new ThreadLocal<>();

	private static DataSource defaultPool;
	public static void setConnectionPool(ConnectionPool p) {defaultPool = p;}
	private DataSource pool = defaultPool;

	private String group, order;
	private final CharList table = new CharList(), field = new CharList(), where = new CharList(), limit = new CharList();
	private List<String> rawField = Collections.emptyList();

	private Connection connection;
	private Statement defaultStm;
	private ResultSet set;

	final RingBuffer<String> logs;

	private final QueryBuilder parent;

	public static QueryBuilder getInstance() {
		QueryBuilder dba = INSTANCE.get();
		if (dba == null) INSTANCE.set(dba = new QueryBuilder());
		dba.logs.clear();
		return dba.reset();
	}
	public static void closeInstance() throws SQLException {
		QueryBuilder inst = INSTANCE.get();
		if (inst != null) inst.close();
	}

	public static QueryBuilder getInstance(ConnectionPool pool) {
		QueryBuilder dba = new QueryBuilder();
		dba.pool = pool;
		return dba;
	}
	public QueryBuilder copy() {return new QueryBuilder(parent);}

	@Autoload(Autoload.Target.NIXIM)
	@Weave(target = GreatErrorPage.class)
	private static final class JDBCErrorType {
		@Inject(at = Inject.At.TAIL)
		public static void registerCustomTag() {
			GreatErrorPage.addCustomTag("QUERIES", req -> {
				QueryBuilder inst = INSTANCE.get();
				if (inst == null) return null;

				List<String> values = new ArrayList<>(inst.logs);
				IntBiMap<String> index = new IntBiMap<>(values.size());
				for (int i = 0; i < values.size();) index.putInt(i, String.valueOf(++i));
				return new ListMap<>(index, values);
			});
		}
	}

	private QueryBuilder() {
		parent = this;
		logs = new RingBuffer<>(10);
	}
	private QueryBuilder(QueryBuilder parent) {
		this.parent = parent;
		this.logs = parent.logs;
	}

	public synchronized void close() throws SQLException {
		reset();
		try {
			if (isInTrans()) transEnd(false);

			if (defaultStm != null) {
				defaultStm.close();
				defaultStm = null;
				set = null;
			}
		} finally {
			if (connection != null) {
				connection.close();
				connection = null;
			}
		}
	}

	public QueryBuilder reset() {
		group = order = null;
		table.clear();
		field.clear();
		where.clear();
		limit.clear();
		rawField = Collections.emptyList();
		return this;
	}

	public QueryBuilder table(String table) { this.table.clear(); this.table.append('`').append(table).append('`'); return this; }
	public QueryBuilder table(String base, String table) { this.table.clear(); this.table.append(base).append(".`").append(table).append('`'); return this; }

	static final Map<String,String> $myWhere = myWhere(
			"eq"          ,  "=?0",
			"neq"         ,  "<>?0",
			"lt"          ,  "<?0",
			"le"          ,  "<=?0",
			"gt"          ,  ">?0",
			"ge"          ,  ">=?0",
			"like"        ,  "LIKE \"%?0%\"",
			"notlike"     ,  "NOT LIKE \"%?0%\"",
			"leftlike"    ,  "LIKE \"%?0\"",
			"rightlike"   ,  "LIKE \"?0%\"",
			"anybit"      ,  "&?0 != 0",
			"allbit"      ,  "&?0 = ?1",
			"notbit"      ,  "&?0 = 0",
			"between"     ,  "BETWEEN ?0 AND ?1",
			"notbetween"  ,  "NOT BETWEEN ?0 AND ?1",
			"betweeni"    ,  "BETWEEN ?0 AND ?1",
			"notbetweeni" ,  "NOT BETWEEN ?0 AND ?1",
			"fulltext"    ,  "TBD"
	);
	private static Map<String, String> myWhere(String ... a) {
		HashMap<String,String> map = new HashMap<>(a.length/2);
		for (int i = 0; i < a.length;) {
			map.put(a[i++],a[i++]);
		}
		return map;
	}

	public QueryBuilder where(String k) { where.clear(); where.append(k); return this; }
	@Deprecated
	public QueryBuilder where(int id) { return where("Id", Integer.toString(id)); }
	public QueryBuilder where(String k, Object v) { return where(Collections.singletonMap(k,v), false, true); }
	public QueryBuilder andWhere(String k, Object v) { return where(Collections.singletonMap(k,v), false, false); }
	public QueryBuilder andWhere(String k) { where.append(" AND ").append(k); return this; }
	public QueryBuilder where(Map<String,?> map, boolean raw_statement_if_null, boolean clear_where) {
		if (clear_where) where.clear();
		else where.append(" AND ");

		if (map.isEmpty()) return this;

		CharList aa = new CharList();
		CharList bb = new CharList();

		for (Map.Entry<String, ?> entry : map.entrySet()) {
			String k = entry.getKey();

			if (raw_statement_if_null && entry.getValue() == null) {
				where.append(k).append(',');
				continue;
			}

			String v = entry.getValue().toString();

			int i = k.indexOf('|');
			block:
			if (i >= 0) {
				String m = k.substring(i+1);
				if (m.equals("fulltext")) {
					where.append("MATCH(`").append(k).append("`) AGAINST (");
					myescape(where, v).append(" IN BOOLEAN MODE)");
					break block;
				}

				String matcher = $myWhere.get(m);
				if (matcher == null) throw new IllegalArgumentException("$myWhere: missing " + m);

				where.append('`').append(k,0,i).append('`');
				bb.clear(); bb.append(matcher);
				if (matcher.contains("?1")) {
					List<String> component = TextUtil.split(v, '|');
					for (int j = 0; j < component.size(); j++) {
						aa.clear();
						bb.replace("?"+j, m.endsWith("like") ? Tokenizer.escape(aa, component.get(j)) : myescape(aa, component.get(j)));
					}
					if (bb.contains("?"+component.size())) throw new IllegalArgumentException("$myWhere: 缺少组件 " + matcher);
				} else {
					aa.clear();
					bb.replace("?0", m.endsWith("like") ? Tokenizer.escape(aa, v) : myescape(aa, v));
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

	public QueryBuilder rawFieldForSelect(String field) { this.field.clear(); this.field.append(field); this.rawField.clear(); return this; }
	public QueryBuilder field(String field) { rawField = TextUtil.split(field, ','); fieldString(); return this; }
	public QueryBuilder fields(String... field) { rawField = Arrays.asList(field); fieldString(); return this; }
	public QueryBuilder fields(List<String> field) { rawField = field; fieldString(); return this; }

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

	public QueryBuilder order(String order) {
		//if (!order.endsWith("ASC") && !order.endsWith("DESC"))
		this.order = order; return this;
	}

	public QueryBuilder limit(int limit) { this.limit.clear(); this.limit.append(limit); return this; }
	public QueryBuilder limit(int limit, int off) { this.limit.clear(); this.limit.append(limit).append(" OFFSET ").append(off); return this; }
	public QueryBuilder limit(String limit) { this.limit.clear(); this.limit.append(limit); return this; }

	public QueryBuilder group(String group) { this.group = group; return this; }

	private int _fragSize;
	private long _fragOff, _fragTot;
	public QueryBuilder select_paged(int fragment) throws SQLException {
		String sql = makeSelect(true).toStringAndFree();
		logs.ringAddLast(sql);

		if (defaultStm == null) defaultStm = connection().createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);

		set = defaultStm.executeQuery(sql);
		select_index = null;

		_fragSize = fragment;
		_fragOff = 0;
		set.next();
		_fragTot = set.getLong(1);
		if (_fragTot > 0) {
			set.close();
			next_page();
		}
		return this;
	}
	private boolean next_page() throws SQLException {
		if (_fragOff >= _fragTot) return false;

		CharList sb = makeSelect(false)
			.append(" LIMIT ").append(Math.min(_fragSize, _fragTot-_fragOff)).append(" OFFSET ").append(_fragOff);
		_fragOff += _fragSize;

		String sql = sb.toStringAndFree();
		logs.ringAddLast(sql);

		set = defaultStm.executeQuery(sql);
		return true;
	}

	public QueryBuilder select() throws SQLException {
		_fragOff = _fragTot = 0;

		CharList sb = makeSelect(false);
		if (limit.length() > 0) sb.append(" LIMIT ").append(limit);
		return query(sb.toStringAndFree());
	}

	public QueryBuilder query(String sql) throws SQLException {
		logs.ringAddLast(sql);

		if (defaultStm == null) defaultStm = connection().createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
		set = defaultStm.executeQuery(sql);
		select_index = null;

		return this;
	}

	private CharList makeSelect(boolean count) {
		CharList sb = new CharList().append("SELECT ").append(count ? "COUNT(*)" : field.length()==0?"*":field);
		if (table.length() > 0) sb.append(" FROM ").append(table);
		if (where.length() > 0) sb.append(" WHERE ").append(where);
		if (order != null) sb.append(" ORDER BY ").append(order);
		if (group != null) sb.append(" GROUP BY ").append(group);
		return sb;
	}

	public QueryBuilder compiled() throws SQLException {
		throw new SQLException("未实现");
	}

	public QueryBuilder transBegin() throws SQLException {
		if (!connection().getAutoCommit()) throw new SQLException("事务已经开始");
		connection.setAutoCommit(false);
		//connection.setHoldability();
		//connection.setTransactionIsolation();
		return this;
	}
	public QueryBuilder transEnd(boolean commit) throws SQLException {
		if (connection().getAutoCommit()) throw new SQLException("事务没有开始");
		if (commit) connection.commit();
		else connection.rollback();
		connection.setAutoCommit(true);
		return this;
	}
	public boolean isInTrans() throws SQLException { return connection != null && !connection.getAutoCommit(); }

	/**
	 * 读取一条结果
	 */
	public List<String> next() throws SQLException {
		if (set == null) throw new SQLException("not in select mode!");
		List<String> result = new ArrayList<>(set.getMetaData().getColumnCount());
		return next(result) ? result : null;
	}
	/**
	 * 读取一条结果，并存入list
	 */
	public boolean next(List<String> list) throws SQLException {
		ResultSet set = this.set;
		if (!set.next()) {
			set.close();
			if (next_page()) return next(list);
			return false;
		}

		int col = set.getMetaData().getColumnCount();
		for (int i = 1; i <= col; i++) list.add(set.getString(i));
		return true;
	}
	/**
	 * 以关联数组的形式返回一条查询结果
	 */
	public Map<String, String> nextMap() throws SQLException {
		List<String> list = next();
		return list == null ? null : new ListMap<>(select_field_names(), list);
	}

	private IntBiMap<String> select_index;
	public IntBiMap<String> select_field_names() throws SQLException {
		if (select_index != null) return select_index;
		ResultSetMetaData meta = set.getMetaData();
		int count = meta.getColumnCount();
		IntBiMap<String> index = new IntBiMap<>(count);
		for (int i = 0; i < count;) {
			index.forcePut(i, meta.getColumnLabel(++i));
		}
		return select_index = index;
	}

	/**
	 * 读取所有结果中的第一个字段
	 */
	public List<String> getrows_onefield() throws SQLException { return getrows_onefield(0, new ArrayList<>()); }
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
	public Iterable<List<String>> getrows() throws SQLException {
		return () -> new AbstractIterator<>() {
			@Override
			protected boolean computeNext() {
				try {
					if ((result = QueryBuilder.this.next()) != null) return true;
					else set.close();
				} catch (SQLException e) {
					Helpers.athrow(e);
				}
				return false;
			}
		};
	}
	/**
	 * 读取所有结果,作为关联数组存入list
	 */
	public Iterable<Map<String,String>> getrows_colkey() throws SQLException {
		return () -> new AbstractIterator<>() {
			@Override
			protected boolean computeNext() {
				try {
					if ((result = nextMap()) != null) return true;
					else set.close();
				} catch (SQLException e) {
					Helpers.athrow(e);
				}
				return false;
			}
		};
	}
	/**
	 * 读取所有结果,并以第key个值作为主键,value作为子键存入map
	 */
	public Map<String, List<String>> getrows_index(Map<String, List<String>> map, int key) throws SQLException {
		try {
			while (true) {
				List<String> a = next();
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
				Map<String, String> a = nextMap();
				if (a == null) break;

				map.put(set.getString(key+1), a);
			}
		} finally {
			set.close();
		}
		return map;
	}

	public ResultSet result() { return set; }

	public void delete_current() throws SQLException { set.deleteRow(); }
	public void update_current(List<?> values) throws SQLException {
		if (set == null) throw new SQLException("not in select mode");
		if (rawField.isEmpty()) throw new SQLException("field not defined, or not for update");

		if (values.size() < rawField.size()) throw new SQLException("field数量少于values长度");

		IntBiMap<String> id = select_field_names();
		for (int i = 0; i < rawField.size(); i++) {
			int ii = id.getValueOrDefault(rawField.get(i), -1);
			set.updateString(ii+1, values.get(i) == null ? null : values.get(i).toString());
		}

		set.updateRow();
	}

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

	private final IntList affected_ids = new IntList();
	public int affected_id() { return affected_ids.isEmpty() ? -1 : affected_ids.get(0); }
	public IntList affected_ids() { return affected_ids; }

	private int affected_count;
	/**
	 *	返回update,insert,delete上所影响的条数
	 */
	public int affected_rows() { return affected_count; }

	public static final class RawStatement {
		final String s;
		public RawStatement(String s) {this.s = s;}
		public String toString() { return s; }
	}
	// 别用空参数...
	@Deprecated
	public void update() throws SQLException { throw new SQLException(); }
	public int update(Object... values) throws SQLException { return update(Arrays.asList(values)); }
	public int update(List<?> values) throws SQLException {
		if (table.length() == 0) throw new SQLException("table not defined");
		if (rawField.isEmpty()) throw new SQLException("field not defined, or not for update");

		if (values.size() < rawField.size()) throw new SQLException("field数量少于values长度");

		CharList sb = new CharList();
		sb.append("UPDATE ").append(table).append(" SET ");
		int i = 0;
		while (true) {
			sb.append('`').append(rawField.get(i)).append("`=");
			Object o = values.get(i);
			if (o == null) sb.append("NULL");
			else if (o instanceof RawStatement) sb.append(o.toString());
			else myescape(sb, o instanceof CharSequence ? (CharSequence) o : o.toString());
			if (++i == values.size()) break;
			sb.append(",");
		}

		return condition(sb);
	}

	private int condition(CharList sb) throws SQLException {
		if (where.length() > 0) sb.append(" WHERE ").append(where);
		if (order != null) sb.append(" ORDER BY ").append(order);
		if (limit.length() > 0) {
			if (limit.contains("OFFSET")) {
				throw new SQLException("OFFSET 不能和 UPDATE 或 DELETE 共用");
			}
			sb.append(" LIMIT ").append(limit);
		}

		affected_ids.clear();
		return CRUD(sb);
	}

	// 别用空参数...
	@Deprecated
	public void insert() throws SQLException { throw new SQLException(); }
	public boolean insert(Object... values) throws SQLException { return insert(Arrays.asList(values)); }
	public boolean insert(List<?> values) throws SQLException {
		if (table.length() == 0) throw new SQLException("table not defined");
		if (rawField.isEmpty()) throw new SQLException("field not defined, or not for update");

		CharList sb = new CharList();
		sb.append("INSERT INTO ").append(table).append(" (").append(field).append(") VALUES (");
		int i = 0;
		while (true) {
			Object o = values.get(i);
			if (o == null) sb.append("NULL");
			else myescape(sb, o instanceof CharSequence ? (CharSequence) o : o.toString());
			if (++i == values.size()) break;
			sb.append(",");
		}
		sb.append(")");

		affected_ids.clear();
		return CRUD(sb) > 0;
	}
	public int insertMulti(Iterator<List<?>> manyValues, boolean atomicInsert) throws SQLException {
		if (table.length() == 0) throw new SQLException("table not defined");
		if (rawField.isEmpty()) throw new SQLException("field not defined, or not for update");

		List<?> values = manyValues.next();

		CharList sb = new CharList();
		sb.append("INSERT INTO ").append(table).append(" (").append(field).append(") VALUES (");
		for (int i = 0; i < values.size();) {
			sb.append('?');
			if (++i == values.size()) break;
			sb.append(',');
		}

		String sql = sb.append(')').toStringAndFree();
		logs.ringAddLast(sql);

		boolean success = false;
		int successCount = 0;
		try(var stm = connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			while (true) {
				for (int i = 0; i < values.size(); i++) {
					stm.setObject(i+1, values.get(i));
				}
				stm.addBatch();

				if (!manyValues.hasNext()) break;
				values = manyValues.next();
			}

			if (atomicInsert) transBegin();

			int[] rs = stm.executeBatch();
			success = true;
			for (int v : rs) {
				if (v == Statement.EXECUTE_FAILED) {
					success = false;
				} else {
					successCount++;
				}
			}

			pullId(stm);
		} finally {
			if (atomicInsert) transEnd(success);
		}

		return affected_count = successCount;
	}

	public int delete() throws SQLException {
		if (table.length() == 0) throw new SQLException("table not defined");

		CharList sb = new CharList();
		sb.append("DELETE FROM ").append(table);

		return condition(sb);
	}

	// region util

	private int CRUD(CharList sb) throws SQLException {
		String sql = sb.toStringAndFree();
		logs.ringAddLast(sql);

		try (Statement st = connection().createStatement()) {
			affected_count = st.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
			if (affected_count > 0) {
				pullId(st);
			}
			return affected_count;
		}
	}

	private void pullId(Statement st) throws SQLException {
		if (connection().getMetaData().supportsGetGeneratedKeys()) {
			ResultSet set = st.getGeneratedKeys();
			while (set.next()) affected_ids.add(set.getInt(1));
		}
	}

	public Connection connection() throws SQLException {
		if (parent != this) return parent.connection();
		if (connection == null) connection = pool.connect();
		return connection;
	}

	private CharList myescape(CharList sb, CharSequence seq) {
		//FIXME SQL注入
		if (TextUtil.isNumber(seq) != -1) return sb.append(seq);
		return Tokenizer.escape(sb.append('"'), seq).append('"');
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
	public Iterable<Map<String, String>> get_tables_info(String database) throws SQLException {
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
	public Iterable<Map<String, String>> get_fields_info(String database, String table) throws SQLException {
		return reset()
			.table("information_schema","COLUMNS")
			.field("`COLUMN_NAME` as `name`,`DATA_TYPE` as `dtype`,`COLUMN_COMMENT` as `comment`,`COLUMN_TYPE` as `type`,`COLUMN_DEFAULT` as `default_val`,`IS_NULLABLE` as `can_null`,`CHARACTER_MAXIMUM_LENGTH` as `max`")
			.where("TABLE_SCHEMA", database)
			.andWhere("TABLE_NAME", table)
			.order("`ORDINAL_POSITION`")
			.select()
			.getrows_colkey();
	}

	public String lastSql() { return logs.peekLast(); }
	// endregion
}