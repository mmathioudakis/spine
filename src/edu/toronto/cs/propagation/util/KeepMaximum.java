package edu.toronto.cs.propagation.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * An utility class to keep the maximum pair value in a list.
 * 
 * @author chato
 *
 */
public class KeepMaximum {
	private int keyOfMaxValue;

	private int maxValue;

	/**
	 * Initializes the maximum value to {@link Integer#MIN_VALUE}
	 */
	public void reset() {
		keyOfMaxValue = -1;
		maxValue = Integer.MIN_VALUE;
	}

	/**
	 * Adds a single key, value pair.
	 * @param key the key
	 * @param value the value that will be kept if it is larger than the larger value read so far
	 */
	void add(int key, int value) {
		if (value > maxValue) {
			keyOfMaxValue = key;
			maxValue = value;
		}
	}
	

	/**
	 * Reads a key2list map, adds as keys the keys of this map, as values the sizes of the corresponding lists 
	 * @param key2list
	 */
	public void addAllKey2Listsize(Int2ObjectOpenHashMap<IntOpenHashSet> key2list) {
		for (int node : key2list.keySet()) {
			int count = key2list.get(node).size();
			add(node, count);
		}
	}

	/**
	 * Obtains the key associated to the largest value added.
	 * 
	 * @return
	 */
	public int getMaximumKey() {
		return keyOfMaxValue;
	}
}