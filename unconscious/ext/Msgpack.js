/*! MsgPack encode & decode.
 *  Author: Roj234 @ 2025/04/24, All rights reserved
 */
// Usage: MsgPack.encodeMsg(object)
// Usage: MsgPack.decodeMsg(array | TypedArray | Buffer | DataView, {
//     bigint: false,
//     multiple: false,
//     decodeExt: (dataView, type: number, offset: number, length: number) => any | exception,
//     schema: ["fieldName" | ["fieldName", schema]]
// })

"use strict";

/**
 * @typedef {(string | MsgpackSechma)[]} MsgpackSechma
 */

/**
 * @typedef {Object} MsgpackDecodeOptions
 * @property {boolean} [bigint=false] - 使用bigint或double
 * @property {MsgpackSechma} [schema=null] - 省略数据布局
 * @property {function(dataView:DataView,type:number,offset:number,length:number):any} decodeExt - 扩展类型解码函数
 * @property {boolean} [multiple=false] - 一次解码多个对象
 */

/**
 *
 * @param {Array|TypedArray|Buffer|DataView} input
 * @param {MsgpackDecodeOptions} [options=null]
 * @returns any | any[]
 */
function decodeMsg(input, options) {
	if (Array.isArray(input)) input = new Uint8Array(input);
	if (ArrayBuffer.isView(input) || (typeof Buffer !== "undefined" && Buffer.isBuffer(input))) input = new DataView(input.buffer, input.byteOffset, input.byteLength);
	else if (!input instanceof DataView) throw new Error("不支持的输入: "+input);

	if (options?.multiple) {
		const arr = [];
		let offset = 0, result;
		while (offset < input.byteLength) {
			[result, offset] = decodeRawMsg(input, offset, options);
			arr.push(result);
		}
		return arr;
	} else {
		return decodeRawMsg(input, 0, options)[0];
	}
}

const LOOKUP = new Uint8Array(256);
for (let i = 0; i <= 0x7F; i++) LOOKUP[i] = i;
for (let i = 0x80; i <= 0x8F; i++) LOOKUP[i] = 0xBD;
for (let i = 0x90; i <= 0x9F; i++) LOOKUP[i] = 0xBE;
for (let i = 0xA0; i <= 0xBF; i++) LOOKUP[i] = 0xBF;
for (let i = 0xC0; i <= 0xFF; i++) LOOKUP[i] = i;

/**
 * MsgPack decode
 * @param {DataView} dataView the buffer
 * @param {number} offset start offset
 * @param {MsgpackDecodeOptions} [options=null] extra options like no_schema
 * @returns {[any, number]} the result
 */
