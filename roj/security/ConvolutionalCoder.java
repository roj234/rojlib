package roj.security;

import roj.util.BitWriter;
import roj.util.ByteList;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * <a href="https://www.youtube.com/watch?v=b3_lVSrPB6w">...</a>
 * @author Roj234
 * @since 2022/12/26 0026 14:06
 */
public class ConvolutionalCoder {

	public static void main(String[] args) {
		ConvolutionalCoder coder = new ConvolutionalCoder();
		//coder.fill_table(3, 8, null);

		ByteList test = ByteList.wrap(args[0].getBytes(StandardCharsets.UTF_8));
		ByteList tmp = new ByteList();
		coder.encode(test, tmp);
	}

	/*void fill_table(int rate, int order, int [] poly) {
		table = new int[1<<order];
		for (int i = 0; i < 1 << order; i++) {
			int out = 0;
			int mask = 1;
			for (int j = 0; j < rate; j++) {
				out |= poly == null ?  : (popcount(i & poly[j]) % 2 != 0) ? mask : 0;
				mask <<= 1;
			}
			table[i] = out;
		}
		numstates = table.length;
	}*/

	int[] table;  // size 2**order
	int rate;                // e.g. 2, 3...
	int order;               // e.g. 7, 9...
	int numstates;     // 2**order
	BitWriter bit_writer;
	BitWriter bit_reader;

	boolean has_init_decode;
	char[] distances;
	PairLookup pair_lookup;
	static final int CORRECT_SOFT_LINEAR = 0, CORRECT_SOFT_QUADRATIC = 1;
	int soft_measurement;
	HistoryBuffer history_buffer;
	ErrorBuffer errors;

	static class PairLookup {
		int[] keys;
		int[] outputs;
		final int output_mask;
		final int output_width;
		final int outputs_len;
		int[] distances;

		PairLookup(int rate, int order, int[] table) {
			keys = new int[1 << (order-1)];
			outputs = new int[1 << (rate*2)];

			int[] inv_outputs = new int[1 << (rate*2)];
			int output_counter = 1;
			// for every (even-numbered) shift register state, find the concatenated output of the state
			//   and the subsequent state that follows it (low bit set). then, check to see if this
			//   concatenated output has a unique key assigned to it already. if not, give it a key.
			//   if it does, retrieve the key. assign this key to the shift register state.
			for (int i = 0; i < keys.length; i++) {
				// first get the concatenated pair of outputs
				int out = table[i * 2 + 1];
				out <<= rate;
				out |= table[i * 2];

				// does this concatenated output exist in the outputs table yet?
				if (0 == inv_outputs[out]) {
					// doesn't exist, allocate a new key
					inv_outputs[out] = output_counter;
					outputs[output_counter] = out;
					output_counter++;
				}
				// set the opaque key for the ith shift register state to the concatenated output entry
				keys[i] = inv_outputs[out];
			}

			outputs_len = output_counter;
			output_mask = (1 << (rate)) - 1;
			output_width = rate;
			distances = new int[outputs_len];
		}

		// table has numstates rows
		// each row contains all of the polynomial output bits concatenated together
		// e.g. for rate 2, we have 2 bits in each row
		// the first poly gets the LEAST significant bit, last poly gets most significant
		void fill_table(int rate, int order,
						ReedSolomonCoder.Polynomial poly,
						int[] table) {
			for (int i = 0; i < 1 << order; i++) {
				int out = 0;
				int mask = 1;
				for (int j = 0; j < rate; j++) {
					out |= ((popcount(i & poly.coeff.get(j)) & 1) != 0) ? mask : 0;
					mask <<= 1;
				}
				table[i] = out;
			}
		}

		void pair_lookup_fill_distance(char[] distances) {
			for (int i = 1; i < outputs_len; i ++) {
				int concat_out = outputs[i];
				int i_0 = concat_out & output_mask;
				concat_out >>>= output_width;
				int i_1 = concat_out;

				this.distances[i] = (char) ((distances[i_1] << 16) | distances[i_0]);
			}
		}

	}

	static class HistoryBuffer {
		// history entries must be at least this old to be decoded
		final int min_traceback_length;
		// we'll decode entries in bursts. this tells us the length of the burst
		final int traceback_group_length;
		// we will store a total of cap entries. equal to min_traceback_length +
		// traceback_group_length
		final int cap;

		// how many states in the shift register? this is one of the dimensions of
		// history table
		final int num_states;
		// what's the high order bit of the shift register?
		final int highbit;

