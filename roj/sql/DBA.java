package roj.sql;

import java.sql.ResultSet;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/2/11 0011 1:12
 */
public final class DBA {
	public static final ThreadLocal<DbConnector> connection = new ThreadLocal<>();

	private String table, where, field, limit, order, group;
	private ResultSet result;

	private DBA(String table) {
		this.table = table;
	}

	public static DBA table(String table) {
		return new DBA(table);
	}

	public DBA where(String k) {
		this.where = k;
		return this;
	}
	public DBA where(Map<String,String> v) {
		return this;
	}
	public DBA where(String k, String v) {
		return this;
	}

	public DBA field(String field) {
		this.field = field;
		return this;
	}

	public DBA order(String order) {
		this.order = order;
		return this;
	}

	public DBA limit(int cnt, int off) {
		this.limit = cnt+" OFFSET "+off;
		return this;
	}
	public DBA limit(String limit) {
		this.limit = limit;
		return this;
	}

	public DBA group(String group) {
		this.group = group;
		return this;
	}

/*	public DBA select() {
		this.result = this.db.query(this.db.get_select([
			'table'	=> this.table, 'where'	=> this.where,
			'fields'=> this.field, 'order'	=> this.order,
			'limit'	=> this.limit, 'group'	=> this.group
		]));
		return this;
	}

	public DBA query(sql) {
		this.result = this.db.query(sql);
		return this;
	}

	public DBA getOne(index=false) {
		res = this.result;
		if(res) {
			d = this.db.fetch_array(res,index?1:0);
			this.db.release(res);
			this.result = null;
			return d;
		}
		return null;
	}

	public DBA toArray(index=false) {
		res = this.result;
		if(res) {
			d = this.db.fetch_all(res,index?1:0);
			this.db.release(res);
			this.result = null;
			return d;
		}
		return null;
	}

	public DBA asIterator() {
		return new DbItr(this.db, this.result);
	}

	public DBA update(v) {
		if (strpos(this.limit, 'OFFSET') !== false)
			throw new Exception("DbError: OFFSET cannot be used in UPDATE query");
		this.result = this.db.update(this.table,v,this.where,this.order,this.limit);
		return this;
	}

	public DBA insert(v) {
		this.result = this.db.insert(this.table,v);
		return this;
	}

	public DBA delete() {
		if (strpos(this.limit, 'OFFSET') !== false)
			throw new Exception("DbError: OFFSET cannot be used in DELETE query");
		this.result = this.db.delete(this.table,this.where,this.order,this.limit);
		return this;
	}

	public DBA result() {
		return this.result;
	}*/
}