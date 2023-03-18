package roj.net.ddns;

import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.TaskExecutor;
import roj.concurrent.task.AsyncTask;
import roj.concurrent.task.ITask;
import roj.config.JSONParser;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.crypt.HMAC;
import roj.io.IOUtil;
import roj.net.URIUtil;
import roj.net.http.SyncHttpClient;
import roj.text.*;
import roj.ui.CmdUtil;
import roj.util.Helpers;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author Roj234
 * @since 2023/1/27 0027 19:03
 */
public class Aliyun implements DDNSService {
	private static final String API_URL = "https://alidns.aliyuncs.com";
	private static final String DEFAULT_API_VERSION = "2015-01-09";
	private static final String DEFAULT_SIGNATURE_METHOD = "HMAC-SHA1";
	private static final String DEFAULT_SIGNATURE_VERSION = "1.0";

	private String AccessKeyId, AccessKeySecret;
	private Random rnd = new SecureRandom();

	private static final HttpPool pool = new HttpPool(8, 5000);

	private URL makeUrl(Map<String, String> param) {
		Map<String, String> queries = new MyHashMap<>(param);
		queries.put("Format", "JSON");
		queries.put("Version", DEFAULT_API_VERSION);
		queries.put("SignatureMethod", DEFAULT_SIGNATURE_METHOD);
		queries.put("SignatureVersion", DEFAULT_SIGNATURE_VERSION);
		queries.put("AccessKeyId", AccessKeyId);

		byte[] nonce = new byte[16];
		rnd.nextBytes(nonce);
		queries.put("SignatureNonce", IOUtil.SharedCoder.get().encodeHex(nonce));
		queries.put("Timestamp", new ACalendar(null).formatDate("Y-m-dTH:i:sP", System.currentTimeMillis()).toString());

		//计算签名
		String signature = makeSign(queries, AccessKeySecret);

		try {
			return new URL(API_URL+"/?"+makeQuery(queries)+"&Signature="+signature);
		} catch (MalformedURLException e) {
			// should not happen!
			return Helpers.nonnull();
		}
	}
	private AsyncTask<CMapping> execute(URL url) {
		return new AsyncTask<CMapping>() {
			@Override
			protected CMapping invoke() throws Exception {
				SyncHttpClient client = pool.request(url, null);
				return JSONParser.parses(new StreamReader(client.getInputStream(), StandardCharsets.UTF_8)).asMap();
			}
		};
	}