		HistoryBuffer(int min_traceback_length,
					  int traceback_group_length,
					  int renormalize_interval,
					  int num_states,
					  int highbit) {

			this.min_traceback_length = min_traceback_length;
			this.traceback_group_length = traceback_group_length;
			this.cap = min_traceback_length + traceback_group_length;
			this.num_states = num_states;
			this.highbit = highbit;

			history = new byte[cap][num_states];
			fetched = new byte[cap];

			index = len = 0;

			renormalize_counter = 0;
			this.renormalize_interval = renormalize_interval;
		}
		void history_buffer_reset() {
			len = 0;
			index = 0;
		}
		void history_buffer_step() {
			// ?
		}
		byte[] history_buffer_get_slice() {
			return history[index];
		}

		int history_buffer_search(char[] distances, int search_every) {
			int best = 0;
			int bestDiff = 0xFFFF;
			// search for a state with the least error
			for (int state = 0; state < num_states; state += search_every) {
				if (distances[state] < bestDiff) {
					bestDiff = distances[state];
					best = state;
				}
			}
			return best;
		}
		void history_buffer_renormalize(char[] distances, int min_register) {
			char min_distance = distances[min_register];
			for (int i = 0; i < num_states; i++) {
				distances[i] -= min_distance;
			}
		}

		void history_buffer_traceback(int bestpath, int min_traceback_length, BitWriter output) {
			int fetched_index = 0;
			int highbit = this.highbit;
			int index = this.index;
			int cap = this.cap;
			for (int j = 0; j < min_traceback_length; j++) {
				if (index == 0) {
					index = cap-1;
				} else {
					index--;
				}

				// we're walking backwards from what the work we did before
				// so, we'll shift high order bits in
				// the path will cross multiple different shift register states, and we determine
				//   which state by going backwards one time slice at a time
				int pathbit = history[index][bestpath]!=0 ? highbit : 0;
				bestpath |= pathbit;
				bestpath >>>= 1;
			}

			int prefetch_index = index;
			if (prefetch_index == 0) {
				prefetch_index = cap - 1;
			} else {
				prefetch_index--;
			}

			int len = this.len;
			for (int j = min_traceback_length; j < len; j++) {
				index = prefetch_index;
				if (prefetch_index == 0) {
					prefetch_index = cap - 1;
				} else {
					prefetch_index--;
				}

				// this method doing nothing
				//prefetch(history[prefetch_index]);
				// we're walking backwards from what the work we did before
				// so, we'll shift high order bits in
				// the path will cross multiple different shift register states, and we determine
				//   which state by going backwards one time slice at a time
				int pathbit = history[index][bestpath]!=0 ? highbit : 0;
				bestpath |= pathbit;
				bestpath >>>= 1;

				fetched[fetched_index] = (byte) (pathbit!=0 ? 1 : 0);
				fetched_index++;
			}

			writeBitListReversed(output, fetched, fetched_index);
			this.len -= fetched_index;
		}

		private void writeBitListReversed(BitWriter output, byte[] fetched, int index) {
			// todo
		}

		void history_buffer_process_skip(char[] distances, BitWriter output, int skip) {
			if (++index == cap) {
				index = 0;
			}

			renormalize_counter++;
			len++;

			// there are four ways these branches can resolve
			// a) we are neither renormalizing nor doing a traceback
			// b) we are renormalizing but not doing a traceback
			// c) we are renormalizing and doing a traceback
			// d) we are not renormalizing but we are doing a traceback
			// in case c, we want to save the effort of finding the bestpath
			//    since that's expensive
			// so we have to check for that case after we renormalize
			if (renormalize_counter == renormalize_interval) {
				renormalize_counter = 0;
				int bestpath = history_buffer_search(distances, skip);
				history_buffer_renormalize(distances, bestpath);
				if (len == cap) {
					// reuse the bestpath found for renormalizing
					history_buffer_traceback(bestpath, min_traceback_length, output);
				}
			} else if (len == cap) {
				// not renormalizing, find the bestpath here
				int bestpath = history_buffer_search(distances, skip);
				history_buffer_traceback(bestpath, min_traceback_length, output);
			}
		}
		void history_buffer_process(char[]distances, BitWriter output) {
			history_buffer_process_skip(distances, output, 1);
		}
		void history_buffer_flush(BitWriter output) {
			history_buffer_traceback(0, 0, output);
		}

		// history is a compact history representation for every shift register
		// state,
		//    one bit per time slice
		byte[][] history;

		// which slice are we writing next?
		int index;

		// how many valid entries are there?
		int len;

		// temporary store of fetched bits
		byte[] fetched;

		// how often should we renormalize?
		int renormalize_interval;
		int renormalize_counter;
	}

