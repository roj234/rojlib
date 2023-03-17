package roj.crypt;

import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.crypto.ShortBufferException;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/11/12 0012 15:34
 */
public class AES implements CipheR {
	private byte[] lastKey;

	int[] encrypt_key, decrypt_key;
	int limit;

	boolean mode;

	public AES() {}
	public AES(AES aes, boolean decrypt) {
		this.encrypt_key = aes.encrypt_key;
		this.decrypt_key = aes.decrypt_key;
		this.limit = aes.limit;
		this.mode = decrypt;
	}

	@Override
	public int getMaxKeySize() {
		return 32;
	}

	@Override
	public void setKey(byte[] key, int flags) {
		switch (key.length) {
			case 16: case 24: case 32: break;
			default: throw new IllegalStateException("AES key length must be 16, 24 or 32");
		}

		mode = (flags & DECRYPT) != 0;
		if (Arrays.equals(lastKey, key)) return;

		int ROUNDS = (key.length >> 2) + 6;
		limit = ROUNDS << 2;
		int ROUND_KEY_COUNT = limit + 4;

		int[] Ke = new int[ROUND_KEY_COUNT]; // encryption round keys
		int[] Kd = new int[ROUND_KEY_COUNT]; // decryption round keys

		int KC = key.length/4; // keylen in 32-bit elements

		int[] tk = new int[KC];
		Conv.b2i(key,0,key.length,tk,0);

		int i, j;

		int k = 0;
		for (j = 0; j < KC && k < ROUND_KEY_COUNT; j++, k++) {
			Ke[k] = tk[j];
			Kd[(limit - (k&~3)) | (k&3)] = tk[j];
		}

		int t, ri = 0;
		while (k < ROUND_KEY_COUNT) {
			// extrapolate using phi (the round key evolution function)
			t = tk[KC - 1];
			tk[0] ^=
				(S[(t >>> 16) & 0xFF]) << 24 ^
				(S[(t >>> 8) & 0xFF] & 0xFF) << 16 ^
				(S[t & 0xFF] & 0xFF) << 8 ^
				(S[(t >>> 24)] & 0xFF)^
				rcon[ri++] << 24;
			if (KC != 8)
				for (i = 1, j = 0; i < KC; i++, j++) tk[i] ^= tk[j];
			else {
				for (i = 1, j = 0; i < KC / 2; i++, j++) tk[i] ^= tk[j];
				t = tk[KC / 2 - 1];
				tk[KC / 2] ^=
					(S[t & 0xFF] & 0xFF) ^
					(S[(t >>> 8) & 0xFF] & 0xFF) << 8 ^
					(S[(t >>> 16) & 0xFF] & 0xFF) << 16 ^
					S[(t >>> 24)] << 24;
				for (j = KC / 2, i = j + 1; i < KC; i++, j++) tk[i] ^= tk[j];
			}

			for (j = 0; j < KC && k < ROUND_KEY_COUNT; j++, k++) {
				Ke[k] = tk[j];
				Kd[(limit - (k&~3)) | (k&3)] = tk[j];
			}
		}

		for (int r = 4; r < limit; r++) {
			t = Kd[r];
			Kd[r] = U1[(t >>> 24) & 0xFF] ^
				U2[(t >>> 16) & 0xFF] ^
				U3[(t >>>  8) & 0xFF] ^
				U4[ t  & 0xFF];
		}

		// shift right
		int a = Kd[ROUND_KEY_COUNT-4],b = Kd[ROUND_KEY_COUNT-3];
		int c = Kd[ROUND_KEY_COUNT-2],d = Kd[ROUND_KEY_COUNT-1];
		// noinspection all
		System.arraycopy(Kd, 0, Kd, 4, ROUND_KEY_COUNT-4);
		Kd[0] = a;Kd[1] = b;Kd[2] = c;Kd[3] = d;

		encrypt_key = Ke;
		decrypt_key = Kd;
		lastKey = key.clone();
	}

	@Override
	public int getBlockSize() {
		return 16;
	}

	@Override
	public void crypt(DynByteBuf in, DynByteBuf out) throws GeneralSecurityException {
		if (out.writableBytes() < in.readableBytes()) throw new ShortBufferException();

		if (mode) aes_decrypt(decrypt_key, limit, in, out);
		else aes_encrypt(encrypt_key, limit, in, out);
	}