function decodeRawMsg(dataView, offset, options) {
	const bigint = options?.bigint ?? false;
	const decodeExtUser = options?.decodeExt ?? ((dataView, type, offset, length) => {
		//return data;
		throw new Error("自定义类型: 0x"+type.toString(16));
	});

	let currSchema = options?.schema;
	const decodeMap = currSchema ? decodeMapSchema : decodeMapNormal;

	return [decode(), offset];

	function decode() {
		const tagByte = dataView.getInt8(offset++);

		switch (LOOKUP[tagByte&0xFF]) {
			case 0xC0: return null;
			case 0xC1: throw new Error(`0xC1`);
			case 0xC2: return false;
			case 0xC3: return true;
			case 0xC4: {
				const len = dataView.getUint8(offset);
				offset++;
				const value = new Uint8Array(dataView.buffer, offset, len);
				offset += len;
				return value;
			}
			case 0xC5: {
				const len = dataView.getUint16(offset);
				offset += 2;
				const value = new Uint8Array(dataView.buffer, offset, len);
				offset += len;
				return value;
			}
			case 0xC6: {
				const len = dataView.getUint32(offset);
				offset += 4;
				const value = new Uint8Array(dataView.buffer, offset, len);
				offset += len;
				return value;
			}
			case 0xCA: {
				const value = dataView.getFloat32(offset);
				offset += 4;
				return value;
			}
			case 0xCB: {
				const value = dataView.getFloat64(offset);
				offset += 8;
				return value;
			}
			case 0xCC: {
				const value = dataView.getUint8(offset);
				offset++;
				return value;
			}
			case 0xCD: {
				const value = dataView.getUint16(offset);
				offset += 2;
				return value;
			}
			case 0xCE: {
				const value = dataView.getUint32(offset);
				offset += 4;
				return value;
			}
			case 0xCF: {
				const value = dataView.getBigUint64(offset);
				offset += 8;
				return bigint ? value : Number(value);
			}
			case 0xD0: {
				const value = dataView.getInt8(offset);
				offset++;
				return value;
			}
			case 0xD1: {
				const value = dataView.getInt16(offset);
				offset += 2;
				return value;
			}
			case 0xD2: {
				const value = dataView.getInt32(offset);
				offset += 4;
				return value;
			}
			case 0xD3: {
				const value = dataView.getBigInt64(offset);
				offset += 8;
				return bigint ? value : Number(value);
			}
			case 0xBF: {
				const len = tagByte & 0x1F;
				return readUTF(len);
			}
			case 0xD9: {
				const len = dataView.getUint8(offset);
				offset++;
				return readUTF(len);
			}
			case 0xDA: {
				const len = dataView.getUint16(offset);
				offset += 2;
				return readUTF(len);
			}
			case 0xDB: {
				const len = dataView.getUint32(offset);
				offset += 4;
				return readUTF(len);
			}
			case 0xBE: {
				const size = tagByte & 0x0F;
				return decodeArray(size);
			}
			case 0xDC: {
				const size = dataView.getUint16(offset);
				offset += 2;
				return decodeArray(size);
			}
			case 0xDD: {
				const size = dataView.getUint32(offset);
				offset += 4;
				return decodeArray(size);
			}
			case 0xBD: {
				const size = tagByte & 0x0F;
				return decodeMap(size);
			}
			case 0xDE: {
				const size = dataView.getUint16(offset);
				offset += 2;
				return decodeMap(size);
			}
			case 0xDF: {
				const size =dataView.getUint32(offset);
				offset += 4;
				return decodeMap(size);
			}

			case 0xD4: case 0xD5: case 0xD6: case 0xD7: case 0xD8: {
				return decodeExt(1 << (tagByte - 0xD4));
			}
			case 0xC7: {
				const len = dataView.getUint8(offset);
				offset++;
				return decodeExt(len);
			}
			case 0xC8: {
				const len = dataView.getUint16(offset);
				offset += 2;
				return decodeExt(len);
			}
			case 0xC9: {
				const len = dataView.getUint32(offset);
				offset += 4;
				return decodeExt(len);
			}

			default: return tagByte;
		}
	}
	function decodeArray(size) {
		const arr = Array(size);
		for (let i = 0; i < size; i++) {
			arr[i] = decode();
		}
		return arr;
	}
	function decodeMapSchema(size) {
		const obj = {};
		const schema = currSchema;

		for (let i = 0; i < size; i++) {
			let key = decodeMapKey();
			if (typeof key === "number") {
				key = schema[key];
				if (Array.isArray(key)) {
					currSchema = key[1];
					key = key[0];
				}
			}

			obj[key] = decode();
		}

		currSchema = schema;
		return obj;
	}
	function decodeMapNormal(size) {
		const obj = {};

		for (let i = 0; i < size; i++) {
			const key = decodeMapKey();
			obj[key] = decode();
		}
		return obj;
	}
	function decodeMapKey() {
		const tagByte = dataView.getInt8(offset++);
		let len;
		switch (LOOKUP[tagByte&0xFF]) {
			case 0xCC:
				len = dataView.getUint8(offset);
				offset++;
				return len;
			case 0xCD:
				len = dataView.getUint16(offset);
				offset += 2;
				return len;
			case 0xD0:
				len = dataView.getInt8(offset);
				offset ++;
				return len;
			case 0xD1:
				len = dataView.getInt16(offset);
				offset += 2;
				return len;
			case 0xD2:
				len = dataView.getInt32(offset);
				offset += 4;
				return len;

			case 0xBF: len = tagByte & 0x1F; break;
			case 0xD9:
				len = dataView.getUint8(offset);
				offset++;
				break;
			case 0xDA:
				len = dataView.getUint16(offset);
				offset += 2;
				break;
			case 0xDB:
				len = dataView.getUint32(offset);
				offset += 4;
				break;
			default:
				if (tagByte > 0x7F && tagByte <= 0xDF) throw new Error('键必须是字符串或整数: 0x'+tagByte.toString(16));
				return tagByte;
		}

		return readUTF(len);
	}
	function decodeExt(length)	{
		const extType = dataView.getInt8(offset++);
		let result;
		if (extType === -1) {
			result = decodeTimestamp(offset, length);
		} else {
			result = decodeExtUser(dataView, extType, offset, length);
		}

		offset += length;
		return result;
	}
	function decodeTimestamp(offset, dataLen) {
		switch (dataLen) {
			case 4: {
				const seconds = dataView.getUint32(offset);
				return new Date(seconds * 1000);
			}
			case 8: {
				const data = dataView.getBigUint64(offset);
				const nanoseconds = Number(data >> 34n);
				const seconds = Number(data & 0x3FFFFFFFFn);
				return new Date(seconds * 1000 + Math.floor(nanoseconds / 1e6));
			}
			case 12: {
				const nanoseconds = dataView.getUint32(offset);
				offset += 4;
				const seconds = dataView.getBigInt64(offset);
				return new Date(Number(seconds) * 1000 + Math.floor(nanoseconds / 1e6));
			}
			default:
				throw new Error(`时间戳长度无效: ${dataLen}`);
		}
	}

	function readUTF(length) {
		const utf = decodeUtf8(new Uint8Array(dataView.buffer), offset, length);
		offset += length;
		return utf;
	}
}