	static class ErrorBuffer {
		final int num_states;
		char[][] errors;
		int index;
		char[] read_errors, write_errors;
		ErrorBuffer(int num_states) {
			// how large are the error buffers?
			this.num_states = num_states;

			// save two error metrics, one for last round and one for this
			// (double buffer)
			// the error metric is the aggregated number of bit errors found
			//   at a given path which terminates at a particular shift register state
			errors = new char[2][num_states];

			// which buffer are we using, 0 or 1?
			index = 0;
			read_errors = errors[0];
			write_errors = errors[1];
		}

		void error_buffer_reset() {
			Arrays.fill(errors[0], (char) 0);
			Arrays.fill(errors[1], (char) 0);
			index = 0;
			read_errors = errors[0];
			write_errors = errors[1];
		}

		void error_buffer_swap() {
			read_errors = errors[index];
			index ^= 1;
			write_errors = errors[index];
		}
	}

	int encoded_size(int msg_len) {
		int msgbits = 8 * msg_len;
		return rate * (msgbits + order + 1);
	}

	int encode(ByteList data, ByteList out) {
		int encoded_len_bits = encoded_size(data.readableBytes());

		bit_reader.reset(data);
		bit_writer.reset(out);

		int buf = 0;
		int mask = (1 << order)-1;
		for (int i = data.readableBytes() << 3; i > 0; i--) {
			// latest on LSB
			buf <<= 1;
			buf |= bit_reader.readBit1();
			buf &= mask;

			// 1.rate bits, 提供了rate-1bit的历史信息用于恢复数据
			bit_writer.writeBit(rate, table[buf]);
		}

		// flush, 缺少的输入填0
		for (int i = order; i >= 0; i--) {
			buf <<= 1;
			buf &= mask;

			bit_writer.writeBit(rate, table[buf]);
		}

		bit_writer.endBitWrite();

		bit_reader.list = null;
		bit_writer.list = null;

		return encoded_len_bits;
	}


	void convolutional_decode_warmup(int sets, byte[] soft) {
		// first phase: load shiftregister up from 0 (order goes from 1 to order)
		// we are building up error metrics for the first order bits
		for (int i = 0; i < order - 1 && i < sets; i++) {
			// peel off rate bits from encoded to recover the same `out` as in the encoding process
			// the difference being that this `out` will have the channel noise/errors applied
			int out;
			if (null == soft) {
				out = bit_reader.readBit(rate);
			} else {
				out = 0;
			}

			char[] read_errors = errors.read_errors;
			char[] write_errors = errors.write_errors;
			// walk all of the state we have so far
			for (int j = 0; j < (1 << (i + 1)); j += 1) {
				int last = j >>> 1;
				char dist;
				if (soft != null) {
					if (soft_measurement == CORRECT_SOFT_LINEAR) {
						dist = metric_soft_distance_linear(table[j], soft, i * rate, rate);
					} else {
						dist = metric_soft_distance_quadratic(table[j], soft, i * rate, rate);
					}
				} else {
					dist = metric_distance(table[j], out);
				}
				write_errors[j] = (char) (dist + read_errors[last]);
			}
			errors.error_buffer_swap();
		}
	}