	static void aes_encrypt(int[] K, int len, DynByteBuf in, DynByteBuf out) {
		int kOff = 0;
		int a = in.readInt() ^ K[kOff++];
		int b = in.readInt() ^ K[kOff++];
		int c = in.readInt() ^ K[kOff++];
		int d = in.readInt() ^ K[kOff++];

		while (kOff < len) {
			int u, v, w;

			u = T1[(a >>> 24)] ^ T2[(b >>> 16) & 0xFF] ^ T3[(c >>> 8) & 0xFF] ^ T4[d & 0xFF] ^ K[kOff++];
			v = T1[(b >>> 24)] ^ T2[(c >>> 16) & 0xFF] ^ T3[(d >>> 8) & 0xFF] ^ T4[a & 0xFF] ^ K[kOff++];
			w = T1[(c >>> 24)] ^ T2[(d >>> 16) & 0xFF] ^ T3[(a >>> 8) & 0xFF] ^ T4[b & 0xFF] ^ K[kOff++];
			d = T1[(d >>> 24)] ^ T2[(a >>> 16) & 0xFF] ^ T3[(b >>> 8) & 0xFF] ^ T4[c & 0xFF] ^ K[kOff++];

			a = u;
			b = v;
			c = w;
		}

		int tt = K[kOff++];
		out.put((byte) (S[(a >>> 24)] ^ (tt >>> 24)))
		   .put((byte) (S[(b >>> 16) & 0xFF] ^ (tt >>> 16)))
		   .put((byte) (S[(c >>> 8) & 0xFF] ^ (tt >>> 8)))
		   .put((byte) (S[d & 0xFF] ^ tt));
		tt = K[kOff++];
		out.put((byte) (S[(b >>> 24)] ^ (tt >>> 24)))
		   .put((byte) (S[(c >>> 16) & 0xFF] ^ (tt >>> 16)))
		   .put((byte) (S[(d >>> 8) & 0xFF] ^ (tt >>> 8)))
		   .put((byte) (S[a & 0xFF] ^ tt));
		tt = K[kOff++];
		out.put((byte) (S[(c >>> 24)] ^ (tt >>> 24)))
		   .put((byte) (S[(d >>> 16) & 0xFF] ^ (tt >>> 16)))
		   .put((byte) (S[(a >>> 8) & 0xFF] ^ (tt >>> 8)))
		   .put((byte) (S[b & 0xFF] ^ tt));
		tt = K[kOff];
		out.put((byte) (S[(d >>> 24)] ^ (tt >>> 24)))
		   .put((byte) (S[(a >>> 16) & 0xFF] ^ (tt >>> 16)))
		   .put((byte) (S[(b >>> 8) & 0xFF] ^ (tt >>> 8)))
		   .put((byte) (S[c & 0xFF] ^ tt));
	}
	static void aes_decrypt(int[] K, int len, DynByteBuf in, DynByteBuf out) {
		int kOff = 4;

		int a = in.readInt() ^ K[kOff++];
		int b = in.readInt() ^ K[kOff++];
		int c = in.readInt() ^ K[kOff++];
		int d = in.readInt() ^ K[kOff++];

		len += 4;
		while (kOff < len) {
			int u, v, w;

			u = T5[(a >>> 24)] ^ T6[(d >>> 16) & 0xFF] ^ T7[(c >>> 8) & 0xFF] ^ T8[b & 0xFF] ^ K[kOff++];
			v = T5[(b >>> 24)] ^ T6[(a >>> 16) & 0xFF] ^ T7[(d >>> 8) & 0xFF] ^ T8[c & 0xFF] ^ K[kOff++];
			w = T5[(c >>> 24)] ^ T6[(b >>> 16) & 0xFF] ^ T7[(a >>> 8) & 0xFF] ^ T8[d & 0xFF] ^ K[kOff++];
			d = T5[(d >>> 24)] ^ T6[(c >>> 16) & 0xFF] ^ T7[(b >>> 8) & 0xFF] ^ T8[a & 0xFF] ^ K[kOff++];

			a = u;
			b = v;
			c = w;
		}

		// last round
		int k = K[0];
		out.put((byte) (Si[(a >>> 24)] ^ (k >>> 24)))
		   .put((byte) (Si[(d >>> 16) & 0xFF] ^ (k >>> 16)))
		   .put((byte) (Si[(c >>> 8) & 0xFF] ^ (k >>> 8)))
		   .put((byte) (Si[b & 0xFF] ^ k));
		k = K[1];
		out.put((byte) (Si[(b >>> 24)] ^ (k >>> 24)))
		   .put((byte) (Si[(a >>> 16) & 0xFF] ^ (k >>> 16)))
		   .put((byte) (Si[(d >>> 8) & 0xFF] ^ (k >>> 8)))
		   .put((byte) (Si[c & 0xFF] ^ k));
		k = K[2];
		out.put((byte) (Si[(c >>> 24)] ^ (k >>> 24)))
		   .put((byte) (Si[(b >>> 16) & 0xFF] ^ (k >>> 16)))
		   .put((byte) (Si[(a >>> 8) & 0xFF] ^ (k >>> 8)))
		   .put((byte) (Si[d & 0xFF] ^ k));
		k = K[3];
		out.put((byte) (Si[(d >>> 24)] ^ (k >>> 24)))
		   .put((byte) (Si[(c >>> 16) & 0xFF] ^ (k >>> 16)))
		   .put((byte) (Si[(b >>> 8) & 0xFF] ^ (k >>> 8)))
		   .put((byte) (Si[a & 0xFF] ^ k));
	}

