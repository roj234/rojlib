
const customDataTypeRegistry = {};

/**
 * AJAX配置对象
 * @typedef {Object} AjaxConfig
 * @property {string} [method="get"] - 请求方法：get/post
 * @property {number} [timeout=30000] - 请求超时时间（毫秒）
 * @property {string} [dataType="text"] - 响应数据类型：text/json
 * @property {string} [responseType="text"] - XMLHttpRequest的responseType
 * @property {Object} [header={}] - 请求头对象
 * @property {string} [url] 请求地址
 * @property {Function} [success] - 请求成功回调函数
 * @property {Function} [error] - 请求失败回调函数
 */
const ajaxConfig = {
	prefix: "",
	method: "get",
	timeout: 30000,
	dataType: "text",
	responseType: "json",
	success: () => {},
	error: (xhr) => {console.error("Network Error:"+xhr.statusText)}
};

/**
 * 发送AJAX请求
 * @param {string|AjaxConfig} url - 请求URL地址或配置对象
 * @param {Object|string} [data] - 请求数据，对象或字符串格式
 * @param {Function} [onSuccess=config.success] - 成功回调函数，接收xhr数据和statusText
 * @param {Function} [onError=config.error] - 错误回调函数，接收xhr对象和错误信息
 * @returns {void}
 *
 * @example
 * // 基本GET请求
 * sendReq('/api/data', null, function(xhr, error) {
 *     if (error) console.error('Error:', error);
 *     else console.log('Success:', xhr.response);
 * });
 *
 * @example
 * // POST请求带数据
 * sendReq('/api/submit', {name: 'John'});
 *
 * @example
 * // 带成功回调的配置
 * sendReq({
 *     method: 'get',
 *     dataType: 'json',
 *     success: function(data, status) {
 *         console.log('Data received:', data);
 *     }
 * }, '/api/users', null, errorCallback);
 */
function ajax(url, data, onSuccess, onError) {
	if (typeof url === 'string') url = {url}
	const config = Object.assign(Object.create(ajaxConfig), url);
	if (!data) data = config.data;
	if (onSuccess) config.success = onSuccess;
	if (onError) config.error = onError;

	let hasError = false;
	const xhr = new XMLHttpRequest();

	xhr.responseType = config.responseType;
	xhr.timeout = config.timeout;

	xhr.onerror = function(e) {
		if (!hasError) {
			//xhr.abort();
			config.error(xhr, e);
			hasError = true;
		}
	};

	xhr.onreadystatechange = function() {
		if (xhr.readyState === xhr.DONE) {
			if (xhr.status < 400) {
				switch (config.dataType) {
					case "text":
						config.success(xhr.response, xhr.statusText);
						break;
					case "json":
						try {
							const jsonResponse = JSON.parse(xhr.response);
							config.success(jsonResponse, xhr.statusText);
						} catch (e) {
							console.log("decode error", e);
							if (!hasError) {
								config.error(xhr, e);
								hasError = true;
							}
						}
						break;
					default:
						const handler = customDataTypeRegistry[config.dataType];
						handler(xhr,config);
				}
			} else {
				//xhr.abort();
				config.error(xhr, xhr.status);
				hasError = true;
			}
		}
	};

	try {
		xhr.open(config.method, config.prefix+config.url);

		const headers = config.header;
		for (const key in headers) {
			xhr.setRequestHeader(key, headers[key]);
		}

		if (config.method === "post") {
			let postData;
			if (Object.prototype.toString.call(data) !== "[object Object]") {
				postData = data;
			} else {
				if (typeof data === "object") {
					const entries = Object.entries(data);
					postData = "";
					for (const [key, value] of entries) {
						postData += encodeURIComponent(key) + "=" + encodeURIComponent(value) + "&";
					}
					if (postData.length > 0) {
						postData = postData.substring(0, postData.length - 1);
					}
				}
				xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
			}
			xhr.send(postData);
		} else {
			xhr.send(null);
		}
	} catch (e) {
		if (!hasError) {
			//xhr.abort();
			onError(xhr, e);
			hasError = true;
		}
	}
}


function asyncAjax(config) {
	if (typeof(config) == "string") {
		config = { url: config };
	}

	return new Promise(function(ok, fail) {
		config.success = ok;
		config.error = fail;
		ajax(config);
	});
}


export { ajaxConfig, ajax, asyncAjax, customDataTypeRegistry };