	void convolutional_decode_inner(int sets,
									byte[] soft) {
		int highbit = 1 << (order - 1);
		for (int i = order - 1; i < (sets - order + 1); i++) {
			char[] distances = this.distances.clone();
			// lasterrors are the aggregate bit errors for the states of shiftregister for the previous
			// time slice
			if (soft != null) {
				if (soft_measurement == CORRECT_SOFT_LINEAR) {
					for (int j = 0; j < 1 << (rate); j++) {
						distances[j] = metric_soft_distance_linear(j, soft, i * rate, rate);
					}
				} else {
					for (int j = 0; j < 1 << (rate); j++) {
						distances[j] = metric_soft_distance_quadratic(j, soft, i * rate, rate);
					}
				}
			} else {
				int out = bit_reader.readBit(rate);
				for (int j = 0; j < 1 << rate; j++) {
					distances[j] = metric_distance(j, out);
				}
			}
			PairLookup pair_lookup = this.pair_lookup;
			pair_lookup.pair_lookup_fill_distance(distances);

			// a mask to get the high order bit from the shift register
			int num_iter = highbit << 1;
			char[] read_errors = errors.read_errors;
			// aggregate bit errors for this time slice
			char[] write_errors = errors.write_errors;

			byte[] history = history_buffer.history_buffer_get_slice();
			// walk through all states, ignoring oldest bit
			// we will track a best register state (path) and the number of bit errors at that path at
			// this time slice
			// this loop considers two paths per iteration (high order bit set, clear)
			// so, it only runs numstates/2 iterations
			// we'll update the history for every state and find the path with the least aggregated bit
			// errors

			// now run the main loop
			// we calculate 2 sets of 2 register states here (4 states per iter)
			// this creates 2 sets which share a predecessor, and 2 sets which share a successor
			//
			// the first set definition is the two states that are the same except for the least order
			// bit
			// these two share a predecessor because their high n - 1 bits are the same (differ only by
			// newest bit)
			//
			// the second set definition is the two states that are the same except for the high order
			// bit
			// these two share a successor because the oldest high order bit will be shifted out, and
			// the other bits will be present in the successor
			//
			int highbase = highbit >>> 1;
			for (int low = 0, high = highbit, base = 0; high < num_iter;
				 low += 8, high += 8, base += 4) {
				// shifted-right ancestors
				// low and low_plus_one share low_past_error
				//   note that they are the same when shifted right by 1
				// same goes for high and high_plus_one
				for (int offset = 0, base_offset = 0; base_offset < 4;
					 offset += 2, base_offset += 1) {
					int low_key = pair_lookup.keys[base + base_offset];
					int high_key = pair_lookup.keys[highbase + base + base_offset];
					int low_concat_dist = pair_lookup.distances[low_key];
					int high_concat_dist = pair_lookup.distances[high_key];

					char low_past_error = read_errors[base + base_offset];
					char high_past_error = read_errors[highbase + base + base_offset];

					char low_error = (char) ((low_concat_dist & 0xffff) + low_past_error);
					char high_error = (char) ((high_concat_dist & 0xffff) + high_past_error);

					int successor = low + offset;
					char error;
					byte history_mask;
					if (low_error <= high_error) {
						error = low_error;
						history_mask = 0;
					} else {
						error = high_error;
						history_mask = 1;
					}
					write_errors[successor] = error;
					history[successor] = history_mask;

					int low_plus_one = low + offset + 1;

					char low_plus_one_error = (char) ((low_concat_dist >>> 16) + low_past_error);
					char high_plus_one_error = (char) ((high_concat_dist >>> 16) + high_past_error);

					int plus_one_successor = low_plus_one;
					char plus_one_error;
					byte plus_one_history_mask;
					if (low_plus_one_error <= high_plus_one_error) {
						plus_one_error = low_plus_one_error;
						plus_one_history_mask = 0;
					} else {
						plus_one_error = high_plus_one_error;
						plus_one_history_mask = 1;
					}
					write_errors[plus_one_successor] = plus_one_error;
					history[plus_one_successor] = plus_one_history_mask;
				}
			}

			history_buffer.history_buffer_process(write_errors, bit_writer);
			errors.error_buffer_swap();
		}
	}

	void convolutional_decode_tail(int sets, byte[] soft) {
		// flush state registers
		// now we only shift in 0s, skipping 1-successors
		int highbit = 1 << (order - 1);
		for (int i = sets - order + 1; i < sets; i++) {
			// lasterrors are the aggregate bit errors for the states of shiftregister for the previous
			// time slice
			char[]read_errors = errors.read_errors;
			// aggregate bit errors for this time slice
			char[]write_errors = errors.write_errors;

			byte[] history = history_buffer.history_buffer_get_slice();

			// calculate the distance from all output states to our sliced bits
			char[] distances = this.distances;
			if (soft != null) {
				if (soft_measurement == CORRECT_SOFT_LINEAR) {
					for (int j = 0; j < 1 << rate; j++) {
						distances[j] = metric_soft_distance_linear(j, soft, i*rate, rate);
					}
				} else {
					for (int j = 0; j < 1 << rate; j++) {
						distances[j] = metric_soft_distance_quadratic(j, soft, i*rate, rate);
					}
				}
			} else {
				int out = bit_reader.readBit(rate);
				for (int j = 0; j < 1 << rate; j++) {
					distances[j] = metric_distance(j, out);
				}
			}
			int[] table = this.table;

			// a mask to get the high order bit from the shift register
			int num_iter = highbit << 1;
			int skip = 1 << (order - (sets - i));
			int base_skip = skip >>> 1;

			int highbase = highbit >>> 1;
			for (int low = 0, high = highbit, base = 0; high < num_iter;
				 low += skip, high += skip, base += base_skip) {
				int low_output = table[low];
				int high_output = table[high];
				char low_dist = distances[low_output];
				char high_dist = distances[high_output];

				char low_past_error = read_errors[base];
				char high_past_error = read_errors[highbase + base];

				char low_error = (char) (low_dist + low_past_error);
				char high_error = (char) (high_dist + high_past_error);

				int successor = low;
				char error;
				byte history_mask;
				if (low_error < high_error) {
					error = low_error;
					history_mask = 0;
				} else {
					error = high_error;
					history_mask = 1;
				}
				write_errors[successor] = error;
				history[successor] = history_mask;
			}

			history_buffer.history_buffer_process_skip(write_errors, bit_writer, skip);
			errors.error_buffer_swap();
		}
	}