let array = [0];
let obdv;

/**
 * Msgpack encode
 * @param {any} data
 * @returns {Uint8Array}
 */
function encodeMsg(data) {
	const pow32 = 0x100000000;	 // 2^32

	let offset = 0;
	ensureCapacity(256);
	encode(data);
	var tmp = array.subarray(0, offset);
	if (array.length > 4096) {
		array = [0];
		obdv = null;
	}
	return tmp;

	function encode(data) {
		ensureCapacity(offset + 9);
		switch (typeof data) {
			case "undefined": encodeNull(); break;
			case "boolean": encodeBoolean(data); break;
			case "number": encodeNumber(data); break;
			case "bigint": encodeBigint(data); break;
			case "string": encodeString(data); break;
			case "object":
				if (data === null) encodeNull();
				else if (data instanceof Date) encodeDate(data);
				else if (Array.isArray(data)) encodeArray(data);
				else if (ArrayBuffer.isView(data)) {
					if (data instanceof Uint8Array || data instanceof Uint8ClampedArray) encodeBinArray(data);
					else encodeArray(data);
				} else {
					encodeObject(data);
				}
				break;
			default: throw new Error((typeof data) + " 不支持序列化");
		}
	}

	function encodeNull() {encodeByte(0xc0);}
	function encodeBoolean(data) {encodeByte(data ? 0xc3 : 0xc2);}
	function encodeNumber(data) {
		if (isFinite(data) && Number.isSafeInteger(data)) {
			// Integer
			if (data >= -0x20 && data <= 0x7f) { // Tiny
				encodeByte(data);
			}
			else if (data >= -128 && data <= 255) { // int8
				encodeByte(data < 0 ? 0xD0 : 0xCC);
				encodeByte(data);
			}
			else if (data >= -32768 && data <= 65535) {	 // int16
				encodeByte(data < 0 ? 0xD1 : 0xCD);
				obdv.setUint16(offset, data);
				offset += 2;
			}
			else if (data >= -2147483648 && data <= 4294967295) { // int32
				encodeByte(data < 0 ? 0xD2 : 0xCE);
				obdv.setUint32(offset, data);
				offset += 4;
			}
			else { // int64
				encodeByte(0xd3);
				obdv.setBigInt64(offset, BigInt(data));
				offset += 8;
			}
		}
		else {
			// Float
			encodeByte(0xcb);
			obdv.setFloat64(offset, data);
			offset += 8;
		}
	}

	function encodeBigint(value) {
		encodeByte(0xD3);
		obdv.setBigInt64(offset, value);
		offset += 8;
	}

	function encodeString(data) {
		let bytes = encodeUtf8(data);
		let length = bytes.length;

		if (length <= 0x1f) {
			encodeByte(0xa0 | length);
		} else if (length <= 0xff) {
			encodeByte(0xD9);
			encodeByte(length);
		} else if (length <= 0xffff) {
			encodeByte(0xDA);
			obdv.setUint16(offset, length);
			offset += 2;
		} else {
			encodeByte(0xDB);
			obdv.setUint32(offset, length);
			offset += 4;
		}

		encodeBytes(bytes);
	}

	function encodeArray(data) {
		const length = data.length;

		if (length <= 0xf) {
			encodeByte(0x90 | length);
		} else if (length <= 0xffff) {
			encodeByte(0xDC);
			obdv.setUint16(offset, length);
			offset += 2;
		} else {
			encodeByte(0xDD);
			obdv.setUint32(offset, length);
			offset += 4;
		}

		for (let i = 0; i < length; i++) encode(data[i]);
	}

	function encodeBinArray(data) {
		const length = data.length;

		if (length <= 0xff) {
			encodeByte(0xC4);
			encodeByte(length);
		} else if (length <= 0xffff) {
			encodeByte(0xC5);
			obdv.setUint16(offset, length);
			offset += 2;
		} else {
			encodeByte(0xC6);
			obdv.setUint32(offset, length);
			offset += 4;
		}

		encodeBytes(data);
	}

	function encodeObject(data) {
		let length = 0;
		for (let key in data) {
			if (data[key] !== undefined) {
				length++;
			}
		}

		if (length <= 0xf) {
			encodeByte(0x80 | length);
		} else if (length <= 0xffff) {
			encodeByte(0xDE);
			obdv.setUint16(offset, length);
			offset += 2;
		} else {
			encodeByte(0xDF);
			obdv.setUint32(offset, length);
			offset += 4;
		}

		for (let key in data) {
			let value = data[key];
			if (value !== undefined) {
				encode(key);
				encode(value);
			}
		}
	}

	function encodeDate(data) {
		let sec = data.getTime() / 1000;
		if (data.getMilliseconds() === 0 && sec >= 0 && sec < pow32) {	 // 32 bit seconds
			obdv.setUint16(offset, 0xD6FF);
			offset += 2;
			obdv.setUint32(offset, sec);
			offset += 4;
		}
		else if (sec >= 0 && sec < 0x400000000) {	 // 30 bit nanoseconds, 34 bit seconds
			let ns = data.getMilliseconds() * 1000000;
			encodeBytes([0xd7, 0xff, ns >>> 22, ns >>> 14, ns >>> 6, ((ns << 2) >>> 0) | (sec / pow32), sec >>> 24, sec >>> 16, sec >>> 8, sec]);
		}
		else {	 // 32 bit nanoseconds, 64 bit seconds, negative values allowed
			ensureCapacity(offset + 15);
			encodeByte(0xC7);
			encodeByte(12);
			encodeByte(0xFF);
			obdv.setUint32(offset, data.getMilliseconds() * 1000000);
			offset += 4;
			obdv.setBigInt64(offset, BigInt(Math.floor(sec)));
			offset += 8;
		}
	}

	function ensureCapacity(capacity) {
		if (array.length < capacity) {
			let newLength = array.length << 1;
			while (newLength < capacity) newLength <<= 1;
			let newArray = new Uint8Array(newLength);
			newArray.set(array);
			array = newArray;
			obdv = new DataView(array.buffer);
		}
	}
	function encodeByte(byte) {array[offset++] = byte;}
	function encodeBytes(bytes) {
		ensureCapacity(offset+bytes.length);
		array.set(bytes, offset);
		offset += bytes.length;
	}
}

