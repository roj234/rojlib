/** Copyright (c) Roj234 2025/03/06 All rights reserved */
"use strict";
ajaxConfig.dataType = "json";
const prettyTime = ILDate.prettyTime;
const formatSize = ILText.formatSize;
function isMobile() { return navigator.userAgent.match(/.*Mobile.*/); }

const file_types = ["file", "file-text", "file-code", "file-archive", "file-audio", "file-video", "file-image", "file-pdf", "file-excel", "file-powerpoint", "file-word", "files", "folder"];
const file_types_str = ["未知", "文本", "代码", "压缩包", "音频", "视频", "图像", "PDF", "excel", "powerpoint", "word", "多个文件", "文件夹"];

function getIconFromNameAndType(name, type) {
	let icon = 0;
	const extName = name.substring(name.lastIndexOf('.')+1).toLowerCase();
	if (type.startsWith("text/")) {
		icon = 1;
	} else if (["java","js","py","php","html","htm","xhtml","xml","c","cpp","h","hpp","ts","lua","json","yml","yaml","toml","go","cmd","bat","sh"].includes(extName)) {
		icon = 2;
	} else if (["zip","jar","war","7z","rar","gz","tar","lz","lzma","001"].includes(extName)) {
		icon = 3;
	} else if (type.startsWith("audio/")) {
		icon = 4;
	} else if (type.startsWith("video/")) {
		icon = 5;
	} else if (type.startsWith("image/")) {
		icon = 6;
	} else if (type === "application/pdf") {
		icon = 7;
	} else if (["xls","xlsx","csv"].includes(extName)) {
		icon = 8;
	} else if (["ppt","pptx","pot"].includes(extName)) {
		icon = 9;
	} else if (["doc","docx"].includes(extName)) {
		icon = 10;
	} else if (type === "many") {
		icon = 11;
	} else if (type === "folder") {
		icon = 12;
	}
	return icon;
}