	void _convolutional_decode_init(int min_traceback,
									int traceback_length, int renormalize_interval) {
		has_init_decode = true;

		distances = new char[1 << rate];
		pair_lookup = new PairLookup(rate, order, table);

		soft_measurement = CORRECT_SOFT_LINEAR;

		// we limit history to go back as far as 5 * the order of our polynomial
		history_buffer = new HistoryBuffer(min_traceback, traceback_length, renormalize_interval,
			numstates / 2, 1 << (order - 1));

		errors = new ErrorBuffer(numstates);
	}

	long _convolutional_decode(int num_encoded_bits, int num_encoded_bytes,
							   ByteList out, byte[] soft_encoded) {
		if (!has_init_decode) {
			long max_error_per_input = rate * 0xFF;
			int renormalize_interval = (int) (0xFFFF / max_error_per_input);
			_convolutional_decode_init(5 * order, 15 * order, renormalize_interval);
		}

		bit_writer.reset(out);
		int sets = num_encoded_bits / rate;

		errors.error_buffer_reset();
		history_buffer.history_buffer_reset();

		// no outputs are generated during warmup
		convolutional_decode_warmup(sets, soft_encoded);
		convolutional_decode_inner(sets, soft_encoded);
		convolutional_decode_tail(sets, soft_encoded);

		history_buffer.history_buffer_flush(bit_writer);

		bit_writer.endBitWrite();
		return bit_writer.readableBits();
	}

	// perform viterbi decoding
	// hard decoder
	long correct_convolutional_decode(ByteList encoded, int num_encoded_bits, ByteList msg) {
		if (num_encoded_bits % rate != 0) throw new IllegalArgumentException("encoded length of message must be a multiple of rate");

		int num_encoded_bytes =
			(num_encoded_bits % 8 != 0) ? (num_encoded_bits / 8 + 1) : (num_encoded_bits / 8);
		bit_reader.reset(encoded);

		return _convolutional_decode(num_encoded_bits, num_encoded_bytes, msg, null);
	}

	long correct_convolutional_decode_soft(byte[] encoded, int num_encoded_bits, ByteList msg) {
		if (num_encoded_bits % rate != 0) throw new IllegalArgumentException("encoded length of message must be a multiple of rate");

		int num_encoded_bytes = (num_encoded_bits % 8 != 0) ? (num_encoded_bits / 8 + 1) : (num_encoded_bits / 8);

		return _convolutional_decode(num_encoded_bits, num_encoded_bytes, msg, encoded);
	}

	// measure the square of the euclidean distance between x and y
	// since euclidean dist is sqrt(a^2 + b^2 + ... + n^2), the square is just
	//    a^2 + b^2 + ... + n^2
	static char metric_soft_distance_quadratic(int hard_x, byte[] soft_y, int off, int len) {
		int dist = 0;
		for (int i = 0; i < len; i++) {
			// first, convert hard_x to a soft measurement (0 -> 0, 1 - > 255)
			int soft_x = (hard_x & 1) * 255;
			hard_x >>>= 1;
			int d = soft_y[i+off] - soft_x;
			dist += d*d;
		}
		return (char) (dist >>> 3);
	}

	static char metric_soft_distance_linear(int hard_x, byte[] soft_y, int off, int len) {
		int dist = 0;
		for (int i = 0; i < len; i++) {
			int soft_x = (-(hard_x & 1)) & 0xff;
			hard_x >>= 1;
			int d = soft_y[i] - soft_x;
			dist += (d < 0) ? -d : d;
		}
		return (char) dist;
	}

	// measure the hamming distance of two bit strings
	// implemented as population count of x XOR y
	static char metric_distance(int x, int y) {
		return (char) popcount(x ^ y);
	}


	static int popcount(int x) {
		//Integer.bitCount(x)
		/* taken from the helpful http://graphics.stanford.edu/~seander/bithacks.html#CountBitsSetParallel */
		x = x - ((x >> 1) & 0x55555555);
		x = (x + (x >> 2)) & 0x33333333;
		return ((x + (x >> 4) & 0x0f0f0f0f) * 0x01010101) >> 24;
	}

}