	private String makeQuery(Map<String, String> queries) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> p : queries.entrySet()) {
			sb.append("&").append(URIUtil.encodeURIComponent(p.getKey()))
			  .append("=").append(URIUtil.encodeURIComponent(p.getValue()));
		}
		return sb.substring(1);
	}
	private static String makeSign(Map<String, String> queries, String accessSecret) {
		SimpleList<Map.Entry<String, String>> sorted = new SimpleList<>(queries.entrySet());
		sorted.sort((o1, o2) -> o1.getKey().compareTo(o2.getKey()));

		StringBuilder sb = new StringBuilder();
		String s = "";
		for (Map.Entry<String, String> p : sorted) {
			sb.append(s).append(encodeSignature(p.getKey()))
			  .append("=").append(encodeSignature(p.getValue()));
			s = "&";
		}
		String signStr = "GET" + "&%2F&" + encodeSignature(sb);

		try {
			UTFCoder uc = IOUtil.SharedCoder.get();

			HMAC hmac = new HMAC(MessageDigest.getInstance("SHA-1"));
			hmac.setSignKey(accessSecret.concat("&").getBytes(StandardCharsets.UTF_8));

			hmac.update(uc.encode(signStr));
			return encodeSignature(uc.encodeBase64(hmac.digestShared())).toString();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	private static final MyBitSet aliyun_pass = MyBitSet.from("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~");
	private static CharList encodeSignature(CharSequence src) {
		CharList out = IOUtil.getSharedCharBuf();
		byte[] data = src.toString().getBytes(StandardCharsets.UTF_8);
		for (int i = 0; i < data.length; i++) {
			int j = data[i] & 0xFF;
			if (aliyun_pass.contains(j)) out.append((char) j);
			else out.append('%').append(TextUtil.b2h(j>>>4)).append(TextUtil.b2h(j&0xF));
		}
		return out;
	}

	private Map<String, DDnsRecord> domain2Id = new MyHashMap<>();
	static final class DDnsRecord {
		String domain, RR;
		InetAddress v4Addr, v6Addr;
		Object v4Id, v6Id;
	}

	private final TaskExecutor th = new TaskExecutor();

	@Override
	public void loadConfig(CMapping config) {
		AccessKeyId = config.getString("AccessKey");
		AccessKeySecret = config.getString("AccessSecret");
	}

	@Override
	public void init(Iterable<Map.Entry<String, List<String>>> managed) throws Exception {
		th.start();

		Map<String, String> par = new MyHashMap<>();
		par.put("Action", "DescribeDomainRecords");
		// *域名名称
		// string DomainName
		// 当前页数，起始值为1，默认为1
		// int PageNumber = 1;
		// 分页查询时设置的每页行数，最大值500，默认为20
		// int PageSize = 20;
		// 主机记录的关键字，按照”%RRKeyWord%”模式搜索，不区分大小写
		// string RRKeyWord
		// 解析类型的关键字，按照全匹配搜索，不区分大小写
		// string TypeKeyWord = "A";
		// 记录值的关键字，按照”%ValueKeyWord%”模式搜索，不区分大小写
		// string ValueKeyWord

		for (Map.Entry<String, List<String>> entry : managed) {
			par.put("DomainName", entry.getKey());
			par.put("TypeKeyWord", "A");
			par.put("PageSize", "100");

			List<String> subDomains = entry.getValue();
			for (int i = 0; i < subDomains.size(); i++) {
				String s = subDomains.get(0);
				int pos = s.lastIndexOf(entry.getKey())-1;
				if (pos < 0) System.out.println("Invalid sub domain " + s);
				else {
					DDnsRecord record = new DDnsRecord();
					record.domain = entry.getKey();
					record.RR = s.substring(0, pos);
					domain2Id.put(subDomains.get(i), record);
				}
			}

			if (subDomains.size() == 1) {
				String s = subDomains.get(0);
				int pos = s.lastIndexOf(entry.getKey())-1;
				if (pos > 0) par.put("RRKeyWord", s.substring(0, pos));
			}

			th.pushTask(_init(par));

			par.put("TypeKeyWord", "AAAA");
			th.pushTask(_init(par));
		}

		th.waitFor();
		th.shutdown();
	}

	@Override
	public void update(Iterable<Map.Entry<String, InetAddress[]>> changed) {
		for (Map.Entry<String, InetAddress[]> entry : changed) {
			DDnsRecord record = domain2Id.get(entry.getKey());
			if (record == null) domain2Id.put(entry.getKey(), record = new DDnsRecord());

			InetAddress[] addr = entry.getValue();
			if (addr[0] != null && !addr[0].equals(record.v4Addr)) {
				record.v4Addr = addr[0];
				if (record.v4Id == null) {
					_add(record.domain, record.RR, addr[0].getHostAddress(), "A");
				} else {
					_update(record.v4Id, record.RR, addr[0].getHostAddress(), "A");
				}
			}

			if (addr[1] != null && !addr[1].equals(record.v6Addr)) {
				record.v6Addr = addr[1];
				if (record.v6Id == null) {
					_add(record.domain, record.RR, addr[1].getHostAddress(), "AAAA");
				} else {
					_update(record.v6Id, record.RR, addr[1].getHostAddress(), "AAAA");
				}
			}
		}

		// ==DeleteDomainRecord==
		// 解析记录的ID，此参数在添加解析时会返回，在获取域名解析列表时会返回
		// string RecordId

		// ==SetDomainRecordStatus==
		// 解析记录的ID，此参数在添加解析时会返回，在获取域名解析列表时会返回
		// string RecordId
		// 是否启用, Enable: 启用解析 Disable: 暂停解析
		// string Enable

		// ==UpdateDomainRecord==
		// 解析记录的ID，此参数在添加解析时会返回，在获取域名解析列表时会返回
		// string RecordId
		// 主机记录，如果要解析@.exmaple.com，主机记录要填写"@”，而不是空
		// string RR
		// 解析记录类型，参见解析记录类型格式(https://help.aliyun.com/document_detail/29805.html?spm=a2c4g.11186623.2.19.29f17d8ciNDiKK)
		// string Type = "A";
		// 记录值
		// string Value { get; set; }
		// 生存时间，默认为600秒（10分钟），参见TTL定义说明(https://help.aliyun.com/document_detail/29806.html?spm=a2c4g.11186623.2.20.29f17d8cFvRltO)
		// int TTL { get; set; } = 600;
		// MX记录的优先级，取值范围[1,10]，记录类型为MX记录时，此参数必须
		// int Priority { get; set; }
		// 解析线路，默认为default。参见解析线路枚举(https://help.aliyun.com/document_detail/29807.html?spm=a2c4g.11186623.2.21.29f17d8ciNDiKK)
		// string Line

		// ==AddDomainRecord==
		// 解析记录的ID，此参数在添加解析时会返回，在获取域名解析列表时会返回
		// string DomainName
		// 其余参数同上
	}

	@Override
	public void cleanup() {
		th.shutdown();
	}

	private void _add(String domain, String RR, String addr, String type) {
		Map<String, String> par = new MyHashMap<>();
		par.put("Action", "AddDomainRecord");
		par.put("DomainName", domain);
		par.put("RR", RR);
		par.put("Value", addr);
		par.put("Type", type);

		try {
			SyncHttpClient shc = pool.request(makeUrl(par), null);
			CMapping cfg = _parse(shc);
		} catch (Exception e) {
			CmdUtil.error("请求参数: " + par, e);
		}
	}

	private void _update(Object id, String RR, String addr, String type) {
		Map<String, String> par = new MyHashMap<>();
		par.put("Action", "UpdateDomainRecord");
		par.put("RecordId", id.toString());
		par.put("RR", RR);
		par.put("Value", addr);
		par.put("Type", type);

		try {
			SyncHttpClient shc = pool.request(makeUrl(par), null);
			CMapping cfg = _parse(shc);
		} catch (Exception e) {
			CmdUtil.error("请求参数: " + par, e);
		}
	}

	private CMapping _parse(SyncHttpClient shc) throws Exception {
		CMapping map = JSONParser.parses(new StreamReader(shc.getInputStream(), StandardCharsets.UTF_8)).asMap();
		if (map.containsKey("Message"))
			throw new IllegalArgumentException("API错误: " + map.getString("Message"));
		return map;
	}

	private ITask _init(Map<String, String> param) {
		URL url = makeUrl(param);
		return () -> {
			CMapping cfg = _parse(pool.request(url, null));
			System.out.println(cfg);
			CList list = cfg.getDot("DomainRecords.Record").asList();
			for (int i = 0; i < list.size(); i++) {
				CMapping data = list.get(i).asMap();
				String name = data.getString("RR")+"."+data.getString("DomainName");

				DDnsRecord record = domain2Id.get(name);
				if (record != null) {
					switch (data.getString("Type")) {
						case "A":
							record.v4Addr = InetAddress.getByName(data.getString("Value"));
							record.v4Id = data.getString("RecordId");
							break;
						case "AAAA":
							record.v6Addr = InetAddress.getByName(data.getString("Value"));
							record.v6Id = data.getString("RecordId");
							break;
					}
				}
			}
		};
	}
}