const HistoryManager = (function() {
	let history = [];

	const historyVirtualList = new VirtualList({
		element: G("#left-list"),
		itemHeight: 68,
		fixed: true,
		renderer: function (data, i, value) {
			const icon = getIconFromNameAndType(data.file.name, data.file.type);
			return E(`<div class="file-item">
<div class="file-type fa fa-${file_types[icon]}-o" title="文件类型：${file_types_str[icon]}"></div>
<div class="file-desc" title="${data.name}">
${data.name}
<span title="${new Date(data.time).toLocaleString()}">${prettyTime(data.time)} · ${formatSize(data.size)}</span>
</div>
<i class="fa fa-eye" title="查看"></i>
<i class="fa fa-trash-o" title="删除"></i>
</div>`);
		}
	});

	historyVirtualList.dom.addEventListener("click", e => {
		if (!matchesChain(e.target, "i.fa-eye, i.fa-trash-o")) return;
		const id = e.target.parentElement[VirtualList.INDEX];
		if (e.target.matches(".fa-eye")) {
			showHistory(history[id]);
		} else {
			myAjaxGet("../"+history[id].id+"/delete", {}, json => {
				if (json.ok) {
					history.splice(id, 1);
					historyVirtualList.repaint();
				} else {
					userWarning(json.data);
				}
			})
		}
	});

	const rightList = G(".left");
	rightList.style.display = "none";
	let timer = -1;
	let isShowing = false;
	function showList(show) {
		let isShow = rightList.style.left === "0px";
		if (isShow !== show) {
			if (isShowing) return;
			if (show) {
				rightList.style = "";
				requestAnimationFrame(() => {
					rightList.style = "left: 0px";
				})
			} else {
				isShowing = true;
				rightList.style.left = "-350px";
				timer = setTimeout(() => {
					rightList.style.display = "none";
					isShowing = false;
				}, 300);
			}
		}
	}

	A(".log-link").forEach(el => el.addEventListener("click", e => {
		if (historyVirtualList.items) showList(true);
		else myAjaxGet("../list", null, json => {
			if (json.ok) {
				history = historyVirtualList.items = json.data;
				historyVirtualList.repaint();
				showList(true);
			} else {
				userWarning(json.data);
			}
		});
	}));
	G("#left-list-close").addEventListener("click", e => showList(false));

	function showHistory(data, callback) {
		G("#file-detail").style = "";
		G("#file-detail").innerHTML = `<div class="detail-box">
<h2>分享详情 ${data.id}</h2>
<span><span><i class="fa fa-file-o"></i> 分享名称:</span><span>${data.name}</span></span>
<span><span><i class="fa fa-calendar-o"></i> 上传时间:</span><span>${prettyTime(data.time)}</span></span>
<span><span><i class="fa fa-signal"></i> 文件大小:</span><span>${formatSize(data.size)}</span></span>
<span><span><i class="fa fa-clock-o"></i> 过期时间:</span><span>${data.expire > 10000 ? prettyTime(data.expire) : data.expire ? data.expire+"次" : "永久"}</span></span>
<span><span><i class="fa fa-eye"></i> 查看次数:</span><span>${data.view}</span></span>
<span><span><i class="fa fa-download"></i> 下载次数:</span><span>${data.download}</span></span>
</div>
<div class="url-box">
<div>提取码<button id="copyLink">${data.code == null ? "无" : data.code}</button><span>点击复制链接和提取码</span></div>
${isMobile() ? "" : '<div>二维码<canvas id="qrcode" class="qrcode"></canvas><span>扫描二维码快速下载</span></div>'}
</div>
<div class="file-box" style="margin-bottom: 20px"><button class="file-btn" id="closeHistory">关闭</button></div>`;

		const a = document.createElement("a");
		a.href = "../"+data.id+"/"+(data.code == null ? "" : "#!"+data.code);
		const myUrl = a.href;

		if (!isMobile()) {
			const el = G("#qrcode");
			new QRious({
				element: el,
				level: 'L',
				size: el.clientWidth,
				value: myUrl
			});
		}

		G("#copyLink").addEventListener("click", e => {
			e.target.disabled = true;
			navigator.clipboard.writeText(myUrl);
		});

		G("#closeHistory").addEventListener("click", e => {
			G("#file-detail").style.display = "none";
			if (callback) callback();
		});
	}

	return {
		showHistory: showHistory
	};
})();

function matchesChain(element, selector) {
	while (element != null) {
		if (element.matches(selector)) return true;
		element = element.parentElement;
	}
	return false;
}

function myAjaxGet(url, data, callback) {
	sendReq({
		method: data ? "post" : "get",
		success: callback,
	}, url, data, xhr => {
		callback({ok:0,data:"网络错误("+xhr.status+")"})
	});
}

function executeConcurrentLimit(array, callback, limit) {
	return new Promise((ok, fail) => {
		let parallel = 0;
		let index = 0;
		let error = 0;

		async function next() {
			if (index >= array.length) {
				if (parallel === 0) ok(error);
				return;
			}
			const i = index++;
			parallel++;
			try {
				await callback(array, i);
			} catch (e) {
				console.error('发生错误', e);
				error++;
			} finally {
				parallel--;
				next();
			}
		}

		for (let i = 0; i < Math.min(limit, array.length); i++) {
			next();
		}

		if (!array.length) ok(0);
	});
}

const shareId = (() => {
	const pathname = location.pathname;
	return pathname.substring(pathname.lastIndexOf('/', pathname.length-2)+1, pathname.length-1);
})();