// Encodes a string to UTF-8 bytes.
function encodeUtf8(str) {
	// Prevent excessive array allocation and slicing for all 7-bit characters
	let ascii = true, length = str.length;
	for (let x = 0; x < length; x++) {
		if (str.charCodeAt(x) > 127) {
			ascii = false;
			break;
		}
	}

	// Based on: https://gist.github.com/pascaldekloe/62546103a1576803dade9269ccf76330
	let i = 0, bytes = new Uint8Array(str.length * (ascii ? 1 : 4));
	for (let ci = 0; ci !== length; ci++) {
		let c = str.charCodeAt(ci);
		if (c < 128) {
			bytes[i++] = c;
			continue;
		}
		if (c < 2048) {
			bytes[i++] = c >> 6 | 192;
		}
		else {
			if (c > 0xd7ff && c < 0xdc00) {
				if (++ci >= length)
					throw new Error("UTF-8 encode: incomplete surrogate pair");
				let c2 = str.charCodeAt(ci);
				if (c2 < 0xdc00 || c2 > 0xdfff)
					throw new Error("UTF-8 encode: second surrogate character 0x" + c2.toString(16) + " at index " + ci + " out of range");
				c = 0x10000 + ((c & 0x03ff) << 10) + (c2 & 0x03ff);
				bytes[i++] = c >> 18 | 240;
				bytes[i++] = c >> 12 & 63 | 128;
			}
			else bytes[i++] = c >> 12 | 224;
			bytes[i++] = c >> 6 & 63 | 128;
		}
		bytes[i++] = c & 63 | 128;
	}
	return ascii ? bytes : bytes.subarray(0, i);
}