	private static final byte[] S, Si;
	private static final int[] T1, T2, T3, T4, T5, T6, T7, T8, U1, U2, U3, U4;
	private static final byte[] rcon;
	private static ByteList tmp;
	static {
		tmp = ByteList.allocate(1024);

		// Round constant words
		rcon = _B("AQIECBAgQIAbNmzYq02aL168Y8aXNWrUs33678WR");

		// S-box and Inverse S-box (S is for Substitution)
		S = _B("Y3x3e/Jrb8UwAWcr/terdsqCyX36WUfwrdSir5ykcsC3/ZMmNj/3zDSl5fFx2DEVBMcjwxiWBZoHEoDi6yeydQmDLBobblqgUjvWsynjL4RT0QDtIPyxW2rLvjlKTFjP0O+q+0NNM4VF+QJ/UDyfqFGjQI+SnTj1vLbaIRD/89LNDBPsX5dEF8Snfj1kXRlzYIFP3CIqkIhG7rgU3l4L2+AyOgpJBiRcwtOsYpGV5HnnyDdtjdVOqWxW9Opleq4IunglLhymtMbo3XQfS72LinA+tWZIA/YOYTVXuYbBHZ7h+JgRadmOlJseh+nOVSjfjKGJDb/mQmhBmS0PsFS7Fg==");
		Si = _B("Uglq1TA2pTi/QKOegfPX+3zjOYKbL/+HNI5DRMTe6ctUe5QypsIjPe5MlQtC+sNOCC6hZijZJLJ2W6JJbYvRJXL49mSGaJgW1KRczF1ltpJscEhQ/e252l4VRlenjZ2EkNirAIy80wr35FgFuLNFBtAsHo/KPw8Cwa+9AwETims6kRFBT2fc6pfyz87wtOZzlqx0IuetNYXi+TfoHHXfbkfxGnEdKcWJb7diDqoYvhv8Vj5LxtJ5IJrbwP54zVr0H92oM4gHxzGxEhBZJ4DsX2BRf6kZtUoNLeV6n5PJnO+g4DtNrir1sMjruzyDU5lhFysEfrp31ibhaRRjVSEMfQ==");

		// Transformations for encryption
		T1 = _I(
			"xmNjpfh8fITud3eZ9nt7jf/y8g3Wa2u93m9vsZHFxVRgMDBQAgEBA85nZ6lWKyt95/7+GbXX12JNq6vm7HZ2mo/KykUfgoKdicnJQPp9fYfv+voVsllZ645HR8n78PALQa2t7LPU1GdfoqL9Ra+v6iOcnL9TpKT35HJylpvAwFt1t7fC4f39HD2Tk65MJiZqbDY2Wn4/P0H19/cCg8zMT2g0NFxRpaX00eXlNPnx8QjicXGTq9jYc2IxMVMqFRU/CAQEDJXHx1JGIyNlncPDXjAYGCg3lpahCgUFDy+amrUOBwcJJBISNhuAgJvf4uI9zevrJk4nJ2l" +
				"/srLN6nV1nxIJCRsdg4OeWCwsdDQaGi42Gxst3G5usrRaWu5boKD7pFJS9nY7O0231tZhfbOzzlIpKXvd4+M+Xi8vcROEhJemU1P1udHRaAAAAADB7e0sQCAgYOP8/B95sbHItltb7dRqar6Ny8tGZ76+2XI5OUuUSkremExM1LBYWOiFz89Ku9DQa8Xv7ypPqqrl7fv7FoZDQ8WaTU3XZjMzVRGFhZSKRUXP6fn5EAQCAgb+f3+BoFBQ8Hg8PEQln5+6S6io46JRUfNdo6P+gEBAwAWPj4o/kpKtIZ2dvHA4OEjx9fUEY7y833e2tsGv2tp1QiEhYyAQEDDl//8a/fPzDr/S0m2Bzc1MGAwMFCYTEzXD7Owvvl9f4TWXl6KIRETMLhcXOZPExFdVp6fy/H5+gno9PUfIZGSsul1d5zIZGSvmc3OVwGBgoBmBgZieT0/Ro9zcf0QiImZUKip+O5CQqwuIiIOMRkbKx+7uKWu4uNMoFBQ8p97eebxeXuIWCwsdrdvbdtvg4DtkMjJWdDo6ThQKCh6SSUnbDAYGCkgkJGy4XFzkn8LCXb3T025DrKzvxGJipjmRkagxlZWk0+TkN/" +
				"J5eYvV5+cyi8jIQ243N1nabW23AY2NjLHV1WScTk7SSamp4NhsbLSsVlb68/T0B8/q6iXKZWWv9Hp6jkeurukQCAgYb7q61fB4eIhKJSVvXC4ucjgcHCRXpqbxc7S0x5fGxlHL6Ogjod3dfOh0dJw+Hx8hlktL3WG9vdwNi4uGD4qKheBwcJB8Pj5CcbW1xMxmZqqQSEjYBgMDBff29gEcDg4SwmFho2o1NV+uV1f5abm50BeGhpGZwcFYOh0dJyeenrnZ4eE46/j4EyuYmLMiEREz0mlpu6nZ2XAHjo6JM5SUpy2bm7Y8Hh4iFYeHksnp6SCHzs5JqlVV/1AoKHil3996A4yMj1mhofgJiYmAGg0NF2W/v9rX5uYxhEJCxtBoaLiCQUHDKZmZsFotLXceDw8Re7Cwy6hUVPxtu7vWLBYWOg==");
		T2 = _I(
			"pcZjY4T4fHyZ7nd3jfZ7ew3/8vK91mtrsd5vb1SRxcVQYDAwAwIBAanOZ2d9VisrGef+/mK119fmTaurmux2dkWPysqdH4KCQInJyYf6fX0V7/r667JZWcmOR0cL+/Dw7EGtrWez1NT9X6Ki6kWvr78jnJz3U6SkluRyclubwMDCdbe3HOH9/a49k5NqTCYmWmw2NkF+Pz8C9ff3T4PMzFxoNDT0UaWlNNHl5Qj58fGT4nFxc6vY2FNiMTE/KhUVDAgEBFKVx8dlRiMjXp3DwygwGBihN5aWDwoFBbUvmpoJDgcHNiQSEpsbgIA93+LiJs3r62lOJyfNf7Kyn" +
				"+p1dRsSCQmeHYODdFgsLC40GhotNhsbstxubu60Wlr7W6Cg9qRSUk12Oztht9bWzn2zs3tSKSk+3ePjcV4vL5cThIT1plNTaLnR0QAAAAAswe3tYEAgIB/j/PzIebGx7bZbW77UampGjcvL2We+vktyOTnelEpK1JhMTOiwWFhKhc/Pa7vQ0CrF7+/lT6qqFu37+8WGQ0PXmk1NVWYzM5QRhYXPikVFEOn5+QYEAgKB/n9/8KBQUER4PDy6JZ+f40uoqPOiUVH+XaOjwIBAQIoFj4+tP5KSvCGdnUhwODgE8fX132O8vMF3trZ1r9raY0IhITAgEBAa5f//Dv3z822/0tJMgc3NFBgMDDUmExMvw+zs4b5fX6I1l5fMiEREOS4XF1eTxMTyVaengvx+fkd6PT2syGRk57pdXSsyGRmV5nNzoMBgYJgZgYHRnk9Pf6Pc3GZEIiJ+VCoqqzuQkIMLiIjKjEZGKcfu7tNruLg8KBQUeafe3uK8Xl4dFgsLdq3b2zvb4OBWZDIyTnQ6Oh4UCgrbkklJCgwGBmxIJCTkuFxcXZ" +
				"/Cwm6909PvQ6yspsRiYqg5kZGkMZWVN9Pk5IvyeXky1efnQ4vIyFluNze32m1tjAGNjWSx1dXSnE5O4EmpqbTYbGz6rFZWB/P09CXP6uqvymVljvR6eulHrq4YEAgI1W+6uojweHhvSiUlclwuLiQ4HBzxV6amx3O0tFGXxsYjy+jofKHd3ZzodHQhPh8f3ZZLS9xhvb2GDYuLhQ+KipDgcHBCfD4+xHG1tarMZmbYkEhIBQYDAwH39vYSHA4Oo8JhYV9qNTX5rldX0Gm5uZEXhoZYmcHBJzodHbknnp442eHhE+v4+LMrmJgzIhERu9JpaXCp2dmJB46OpzOUlLYtm5siPB4ekhWHhyDJ6elJh87O/6pVVXhQKCh6pd/fjwOMjPhZoaGACYmJFxoNDdplv78x1+bmxoRCQrjQaGjDgkFBsCmZmXdaLS0RHg8Py3uwsPyoVFTWbbu7OiwWFg==");
		T3 = _I(
			"Y6XGY3yE+Hx3me53e432e/IN//JrvdZrb7Heb8VUkcUwUGAwAQMCAWepzmcrfVYr/hnn/tditder5k2rdprsdspFj8qCnR+CyUCJyX2H+n36Fe/6WeuyWUfJjkfwC/vwrexBrdRns9Si/V+ir+pFr5y/I5yk91OkcpbkcsBbm8C3wnW3/Rzh/ZOuPZMmakwmNlpsNj9Bfj/3AvX3zE+DzDRcaDSl9FGl5TTR5fEI+fFxk+Jx2HOr2DFTYjEVPyoVBAwIBMdSlccjZUYjw16dwxgoMBiWoTeWBQ8KBZq1L5oHCQ4HEjYkEoCbG4DiPd/i6ybN6ydpTieyzX+ydZ/qdQkbEgmDnh2DLHRYLBouNBobLTYbbrLcblrutFqg" +
				"+1ugUvakUjtNdjvWYbfWs859syl7UinjPt3jL3FeL4SXE4RT9aZT0Wi50QAAAADtLMHtIGBAIPwf4/yxyHmxW+22W2q+1GrLRo3LvtlnvjlLcjlK3pRKTNSYTFjosFjPSoXP0Gu70O8qxe+q5U+q+xbt+0PFhkNN15pNM1VmM4WUEYVFz4pF+RDp+QIGBAJ/gf5/UPCgUDxEeDyfuiWfqONLqFHzolGj/l2jQMCAQI+KBY+SrT+SnbwhnThIcDj1BPH1vN9jvLbBd7bada/aIWNCIRAwIBD/GuX/8w7989Jtv9LNTIHNDBQYDBM1JhPsL8PsX+G+X5eiNZdEzIhEFzkuF8RXk8Sn8lWnfoL8fj1Hej1krMhkXee6XRkrMhlzleZzYKDAYIGYGYFP0Z5P3H+j3CJmRCIqflQqkKs7kIiDC4hGyoxG7inH7rjTa7gUPCgU3nmn3l7ivF4LHRYL23at2+A72" +
				"+AyVmQyOk50OgoeFApJ25JJBgoMBiRsSCRc5Lhcwl2fwtNuvdOs70OsYqbEYpGoOZGVpDGV5DfT5HmL8nnnMtXnyEOLyDdZbjdtt9ptjYwBjdVksdVO0pxOqeBJqWy02GxW+qxW9Afz9Oolz+plr8pleo70eq7pR64IGBAIutVvuniI8Hglb0olLnJcLhwkOBym8VemtMdztMZRl8boI8vo3Xyh3XSc6HQfIT4fS92WS73cYb2Lhg2LioUPinCQ4HA+Qnw+tcRxtWaqzGZI2JBIAwUGA/YB9/YOEhwOYaPCYTVfajVX+a5XudBpuYaRF4bBWJnBHSc6HZ65J57hONnh+BPr+JizK5gRMyIRabvSadlwqdmOiQeOlKczlJu2LZseIjweh5IVh+kgyenOSYfOVf+qVSh4UCjfeqXfjI8DjKH4WaGJgAmJDRcaDb/aZb/mMdfmQsaEQmi40GhBw4JBmbApmS13Wi0PER4PsMt7sFT8qFS71m27FjosFg==");
		T4 = _I(
			"Y2Olxnx8hPh3d5nue3uN9vLyDf9ra73Wb2+x3sXFVJEwMFBgAQEDAmdnqc4rK31W/v4Z59fXYrWrq+ZNdnaa7MrKRY+Cgp0fyclAiX19h/r6+hXvWVnrskdHyY7w8Av7ra3sQdTUZ7Oiov1fr6/qRZycvyOkpPdTcnKW5MDAW5u3t8J1/f0c4ZOTrj0mJmpMNjZabD8/QX739wL1zMxPgzQ0XGilpfRR5eU00fHxCPlxcZPi2NhzqzExU2IVFT8qBAQMCMfHUpUjI2VGw8NenRgYKDCWlqE3BQUPCpqatS8HBwkOEhI2JICAmxvi4j3f6+smzScnaU6yss1" +
				"/dXWf6gkJGxKDg54dLCx0WBoaLjQbGy02bm6y3Fpa7rSgoPtbUlL2pDs7TXbW1mG3s7POfSkpe1Lj4z7dLy9xXoSElxNTU/Wm0dFouQAAAADt7SzBICBgQPz8H+Oxsch5W1vttmpqvtTLy0aNvr7ZZzk5S3JKSt6UTEzUmFhY6LDPz0qF0NBru+/vKsWqquVP+/sW7UNDxYZNTdeaMzNVZoWFlBFFRc+K+fkQ6QICBgR/f4H+UFDwoDw8RHifn7olqKjjS1FR86Kjo/5dQEDAgI+PigWSkq0/nZ28ITg4SHD19QTxvLzfY7a2wXfa2nWvISFjQhAQMCD//xrl8/MO/dLSbb/NzUyBDAwUGBMTNSbs7C/DX1/hvpeXojVERMyIFxc5LsTEV5Onp/JVfn6C/D09R3pkZKzIXV3nuhkZKzJzc5XmYGCgwIGBmBlPT9Ge3Nx/oyIiZkQqKn5UkJCrO4iIgwtGRsqM7u4px7i402sUFDwo3t55p15e4rwLCx0W29t2reDgO9syMlZkOjpOdAoKHhRJSduSBgYKDCQkbEhcXOS4wsJdn9PTbr2srO9DYmKmxJGRqDmVlaQx5OQ303l5i" +
				"/Ln5zLVyMhDizc3WW5tbbfajY2MAdXVZLFOTtKcqangSWxstNhWVvqs9PQH8+rqJc9lZa/KenqO9K6u6UcICBgQurrVb3h4iPAlJW9KLi5yXBwcJDimpvFXtLTHc8bGUZfo6CPL3d18oXR0nOgfHyE+S0vdlr293GGLi4YNioqFD3BwkOA+PkJ8tbXEcWZmqsxISNiQAwMFBvb2AfcODhIcYWGjwjU1X2pXV/muubnQaYaGkRfBwViZHR0nOp6euSfh4TjZ+PgT65iYsysRETMiaWm70tnZcKmOjokHlJSnM5ubti0eHiI8h4eSFenpIMnOzkmHVVX/qigoeFDf33qljIyPA6Gh+FmJiYAJDQ0XGr+/2mXm5jHXQkLGhGhouNBBQcOCmZmwKS0td1oPDxEesLDLe1RU/Ki7u9ZtFhY6LA==");

		// Transformations for decryption
		T5 = _I(
			"UfSnUH5BZVMaF6TDOideljura8sfnUXxrPpYq0vjA5MgMPpVrXZt9ojMdpH1AkwlT+XX/MUqy9cmNUSAtWKjj96xWkkluhtnReoOmF3+wOHDL3UCgUzwEo1Gl6Nr0/nGA49f5xWSnJW/bXrrlVJZ2tS+gy1YdCHTSeBpKY7JyER1wolq9I55eJlYPmsnuXHdvuFPtvCIrRfJIKxmfc46tGPfShjlGjGCl1EzYGJTf0WxZHfgu2uuhP6BoBz5CCuUcEhoWI9F/RmU3myHUnv4t6tz0yNySwLi4x+PV2ZVqyqy6ygHL7XCA4bFe5rTNwilMCiH8iO/pbICA2q67RaCXIrPHCunebSS8wfy8E5p4qFl2vTNBgW+1dE0Yh" +
				"/Epv6KNC5TnaLzVaAFiuEypPbrdQuD7DlAYO+qXnGfBr1uEFE+IYr5lt0GPd0+Ba5N5r1GkVSNtXHEXQUEBtRvYFAV/xmY+yTWvemXiUBDzGfZnnew6EK9B4mLiOcZWzh5yO7boXwKR3xCD+n4hB7JAAAAAAmAhoMyK+1IHhFwrGxack79Dv/7D4U4Vj2u1R42LTknCg/ZZGhcpiGbW1TRJDYuOgwKZ7GTV+cPtO6W0hubkZ6AwMVPYdwgolp3S2kcEhoW4pO6CsCgKuU8IuBDEhsXHQ4JDQvyi8etLbaouRQeqchX8RmFr3UHTO6Z3bujf2D99wEmn1xy9bxEZjvFW/t+NItDKXbLI8bctu38aLjk8WPXMdzKQmOFEBOXIkCExhEghUokfdK7Pfiu+TIRxymhbR2eL0vcsjDzDYZS7HfB49ArsxZsqXC5mRGUSPpH6WQiqPyMxKDwPxpWfSzYIjOQ74dJTsfZONHBjMqi/pjUCzam9YHPpXreKNq3jiY/rb+kLDqd5FB4kg1qX8ybVH5GYvaNE8KQ2LjoLjn3XoLDr/WfXYC+adCTfG" +
				"/VLanPJRKzyKyZOxAYfafonGNu2zu7e80meAluWRj07Jq3AYNPmqjmlW5lqv/mfiG8zwjvFejmuueb2UpvNs7qnwnUKbB81jGksq8qPyMxxqWUMDWiZsB0Trw3/ILKpuCQ0LAzp9gV8QSYSkHs2vd/zVAOF5H2L3ZN1o1D77BNzKpNVOSWBN+e0bXjTGqIG8EsH7hGZVF/nV7qBAGMNV36h3Rz+wtBLrNnHVqS29JS6RBWM23WRxOa12GMN6EMeln4FI7rEzyJzqkn7rdhyTXhHOXtekexPJzS31lV8nM/GBTOeXPHN79T983qX/2qW989bxR4RNuGyq/zgbloxD44JDQswqNAXxYdw3K84iUMKDxJi/8NlUE5qAFxCAyz3ti05JxkVsGQe8uEYdUytnBIbFx00LhXQg==");
		T6 = _I(
			"UFH0p1N+QWXDGhekljonXss7q2vxH51Fq6z6WJNL4wNVIDD69q12bZGIzHYl9QJM/E/l19fFKsuAJjVEj7Vio0nesVpnJbobmEXqDuFd/sACwy91EoFM8KONRpfGa9P55wOPX5UVkpzrv2162pVSWS3UvoPTWHQhKUngaUSOychqdcKJePSOeWuZWD7dJ7lxtr7hTxfwiK1mySCstH3OOhhj30qC5RoxYJdRM0ViU3/gsWR3hLtrrhz+gaCU+QgrWHBIaBmPRf2HlN5st1J7+COrc9PicksCV+MfjypmVasHsusoAy+1wpqGxXul0zcI8jAoh7Ijv6W6AgNqXO0WgiuKzxySp3m08PMH8qFOaeLNZdr01QYFvh/RNGKKxKb" +
				"+nTQuU6Ci81UyBYrhdaT26zkLg+yqQGDvBl5xn1G9bhD5PiGKPZbdBq7dPgVGTea9tZFUjQVxxF1vBAbU/2BQFSQZmPuX1r3pzIlAQ3dn2Z69sOhCiAeJizjnGVvbecjuR6F8Cul8Qg/J+IQeAAAAAIMJgIZIMivtrB4RcE5sWnL7/Q7/Vg+FOB49rtUnNi05ZAoP2SFoXKbRm1tUOiQ2LrEMCmcPk1fn0rTulp4bm5FPgMDFomHcIGlad0sWHBIaCuKTuuXAoCpDPCLgHRIbFwsOCQ2t8ovHuS22qMgUHqmFV/EZTK91B7vumd39o39gn/cBJrxccvXFRGY7NFv7fnaLQyncyyPGaLbt/GO45PHK1zHcEEJjhUATlyIghMYRfYVKJPjSuz0RrvkybccpoUsdni/z3LIw7A2GUtB3weNsK7MWmalwufoRlEgiR+lkxKj8jBqg8D/YVn0s7yIzkMeHSU7B2TjR/ozKojaY1AvPpvWBKKV63ibat46kP62/5Cw6nQ1QeJKbal/MYlR+RsL2jRPokNi4Xi459/WCw6" +
				"++n12AfGnQk6lv1S2zzyUSO8ismacQGH1u6Jxje9s7uwnNJnj0blkYAeyat6iDT5pl5pVufqr/5gghvM/m7xXo2brnm85KbzbU6p8J1imwfK8xpLIxKj8jMMallMA1omY3dE68pvyCyrDgkNAVM6fYSvEEmPdB7NoOf81QLxeR9o12TdZNQ++wVMyqTd/klgTjntG1G0xqiLjBLB9/RmVRBJ1e6l0BjDVz+od0LvsLQVqzZx1SktvSM+kQVhNt1keMmtdhejehDI5Z+BSJ6xM87s6pJzW3Ycnt4RzlPHpHsVmc0t8/VfJzeRgUzr9zxzfqU/fNW1/9qhTfPW+GeETbgcqv8z65aMQsOCQ0X8KjQHIWHcMMvOIliyg8SUH/DZVxOagB3ggMs5zYtOSQZFbBYXvLhHDVMrZ0SGxcQtC4Vw==");
		T7 = _I(
			"p1BR9GVTfkGkwxoXXpY6J2vLO6tF8R+dWKus+gOTS+P6VSAwbfatdnaRiMxMJfUC1/xP5cvXxSpEgCY1o4+1YlpJ3rEbZyW6DphF6sDhXf51AsMv8BKBTJejjUb5xmvTX+cDj5yVFZJ6679tWdqVUoMt1L4h01h0aSlJ4MhEjsmJanXCeXj0jj5rmVhx3Se5T7a+4a0X8IisZskgOrR9zkoYY98xguUaM2CXUX9FYlN34LFkroS7a6Ac/oErlPkIaFhwSP0Zj0Vsh5Te+LdSe9Mjq3MC4nJLj1fjH6sqZlUoB7LrwgMvtXuahsUIpdM3h/IwKKWyI79qugIDglztFhwris+0kqd58vDzB+KhTmn0zWXavtUGBWIf0TT" +
				"+isSmU500LlWgovPhMgWK63Wk9uw5C4PvqkBgnwZecRBRvW6K+T4hBj2W3QWu3T69Rk3mjbWRVF0FccTUbwQGFf9gUPskGZjpl9a9Q8yJQJ53Z9lCvbDoi4gHiVs45xnu23nICkehfA/pfEIeyfiEAAAAAIaDCYDtSDIrcKweEXJObFr/+/0OOFYPhdUePa45JzYt2WQKD6YhaFxU0ZtbLjokNmexDArnD5NXltK07pGeG5vFT4DAIKJh3EtpWncaFhwSugrikyrlwKDgQzwiFx0SGw0LDgnHrfKLqLkttqnIFB4ZhVfxB0yvdd277plg/aN/Jp/3AfW8XHI7xURmfjRb+yl2i0PG3Msj/Gi27fFjuOTcytcxhRBCYyJAE5cRIITGJH2FSj340rsyEa75oW3HKS9LHZ4w89yyUuwNhuPQd8EWbCuzuZmpcEj6EZRkIkfpjMSo/D8aoPAs2FZ9kO8iM07Hh0nRwdk4ov6Mygs2mNSBz6b13iileo4m2re/pD+tneQsOpINUHjMm2pfRmJUfhPC9o246JDY914uOa" +
				"/1gsOAvp9dk3xp0C2pb9USs88lmTvIrH2nEBhjbuicu3vbO3gJzSYY9G5ZtwHsmpqog09uZeaV5n6q/88IIbzo5u8Vm9m65zbOSm8J1OqffNYpsLKvMaQjMSo/lDDGpWbANaK8N3ROyqb8gtCw4JDYFTOnmErxBNr3QexQDn/N9i8XkdaNdk2wTUPvTVTMqgTf5Ja1457RiBtMah+4wSxRf0Zl6gSdXjVdAYx0c/qHQS77Cx1as2fSUpLbVjPpEEcTbdZhjJrXDHo3oRSOWfg8iesTJ+7Oqck1t2Hl7eEcsTx6R99ZnNJzP1XyznkYFDe/c8fN6lP3qltf/W8U3z3bhnhE84HKr8Q+uWg0LDgkQF/Co8NyFh0lDLziSYsoPJVB/w0BcTmos94IDOSc2LTBkGRWhGF7y7Zw1TJcdEhsV0LQuA==");
		T8 = _I(
			"9KdQUUFlU34XpMMaJ16WOqtryzudRfEf+lirrOMDk0sw+lUgdm32rcx2kYgCTCX15df8TyrL18U1RIAmYqOPtbFaSd66G2cl6g6YRf7A4V0vdQLDTPASgUaXo43T+cZrj1/nA5KclRVteuu/Ulnalb6DLdR0IdNY4GkpScnIRI7CiWp1jnl49Fg+a5m5cd0n4U+2voitF/AgrGbJzjq0fd9KGGMaMYLlUTNgl1N/RWJkd+Cxa66Eu4GgHP4IK5T5SGhYcEX9GY/ebIeUe/i3UnPTI6tLAuJyH49X41WrKmbrKAeytcIDL8V7moY3CKXTKIfyML+lsiMDaroCFoJc7c8cK4p5tJKnB/Lw82nioU7a9M1lBb7VBjRiH9Gm" +
				"/orELlOdNPNVoKKK4TIF9ut1pIPsOQtg76pAcZ8GXm4QUb0hivk+3QY9lj4Frt3mvUZNVI21kcRdBXEG1G8EUBX/YJj7JBm96ZfWQEPMidmed2foQr2wiYuIBxlbOOfI7tt5fApHoUIP6XyEHsn4AAAAAICGgwkr7UgyEXCsHlpyTmwO//v9hThWD67VHj0tOSc2D9lkClymIWhbVNGbNi46JApnsQxX5w+T7pbStJuRnhvAxU+A3CCiYXdLaVoSGhYck7oK4qAq5cAi4EM8GxcdEgkNCw6Lx63ytqi5LR6pyBTxGYVXdQdMr5ndu+5/YP2jASaf93L1vFxmO8VE+340W0MpdosjxtzL7fxotuTxY7gx3MrXY4UQQpciQBPGESCESiR9hbs9+NL5MhGuKaFtx54vSx2yMPPchlLsDcHj0HezFmwrcLmZqZRI+hHpZCJH/IzEqPA/GqB9LNhWM5DvIklOx4c40cHZyqL+jNQLNpj1gc+met4opbeOJtqtv6Q" +
				"/Op3kLHiSDVBfzJtqfkZiVI0TwvbYuOiQOfdeLsOv9YJdgL6f0JN8adUtqW8lErPPrJk7yBh9pxCcY27oO7t72yZ4Cc1ZGPRumrcB7E+aqIOVbmXm/+Z+qrzPCCEV6Obv55vZum82zkqfCdTqsHzWKaSyrzE/IzEqpZQwxqJmwDVOvDd0gsqm/JDQsOCn2BUzBJhK8eza90HNUA5/kfYvF03WjXbvsE1Dqk1UzJYE3+TRteOeaogbTCwfuMFlUX9GXuoEnYw1XQGHdHP6C0Eu+2cdWrPb0lKSEFYz6dZHE23XYYyaoQx6N/gUjlkTPInrqSfuzmHJNbcc5e3hR7E8etLfWZzycz9VFM55GMc3v3P3zepT/apbXz1vFN9E24Z4r/OBymjEPrkkNCw4o0Bfwh3DchbiJQy8PEmLKA2VQf+oAXE5DLPeCLTknNhWwZBky4RhezK2cNVsXHRIuFdC0A==");

		// Transformations for decryption key expansion
		U1 = _I("AAAAAA4JDQscEhoWEhsXHTgkNCw2LTknJDYuOio/IzFwSGhYfkFlU2xack5iU39FSGxcdEZlUX9UfkZiWndLaeCQ0LDumd27/ILKpvKLx63YtOSc1r3pl8Sm/orKr/OBkNi46J7RteOMyqL+gsOv9aj8jMSm9YHPtO6W0rrnm9nbO7t71TK2cMcpoW3JIKxm4x+PV+0Wglz/DZVB8QSYSqtz0yOlet4ot2HJNbloxD6TV+cPnV7qBI9F/RmBTPASO6tryzWiZsAnuXHdKbB81gOPX+cNhlLsH51F8RGUSPpL4wOTReoOmFfxGYVZ" +
			"+BSOc8c3v33OOrRv1S2pYdwgoq12bfajf2D9sWR34L9teuuVUlnam1tU0YlAQ8yHSU7H3T4FrtM3CKXBLB+4zyUSs+UaMYLrEzyJ+QgrlPcBJp9N5r1GQ++wTVH0p1Bf/apbdcKJanvLhGFp0JN8Z9medz2u1R4zp9gVIbzPCC+1wgMFiuEyC4PsORmY+yQXkfYvdk3WjXhE24ZqX8ybZFbBkE5p4qFAYO+qUnv4t1xy9bwGBb7VCAyz3hoXpMMUHqnIPiGK+TAoh/IiM5DvLDqd5JbdBj2Y1As2is8cK4TGESCu+TIRoPA/GrLrKAe84iUM5pVuZeicY276h3Rz9I55eN6xWknQuFdCwqNAX8yqTVRB7Nr3T+XX/F3+wOFT983qecju23fB49Bl2vTNa9P5xjGksq8/rb+kLbaouSO/pbIJgIaDB4mLiBWSnJUbm5GeoXwKR691B0y9bhBRs2cdWplYPmuXUTNghUokfYtDKXbRNGIf3z1vFM0meAnDL3UC6RBWM+cZWzj1Akwl+wtBLprXYYyU3myHhsV7mojMdpGi81WgrPpYq77hT7aw6EK96p8J1OSWBN/2jRPC" +
			"+IQeydK7PfjcsjDzzqkn7sCgKuV6R7E8dE68N2ZVqypoXKYhQmOFEExqiBtecZ8GUHiSDQoP2WQEBtRvFh3DchgUznkyK+1IPCLgQy45914gMPpV7Jq3AeKTugrwiK0X/oGgHNS+gy3at44myKyZO8allDCc0t9ZktvSUoDAxU+OychEpPbrdar/5n645PFjtu38aAwKZ7ECA2q6EBh9px4RcKw0LlOdOidelig8SYsmNUSAfEIP6XJLAuJgUBX/blkY9ERmO8VKbzbOWHQh01Z9LNg3oQx6OagBcSuzFmwluhtnD4U4VgGMNV0TlyJAHZ4vS0fpZCJJ4GkpW/t+NFXycz9/zVAOccRdBWPfShht1kcT1zHcytk40cHLI8bcxSrL1+8V6ObhHOXt8wfy8P0O//unebSSqXC5mbtrroS1YqOPn12AvpFUjbWDT5qojUaXow==");
		U2 = _I("AAAAAAsOCQ0WHBIaHRIbFyw4JDQnNi05OiQ2LjEqPyNYcEhoU35BZU5sWnJFYlN/dEhsXH9GZVFiVH5GaVp3S7DgkNC77pndpvyCyq3yi8ec2LTkl9a96YrEpv6Byq/z6JDYuOOe0bX+jMqi9YLDr8So/IzPpvWB0rTultm655t72zu7cNUytm3HKaFmySCsV+Mfj1ztFoJB/w2VSvEEmCOrc9MopXreNbdhyT65aMQPk1fnBJ1e6hmPRf0SgUzwyzura8A1ombdJ7lx1imwfOcDj1/sDYZS8R+dRfoRlEiTS+MDmEXqDoVX8RmOWfgUv3PHN7R9zjqpb9UtomHcIPatdm39o39g4LFkd+u" +
			"/bXralVJZ0ZtbVMyJQEPHh0lOrt0+BaXTNwi4wSwfs88lEoLlGjGJ6xM8lPkIK5/3ASZGTea9TUPvsFBR9KdbX/2qanXCiWF7y4R8adCTd2fZnh49rtUVM6fYCCG8zwMvtcIyBYrhOQuD7CQZmPsvF5H2jXZN1oZ4RNubal/MkGRWwaFOaeKqQGDvt1J7+LxccvXVBgW+3ggMs8MaF6TIFB6p+T4hivIwKIfvIjOQ5Cw6nT2W3QY2mNQLK4rPHCCExhERrvkyGqDwPwey6ygMvOIlZeaVbm7onGNz+od0ePSOeUnesVpC0LhXX8KjQFTMqk33Qeza/E/l1+Fd/sDqU/fN23nI7tB3wePNZdr0xmvT+a8xpLKkP62/uS22qLIjv6WDCYCGiAeJi5UVkpyeG5uRR6F8CkyvdQdRvW4QWrNnHWuZWD5gl1EzfYVKJHaLQykf0TRiFN89bwnNJngCwy91M+kQVjjnGVsl9QJMLvsLQYya12GHlN5smobFe5GIzHagovNVq6z6WLa+4U+9sOhC1OqfCd/klgTC9o0TyfiEHvjSuz3z3LIw7s6pJ" +
			"+XAoCo8ekexN3ROvCpmVashaFymEEJjhRtMaogGXnGfDVB4kmQKD9lvBAbUchYdw3kYFM5IMivtQzwi4F4uOfdVIDD6Aeyatwrik7oX8IitHP6BoC3UvoMm2reOO8ismTDGpZRZnNLfUpLb0k+AwMVEjsnIdaT2636q/+ZjuOTxaLbt/LEMCme6AgNqpxAYfaweEXCdNC5TljonXosoPEmAJjVE6XxCD+JySwL/YFAV9G5ZGMVEZjvOSm8201h0IdhWfSx6N6EMcTmoAWwrsxZnJbobVg+FOF0BjDVAE5ciSx2eLyJH6WQpSeBpNFv7fj9V8nMOf81QBXHEXRhj30oTbdZHytcx3MHZONHcyyPG18Uqy+bvFejt4Rzl8PMH8vv9Dv+Sp3m0malwuYS7a66PtWKjvp9dgLWRVI2og0+ao41Glw==");
		U3 = _I("AAAAAA0LDgkaFhwSFx0SGzQsOCQ5JzYtLjokNiMxKj9oWHBIZVN+QXJObFp/RWJTXHRIbFF/RmVGYlR+S2lad9Cw4JDdu+6Zyqb8gset8ovknNi06ZfWvf6KxKbzgcqvuOiQ2LXjntGi/ozKr/WCw4zEqPyBz6b1ltK07pvZuue7e9s7tnDVMqFtxymsZskgj1fjH4Jc7RaVQf8NmErxBNMjq3PeKKV6yTW3YcQ+uWjnD5NX6gSdXv0Zj0XwEoFMa8s7q2bANaJx3Se5fNYpsF/nA49S7A2GRfEfnUj6EZQDk0vjDphF6hmFV/EUjln4N79zxzq0fc4tqW/VIKJh3G32rXZg/aN/d" +
			"+CxZHrrv21Z2pVSVNGbW0PMiUBOx4dJBa7dPgil0zcfuMEsErPPJTGC5Ro8iesTK5T5CCaf9wG9Rk3msE1D76dQUfSqW1/9iWp1woRhe8uTfGnQnndn2dUePa7YFTOnzwghvMIDL7XhMgWK7DkLg/skGZj2LxeR1o12TduGeETMm2pfwZBkVuKhTmnvqkBg+LdSe/W8XHK+1QYFs94IDKTDGhepyBQeivk+IYfyMCiQ7yIzneQsOgY9lt0LNpjUHCuKzxEghMYyEa75Pxqg8CgHsuslDLzibmXmlWNu6Jx0c/qHeXj0jlpJ3rFXQtC4QF/Co01UzKra90Hs1/xP5cDhXf7N6lP37tt5yOPQd8H0zWXa+cZr07KvMaS/pD+tqLkttqWyI7+GgwmAi4gHiZyVFZKRnhubCkehfAdMr3UQUb1uHVqzZz5rmVgzYJdRJH2FSil2i0NiH9E0bxTfPXgJzSZ1AsMvVjPpEFs45xlMJfUCQS77C2GMmtdsh5Tee5qGxXaRiMxVoKLzWKus+k+2vuFCvbDoCdTqnwTf5JYTwvaNHsn4hD340rsw89yyJ" +
			"+7OqSrlwKCxPHpHvDd0TqsqZlWmIWhchRBCY4gbTGqfBl5xkg1QeNlkCg/UbwQGw3IWHc55GBTtSDIr4EM8IvdeLjn6VSAwtwHsmroK4pOtF/CIoBz+gYMt1L6OJtq3mTvIrJQwxqXfWZzS0lKS28VPgMDIRI7J63Wk9uZ+qv/xY7jk/Gi27WexDApqugIDfacQGHCsHhFTnTQuXpY6J0mLKDxEgCY1D+l8QgLicksV/2BQGPRuWTvFRGY2zkpvIdNYdCzYVn0MejehAXE5qBZsK7MbZyW6OFYPhTVdAYwiQBOXL0sdnmQiR+lpKUngfjRb+3M/VfJQDn/NXQVxxEoYY99HE23W3MrXMdHB2TjG3Msjy9fFKujm7xXl7eEc8vDzB//7/Q60kqd5uZmpcK6Eu2ujj7VigL6fXY21kVSaqINPl6ONRg==");
		U4 = _I("AAAAAAkNCw4SGhYcGxcdEiQ0LDgtOSc2Ni46JD8jMSpIaFhwQWVTflpyTmxTf0VibFx0SGVRf0Z+RmJUd0tpWpDQsOCZ3bvugsqm/IvHrfK05JzYvemX1qb+isSv84HK2LjokNG1457Kov6Mw6/1gvyMxKj1gc+m7pbStOeb2bo7u3vbMrZw1SmhbccgrGbJH49X4xaCXO0NlUH/BJhK8XPTI6t63iilYck1t2jEPrlX5w+TXuoEnUX9GY9M8BKBq2vLO6JmwDW5cd0nsHzWKY9f5wOGUuwNnUXxH5RI+hHjA5NL6g6YRfEZhVf4FI5Zxze/c846tH3VLalv3CCiYXZt9q1" +
			"/YP2jZHfgsW16679SWdqVW1TRm0BDzIlJTseHPgWu3TcIpdMsH7jBJRKzzxoxguUTPInrCCuU+QEmn/fmvUZN77BNQ/SnUFH9qltfwolqdcuEYXvQk3xp2Z53Z67VHj2n2BUzvM8IIbXCAy+K4TIFg+w5C5j7JBmR9i8XTdaNdkTbhnhfzJtqVsGQZGnioU5g76pAe/i3UnL1vFwFvtUGDLPeCBekwxoeqcgUIYr5PiiH8jAzkO8iOp3kLN0GPZbUCzaYzxwrisYRIIT5MhGu8D8aoOsoB7LiJQy8lW5l5pxjbuiHdHP6jnl49LFaSd64V0LQo0BfwqpNVMzs2vdB5df8T/7A4V33zepTyO7becHj0Hfa9M1l0/nGa6SyrzGtv6Q/tqi5Lb+lsiOAhoMJiYuIB5KclRWbkZ4bfApHoXUHTK9uEFG9Zx1as1g+a5lRM2CXSiR9hUMpdos0Yh/RPW8U3yZ4Cc0vdQLDEFYz6RlbOOcCTCX1C0Eu+9dhjJrebIeUxXuahsx2kYjzVaCi+lirrOFPtr7oQr2wnwnU6pYE3+SNE8L2hB7J+Ls9" +
			"+NKyMPPcqSfuzqAq5cBHsTx6Trw3dFWrKmZcpiFoY4UQQmqIG0xxnwZeeJINUA/ZZAoG1G8EHcNyFhTOeRgr7UgyIuBDPDn3Xi4w+lUgmrcB7JO6CuKIrRfwgaAc/r6DLdS3jibarJk7yKWUMMbS31mc29JSksDFT4DJyESO9ut1pP/mfqrk8WO47fxotgpnsQwDaroCGH2nEBFwrB4uU500J16WOjxJiyg1RIAmQg/pfEsC4nJQFf9gWRj0bmY7xURvNs5KdCHTWH0s2FahDHo3qAFxObMWbCu6G2clhThWD4w1XQGXIkATni9LHelkIkfgaSlJ+340W/JzP1XNUA5/xF0Fcd9KGGPWRxNtMdzK1zjRwdkjxtzLKsvXxRXo5u8c5e3hB/Lw8w7/+/15tJKncLmZqWuuhLtio4+1XYC+n1SNtZFPmqiDRpejjQ==");

		tmp = null;
	}
	private static byte[] _B(String s) {
		tmp.clear();
		return Base64.decode(s, tmp).toByteArray();
	}
	private static int[] _I(String s) {
		tmp.clear();
		DynByteBuf buf = Base64.decode(s, tmp);
		int[] arr = new int[buf.readableBytes()/4];
		return Conv.b2i(tmp.list,0,buf.readableBytes(),arr,0);
	}
}