if (shareId !== "new") {
	myAjaxGet("info", null, data => {
		if (!data || data.ok === 0) {
			setTimeout(() => {
				location.reload();
			}, 1000);
			return;
		}

		changeTab("get-file");
		setTimeout(() => {
			G("#loading").remove();
		}, 600);

		if (data.ok) {
			okCallback(data.data);
			return;
		}

		//region 解锁后的回调
		function okCallback(data) {
			document.title = data.name + " - " + document.title;
			G("#get-protected").remove();

			const base = G("#get-file");

			G("h2", base).innerText = data.name;
			G(".logo-box > p", base).innerHTML = `
<span><i class="fa fa-signal"></i> ${formatSize(data.size)}</span>
<span title="${new Date(data.time).toLocaleString()}"><i class="fa fa-calendar-o"></i> ${prettyTime(data.time)}上传</span>
<span${data.expire > 10000 ? "title=\""+new Date(data.expire).toLocaleString()+"\"" : ""}><i class="fa fa-clock-o"></i> ${
				data.expire > 100000
					? `${prettyTime(data.expire)}过期`
					: data.expire
						? `还可下载${data.expire}次`
						: `永久`
			}</span>`;

			G(data.files ? "#get-text" : "#get-ok").remove();
			if (!data.files) {
				G("#get-text textarea").value = data.text;
				return;
			}

			if (data.files.length === 1 || (data.expire && data.expire <= 100000))
				G("#get-explore").remove();

			if (data.files.length === 0) {
				G("#get-download").parentElement.remove();
				return;
			}

			for (let i = 0; i < data.files.length; i++) {
				data.files[i].id = i;
			}

			if ("showDirectoryPicker" in window) {
				//region 下载
				function downloadFiles(directoryHandle, progressText) {
					return executeConcurrentLimit(data.files, async (files, i) => {
						const file = files[i];
						const handle = await (await mkdirs(directoryHandle, file.path || "")).getFileHandle(file.name, { create: true });
						await downloadOneFile(files, handle, i);
					}, 8);
				}

				async function mkdirs(rootDirectoryHandle, path) {
					const parts = path.split('/');
					let currentDirectoryHandle = rootDirectoryHandle;

					for (const part of parts) {
						if (part) {
							currentDirectoryHandle = await currentDirectoryHandle.getDirectoryHandle(part, { create: true });
						}
					}

					return currentDirectoryHandle;
				}

				async function downloadOneFile(files, fileHandle, fileId) {
					if (files[fileId].uploading >= 100) return;

					const response = await fetch("file/"+files[fileId].id, {
						headers: {range: "bytes="+(files[fileId].lastKnownPosition ?? 0)+"-"}
					});
					if (response.status > 300) throw new Error("status "+response.status);

					let totalSize = files[fileId].size;
					const reader = response.body.getReader();

					const out = await fileHandle.createWritable({
						keepExistingData: true, 
						mode: "exclusive"
					});

					let downloadedSize = files[fileId].lastKnownPosition ?? 0;
					try {
						if (response.status === 206) {
							if (downloadedSize > 0)
								console.log("断点续传", downloadedSize);
							await out.seek(downloadedSize);
						} else {
							downloadedSize = 0;
						}

						if (downloadedSize === 0) {
							const networkSize = parseInt(response.headers.get('content-length'), 10);
							if (networkSize < totalSize) {
								totalSize = files[fileId].size = networkSize;
							}
						}
						await out.truncate(totalSize);

						let finished = false;
						// 读取并写入数据
						while (!finished) {
							const {value, done} = await reader.read();
							finished = done;
							if (value) {
								downloadedSize += value.length;
								await out.write(value);
								files[fileId].lastKnownPosition = downloadedSize;

								FileManager.updateFileProgress(fileId, (downloadedSize / totalSize) * 100);
							}
						}

						if (downloadedSize >= totalSize) {
							addNotification("已下载"+(files[fileId].path??"")+"/"+files[fileId].name+" ("+formatSize(downloadedSize)+")", 1000);

							delete files[fileId].handle;
							FileManager.updateFileProgress(fileId, 100);
						} else {
							throw new Error("流在完成前中止");
						}
					} catch (e) {
						addNotification("下载失败"+(files[fileId].path??"")+"/"+files[fileId].name+" ("+formatSize(downloadedSize)+")", 5000);
						FileManager.updateFileProgress(fileId, 0);
						throw e;
					} finally {
						await out.close();
					}
				}
				//endregion
				const fileDownloader = async (i) => {
					FileManager.updateFileProgress(i, 0);

					const file = data.files[i];
					let fileHandle = file.handle;
					if (fileHandle == null) file.handle = fileHandle = await window.showSaveFilePicker({ id: "sfs-save-path", suggestedName: file.name, startIn: "downloads" });
					await downloadOneFile(data.files, fileHandle, i);
				};
				FileManager.initDownload(data.files, fileDownloader);

				const downBtn = G("#get-download");
				downBtn.disabled = false;
				downBtn.previousElementSibling.remove();
				if (data.files.length === 1) {
					G("h2", base).innerText = data.files[0].name;

					downBtn.addEventListener('click', async () => {
						downBtn.innerText = "正在下载，请勿关闭本页面";
						downBtn.disabled = true;
						FileManager.setOpen(true);
						try {
							await fileDownloader(0);
							downBtn.innerText = "下载完成";
							FileManager.setOpen(false);
						} catch (e) {
							downBtn.innerText = "下载失败，点击重试";
							downBtn.disabled = false;
						}
					});
				} else {
					let wellKnownFileHandle = null;
					downBtn.addEventListener('click', async () => {
						// 调用 showDirectoryPicker 方法让用户选择目录
						if (wellKnownFileHandle == null) {
							wellKnownFileHandle = await window.showDirectoryPicker({ id: "sfs-save-path", mode: 'readwrite', startIn: "downloads" });
							FileManager.lock();
						}

						// 检查是否有写权限
						const permission = await wellKnownFileHandle.requestPermission({ mode: 'readwrite' });
						if (permission === 'granted') {
							downBtn.innerText = "正在下载，请勿关闭本页面";
							downBtn.disabled = true;
							const error = await downloadFiles(wellKnownFileHandle, downBtn);
							downBtn.innerText = "完成，发生了"+error+"个错误";
							if (error) {
								downBtn.disabled = false;
							} else {
								FileManager.setOpen(false);
							}
						} else {
							console.log('未获得目录写权限');
						}
					});

				}
			} else {
				const fileDownloader = async (i) => {
					const a = document.createElement("a");
					a.href = "file/"+i;
					a.download = data.files[i].name;
					a.click();
				};
				FileManager.initDownload(data.files, fileDownloader);
			}
		}
		//endregion

		G("#get-file h2").innerText = data.data;
		A("#get-ok,#get-text").forEach(el => el.style.display = "none");
		const passwordForm = G("#get-protected");
		if (!data.exist) {
			passwordForm.remove();
			return;
		}
		passwordForm.addEventListener("submit", e => {
			e.preventDefault();
			e.submitter.disabled = true;
			const codeInput = e.target["code"];

			myAjaxGet("info", {
				code: codeInput.value
			}, json => {
				if (!json.ok) {
					report(json.data);
				} else {
					changeTab("get-file");
					setTimeout(() => {
						okCallback(json.data);
						A("#get-ok,#get-text").forEach(el => el.style.display = "");
					}, 300);
				}
			});

			function report(msg) {
				e.submitter.disabled = false;
				codeInput.setCustomValidity(msg);
				codeInput.reportValidity();
				setTimeout(() => {
					codeInput.setCustomValidity('');
				}, 800);
			}
		});

		if (location.hash.startsWith('#!')) {
			passwordForm.elements.code.value = location.hash.substring(2);
			passwordForm.requestSubmit(passwordForm.submit);
		}
	});
} else {
	changeTab("upload-form");
	setTimeout(() => {
		G("#loading").remove();
	}, 600);

	A(".send-input input").forEach(el => {
		el.addEventListener("change", e => {
			A(".send-box").forEach(el => el.style.display = "none");
			A(".send-box input, .send-box textarea").forEach(el => el.disabled = true);
			G("#"+e.target.id+"-box").style = "";
			G("#"+e.target.id+"-box input, #"+e.target.id+"-box textarea").disabled = false;
		})
	});

	G("#expire").addEventListener("change", e => {
		A(".expire-box input").forEach(el => el.disabled = true);
		G("#expire-"+e.target.value).disabled = false;
	});
	const time1 = G("#expire-time");
	var localTime = new Date().getTimezoneOffset() * 60000;
	time1.min = new Date(Date.now() + 60000 - localTime).toISOString().substring(0, 16);
	time1.value = new Date(Date.now() + 86400000 - localTime).toISOString().substring(0, 16);
	time1.max = new Date(Date.now() + 86400000 * 31 - localTime).toISOString().substring(0, 16);
}

