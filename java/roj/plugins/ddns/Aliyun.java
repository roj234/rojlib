package roj.plugins.ddns;

import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.Task;
import roj.concurrent.TaskThread;
import roj.config.JSONParser;
import roj.config.data.CList;
import roj.config.data.CMap;
import roj.crypt.HMAC;
import roj.http.HttpClient;
import roj.http.HttpRequest;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.DateParser;
import roj.text.Escape;
import roj.ui.Terminal;
import roj.util.ByteList;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static roj.plugins.ddns.IpGetter.pooledRequest;

/**
 * @author Roj234
 * @since 2023/1/27 19:03
 */
final class Aliyun implements DDNSService {
	private static final String API_URL = "https://alidns.aliyuncs.com";
	private static final String DEFAULT_API_VERSION = "2015-01-09";
	private static final String DEFAULT_SIGNATURE_METHOD = "HMAC-SHA1";
	private static final String DEFAULT_SIGNATURE_VERSION = "1.0";

	private String AccessKeyId, AccessKeySecret;
	private final Random rnd = new SecureRandom();

	private String makeUrl(Map<String, String> param) {
		Map<String, String> queries = new MyHashMap<>(param);
		queries.put("Format", "JSON");
		queries.put("Version", DEFAULT_API_VERSION);
		queries.put("SignatureMethod", DEFAULT_SIGNATURE_METHOD);
		queries.put("SignatureVersion", DEFAULT_SIGNATURE_VERSION);
		queries.put("AccessKeyId", AccessKeyId);

		byte[] nonce = new byte[16];
		rnd.nextBytes(nonce);
		queries.put("SignatureNonce", IOUtil.encodeHex(nonce));
		queries.put("Timestamp", DateParser.GMT().format("Y-m-dTH:i:sP", System.currentTimeMillis()).toString());

		//计算签名
		String signature = makeSign(queries, AccessKeySecret);

		return API_URL+"/?"+makeQuery(queries)+"&Signature="+signature;
	}

	private static String makeQuery(Map<String, String> queries) {
		var sb = IOUtil.getSharedCharBuf();
		for (Map.Entry<String, String> p : queries.entrySet()) {
			sb.append("&").append(Escape.encodeURIComponent(p.getKey()))
			  .append("=").append(Escape.encodeURIComponent(p.getValue()));
		}
		return sb.substring(1);
	}
	private static String makeSign(Map<String, String> queries, String accessSecret) {
		SimpleList<Map.Entry<String, String>> sorted = new SimpleList<>(queries.entrySet());
		sorted.sort((o1, o2) -> o1.getKey().compareTo(o2.getKey()));

		ByteList sb = new ByteList();
		String s = "";
		for (Map.Entry<String, String> p : sorted) {
			sb.putAscii(s).putAscii(encodeSignature(p.getKey()))
			  .putAscii("=").putAscii(encodeSignature(p.getValue()));
			s = "&";
		}
		String signStr = "GET&%2F&"+encodeSignature(sb);

		try {
			HMAC hmac = new HMAC(MessageDigest.getInstance("SHA-1"));
			hmac.init(accessSecret.concat("&").getBytes(StandardCharsets.UTF_8));

			hmac.update(IOUtil.encodeUTF8(signStr));
			return encodeSignature(IOUtil.encodeBase64(hmac.digestShared())).toString();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private static final MyBitSet aliyun_pass = MyBitSet.from("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.~");
	private static CharList encodeSignature(CharSequence src) {return Escape.escape(IOUtil.getSharedCharBuf(), src instanceof ByteList b ? b : IOUtil.getSharedByteBuf().putUTFData(src), aliyun_pass);}

	private Map<String, DDnsRecord> domain2Id = new MyHashMap<>();
	static final class DDnsRecord {
		String domain, RR;
		InetAddress v4Addr, v6Addr;
		Object v4Id, v6Id;
	}

	private final TaskThread th = new TaskThread();

	@Override
	public void loadConfig(CMap config) {
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

			th.submit(_init(par));

			par.put("TypeKeyWord", "AAAA");
			th.submit(_init(par));
		}

		th.shutdown();
		th.awaitTermination();
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
		// 解析记录类型，参见解析记录类型格式(https://help.aliyun.com/document_detail/29805.html)
		// string Type = "A";
		// 记录值
		// string Value { get; set; }
		// 生存时间，默认为600秒（10分钟），参见TTL定义说明(https://help.aliyun.com/document_detail/29806.html)
		// int TTL { get; set; } = 600;
		// MX记录的优先级，取值范围[1,10]，记录类型为MX记录时，此参数必须
		// int Priority { get; set; }
		// 解析线路，默认为default。参见解析线路枚举(https://help.aliyun.com/document_detail/29807.html)
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
			HttpClient shc = HttpRequest.builder().url(makeUrl(par)).executePooled();
			CMap cfg = _parse(shc);
		} catch (Exception e) {
			Terminal.error("请求参数: " + par, e);
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
			CMap cfg = _parse(pooledRequest(makeUrl(par)));
		} catch (Exception e) {
			Terminal.error("请求参数: " + par, e);
		}
	}

	private CMap _parse(HttpClient shc) throws Exception {
		CMap map = new JSONParser().parse(shc.stream()).asMap();
		if (map.containsKey("Message"))
			throw new IllegalArgumentException("API错误: " + map.getString("Message"));
		return map;
	}

	private Task _init(Map<String, String> param) {
		var url = makeUrl(param);
		return () -> {
			CMap cfg = _parse(pooledRequest(url));
			CList list = cfg.getDot("DomainRecords.Record").asList();
			for (int i = 0; i < list.size(); i++) {
				CMap data = list.get(i).asMap();
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