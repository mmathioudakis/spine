package edu.toronto.cs.propagation.util;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class Int2ArrayOfIntMap {
	
	private final Int2IntOpenHashMap key2pos;
	private final int[][] data;
	
	public Int2ArrayOfIntMap(Int2ObjectOpenHashMap<IntOpenHashSet> input) {
		
		// Gather information about the input
		int nkeys = input.keySet().size();
		key2pos = new Int2IntOpenHashMap(nkeys);
		key2pos.defaultReturnValue(-1); // to catch errors
		
		data = new int[nkeys][];
		int pos = 0;
		for( int key: input.keySet() ) {
			key2pos.put(key, pos);
			data[pos] = input.get(key).toArray( new int[] {} );
			pos++;
		}
	}
	
	public int[] get(int key) {
		return key2pos.get(key) >= 0 ? data[key2pos.get(key)] : null;
	}
	
	public IntSet keySet() {
		return key2pos.keySet();
	}

}