function changeTab(id) {
	A(".main:not(#file-detail)").forEach(el => {
		el.classList.add("fadeOut");
	})
	const classList = G("#"+id).classList;
	classList.remove("fadeOut");
	classList.add("fadeIn");
	setTimeout(() => {
		classList.remove("fadeIn");
	}, 600);
}

const FileManager = (function () {
	let files = [];
	let fileSizeSum = 0;
	let downloadCallback = null;

	const NOTHING = document.createElement("div");
	const virtualFileList = new VirtualList({
		element: G("#right-list"),
		itemHeight: 68,
		data: files,
		fixed: false,
		renderer: function (data, i, value) {
			let type = getIconFromNameAndType(data.name, data.type);

			if (data.uploading >= 100) return NOTHING;
			return E(`<div id="file${i}" class="file-item-wrapper${data.uploading!=null?" uploading":""}"><div class="file-item">
<div class="progress" style="width: ${data.uploading}%"></div>
<div class="file-type fa fa-${file_types[type]}-o" title="文件类型：${file_types_str[type]}"></div>
<div class="file-desc" title="${data.name}">
${data.name}
<span title="${(data.lastModifiedDate ?? new Date(data.lastModified)).toLocaleString()}">${prettyTime(data.lastModified)} · ${formatSize(data.size)}</span>
</div>
${downloadCallback == null ? '' : `<i class="fa fa-download" title="下载" role="button"></i>`}
<i class="fa fa-trash-o" title="删除" role="button"></i>
</div></div>`);
		}
	});

	const leftList = G(".right");
	leftList.style.display = "none";
	let timer = -1;
	let isShowing = false;
	function showList(show) {
		let isShow = leftList.style.right === "0px";
		if (isShow !== show) {
			if (isShowing) return;
			if (show) {
				leftList.style = "";
				requestAnimationFrame(() => {
					leftList.style = "right: 0px";
				})
			} else {
				isShowing = true;
				if (isMobile()) {
					leftList.style.right = "-75%";
					isShowing = false;
				} else {
					leftList.style.right = "-350px";
					timer = setTimeout(() => {
						leftList.style.display = "none";
						isShowing = false;
					}, 300);
				}
			}
		}
	}
	leftList.style.display = "none";
	if (isMobile()) {
		G(".wrapper").addEventListener("click", e => FileManager.setOpen(matchesChain(e.target, ".right")));
	}

	var box = G("#send-file-box");
	box.addEventListener("click", e => {
		G("#val-file").click();
	});
	box.addEventListener("dragenter", e => {
		box.classList.add("dragover");
	});
	box.addEventListener("dragover", e => {
		e.cancelable && e.preventDefault();
	});
	box.addEventListener("dragleave", e => {
		box.classList.remove("dragover");
	});
	box.addEventListener("drop", e => {
		e.cancelable && e.preventDefault();
		box.classList.remove("dragover");

		try {
			const fileSystemEntry = e.dataTransfer.items[0].webkitGetAsEntry();
			if (fileSystemEntry.isDirectory) {
				traverseDirectory(fileSystemEntry, "");
				return;
			}
		} catch (e) {}

		const files = e.dataTransfer.files;
		for (let i = 0; i < files.length; i++) {
			addFile(files[i]);
		}
	});

	// 遍历子文件夹
	async function traverseDirectory(entry, path) {
		for (const file1 of await readDirectory(entry.createReader())) {
			if (file1.isDirectory) {
				traverseDirectory(file1, (path?path+"/":"")+file1.name);
			} else {
				readFile(file1).then(file => {
					file.path = path;
					addFile(file);
				});
			}
		}
	}
	function readDirectory(reader) {return new Promise(reader.readEntries.bind(reader));}
	function readFile(entry) {return new Promise(entry.file.bind(entry));}

	G("#val-file").addEventListener("change", e => {
		var files = e.target.files;
		for (var i = 0; i < files.length; i++) {
			addFile(files[i]);
		}
		e.target.value = "";
	});

	function addFile(file) {
		files.push(file);
		showList(true);
		virtualFileList.repaint();

		fileSizeSum += file.size;
		updateFileUploadString();
	}

	virtualFileList.dom.addEventListener("click", e => {
		if (!matchesChain(e.target, "i.fa-trash-o, i.fa-download")) return;
		const id = e.target.parentElement.parentElement[VirtualList.INDEX];

		if (e.target.matches("i.fa-download")) {
			downloadCallback(id);
			return;
		}

		fileSizeSum -= files.splice(id, 1)[0].size;
		if (files.length === 0) showList(false);
		else virtualFileList.repaint();

		updateFileUploadString();
	});

	function updateFileUploadString() {
		G("#val-file-text").innerText = `拖动或单击以上传\n您已选择${formatSize(fileSizeSum)}文件`;
	}

	let scheduleRepaint;
	function updateFileProgress(i, progress) {
		if (progress > 100) return;

		files[i].uploading = progress;
		const el = virtualFileList.dom.querySelector("#file"+i);

		if (progress === 100) {
			if (el != null) el.style.height = 0;
			files[i][VirtualList.HEIGHT] = 0;
			files[i][VirtualList.INDEX] = -1;
			clearTimeout(scheduleRepaint);
			scheduleRepaint = setTimeout(() => {
				virtualFileList.repaint();
			}, 150);
		}

		if (el == null) return;

		el.classList.add("uploading");
		el.querySelector(".progress").style.width = progress+"%";
	}

	return {
		setOpen: showList,
		initDownload: function (items, callback) {
			files = items;
			downloadCallback = callback;
			virtualFileList.items = items;
			showList(items.length > 1);
			virtualFileList.repaint(true);
		},
		lock: function() {
			for (let i = 0; i < files.length; i++) {
				if (files[i].uploading == null)
					files[i].uploading = 0;
			}
			virtualFileList.repaint(true);
			box.classList.add("disabled");
		},
		updateFileProgress: updateFileProgress,

		getFiles: () => files
	}
})();