// roj.text.UTF8
function decodeUtf8(bytes, i, length) {
	let str = "";
	length += i;
	while (i < length) {
		let c = bytes[i];
		if (c > 127) break;
		i++;
		str += String.fromCharCode(c);
	}

	let c2, c3, c4;
	while (i < length) {
		let c = bytes[i++];
		switch ((c>>>4)&0xF) {
			case 0: case 1: case 2: case 3: case 4: case 5: case 6: case 7:
				str += String.fromCharCode(c);
				break;
			case 12: case 13:
				c2 = bytes[i++];
				str += String.fromCharCode(c << 6 ^ c2 ^ 0b11000010000000);
				break;
			case 14:
				c2 = bytes[i++];
				c3 = bytes[i++];

				str += String.fromCharCode(c << 12 ^ c2 << 6 ^ c3 ^ 0b0010000010000000);
				break;
			case 15:
				c2 = bytes[i++];
				c3 = bytes[i++];
				c4 = bytes[i++];
				c4 = 2097151 & (c << 18 ^ c2 << 12 ^ c3 << 6 ^ c4 ^ 0b0010000010000010000000);

				if (c4 <= 0xFFFF) {
					str += String.fromCharCode(c4);
				} else {
					str += String.fromCharCode((c4 >> 10) + 0xd7c0);
					str += String.fromCharCode(c4 & 0x3FF | 0xdc00);
				}

				break;
			default: throw new Error("UTF-8 decode error");
		}
	}
	return str;
}

export {encodeMsg, decodeMsg, decodeRawMsg}