function userWarning(s) {addNotification(s, 3000);}

const title = document.title;
G("#upload-form").addEventListener("submit", e => {
	e.preventDefault();

	const form = e.target.elements;

	const obj = {
		id: form.id.value,
		name: form.name.value,
		type: form["send-type"].value,
		code: form.code.value
	};
	switch (form.expire.value) {
		case "time": obj.expire = new Date(form["expire-time"].value).getTime(); break;
		case "count": obj.expire = form["expire-count"].value; break;
		case "never": obj.expire = "never"; break;
	}
	switch (obj.type) {
		case "text": obj.text = form["val-text"].value; break;
		case "folder": obj.path = form["val-folder"].value; break;
		case "file":
			if (!FileManager.getFiles().length) {
				userWarning("请选择文件");
				return;
			}
		break;
	}

	e.submitter.disabled = true;
	myAjaxGet("", obj, json => {
		if (!json.ok) {
			e.submitter.disabled = false;
			userWarning(json.data);
			return;
		}

		for (const el of form) {
			el.disabled = true;
		}

		FileManager.lock();
		const shareId = json.data.id;

		//region uploadAllFiles
		const uploadHandle = (event) => {
			// Cancel the event as stated by the standard.
			event.preventDefault();
			// Chrome requires returnValue to be set.
			event.returnValue = "";
		};
		function uploadAllFiles() {
			window.addEventListener("beforeunload", uploadHandle);

			return executeConcurrentLimit(FileManager.getFiles(), async (files, i) => {
				const file = files[i];
				if (file.uploading === 100) return;

				const ajaxCallback = async json => {
					let fragmentArray;
					const FRAGMENT_SIZE = json.fragment;

					if (!file._uploadId) {
						if (false === json.ok) {
							userWarning("服务器拒绝上传"+file.name+":"+json.data);
							FileManager.updateFileProgress(i, 100);
							return;
						}
						if (!json.ok) throw "创建上传任务失败:"+json;

						file._uploadId = json.id;
						file._uploadFragment = json.fragment;
						file._uploadCompleted = 0;
						fragmentArray = {length: parseInt((file.size + FRAGMENT_SIZE - 1) / FRAGMENT_SIZE)};
						console.log("开始上传任务", json.id);
					} else {
						await requestChunkRemain();
						console.log("继续上传任务", json.id);
					}

					let failure;
					while (true) {
						failure = await executeConcurrentLimit(fragmentArray, async (array, j) => {
							const fragment = array[j] ?? j;
							const response = await fetch('../upload/'+json.id+'/'+fragment, {
								method: 'POST',
								headers: {'Content-Type': 'application/octet-stream'},
								body: file.slice(FRAGMENT_SIZE * fragment, Math.min(FRAGMENT_SIZE * (fragment+1), file.size)) // 直接发送二进制数据
							});

							if (response.ok) {
								document.title = "上传中 ("+i+"/"+files.length+") - "+title;
								FileManager.updateFileProgress(i, Math.min(100, (++file._uploadCompleted * FRAGMENT_SIZE) / file.size * 100));
							} else {
								throw "分块上传失败:"+response.statusText;
							}
						}, json.threads);

						if (!failure) break;

						await requestChunkRemain();
					}

					function requestChunkRemain() {
						return new Promise((ok, fail) => {
							sendReq({
								success: (json) => {
									fragmentArray = json.data;
									ok();
								},
							}, '../upload/'+json.id+'/status', null, xhr => {
								fail("请求上传状态失败:"+xhr.status);
							});
						});
					}

					FileManager.updateFileProgress(i, 100);
					console.log("完成上传任务", json.id);
				};

				if (file._uploadId) {
					await ajaxCallback({
						ok: true,
						id: file._uploadId,
						fragment: file._uploadFragment
					});
				} else {
					await new Promise((ok, fail) => {
						sendReq({
							method: "post",
							success: (json) => {
								ajaxCallback(json).then(ok, fail)
							},
						}, "../"+shareId+"/add", {
							name: file.name,
							path: file.path ?? "",
							size: file.size,
							lastModified: file.lastModified
						}, xhr => {
							fail("网络错误("+xhr.status+")");
						});
					});
				}
			}, json.threads);
		}
		//endregion

		const uploadFileCallback = errorCount => {
			if (errorCount) {
				let count = 4;
				let timer = setInterval(() => {
					e.submitter.innerText = errorCount+"个文件上传失败，"+ --count +"秒后重试";
					if (count === 0) {
						e.submitter.innerText = "正在重新上传";
						clearInterval(timer);
						uploadAllFiles().then(uploadFileCallback);
					}
				}, 1000);
				return;
			}
			myAjaxGet("../"+shareId+"/submit", {}, json2 => {
				window.removeEventListener("beforeunload", uploadHandle);
				document.title = "上传成功 - "+title;
				FileManager.setOpen(false);
				if (!json2.ok) {
					e.submitter.innerText = "上传失败，请刷新页面重试";
					return;
				}
				HistoryManager.showHistory(json2.data, () => location.reload());
			});
		};

		if (obj.type === "file") {
			uploadAllFiles().then(uploadFileCallback);
		} else {
			HistoryManager.showHistory(json.data, () => location.reload());
		}
	});
});