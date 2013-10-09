package edu.toronto.cs.propagation.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;

/**
 * A node in a social network
 * 
 */
public class Node implements Comparable<Node> {

	private static String defaultStartNodeName = "omega";

	private static Object2IntOpenHashMap<String> name2id;

	private static Int2ObjectOpenHashMap<String> id2name;

	public static Node DEFAULT_START_NODE;

	static int lastid;

	static {
		resetIds();
	}

	public static void resetIds() {
		name2id = new Object2IntOpenHashMap<String>();
		id2name = new Int2ObjectOpenHashMap<String>();
		lastid = 0;
		DEFAULT_START_NODE = new Node(defaultStartNodeName);
	}

	public static int getNullId() {
		return 0;
	}

	public static int getDefaultStartNodeId() {
		return 1;
	}

	/**
	 * This is the only function allowed to build a sparse matrix of doubles; this is because this is
	 * the only function that knows what is the maximum nodeid.
	 * 
	 * Do not create a {@link SparseDoubleMatrix2D} outside this function.
	 * 
	 * @return
	 */
	public static SparseDoubleMatrix2D getSparseDoubleMatrix() {
		// Expect around 50 elements per node.
		// Allow occupation to be between 5% and 80% (default is 25% and 50%).
		return new SparseDoubleMatrix2D(lastid + 1, lastid + 1, (lastid+1)*50, 0.05, 0.8);
	}
	
	public static SparseIntArrayListMatrix2D getSparseObjectMatrix() {
		// Expect around 50 elements per node.
		// Allow occupation to be between 5% and 80% (default is 25% and 50%).
		return new SparseIntArrayListMatrix2D(lastid + 1, lastid + 1, (lastid+1)*50, 0.05, 0.8);
	}

	private int id;

	public static int getId(String name) {
		if (name == null) {
			return 0;
		}
		if (name2id.containsKey(name)) {
			return name2id.getInt(name);
		} else {
			if (lastid == Integer.MAX_VALUE - 1) {
				throw new IllegalStateException("Overflow in number of nodes");
			}
			lastid++;
			name2id.put(name, lastid);
			id2name.put(lastid, name);
			return lastid;
		}
	}

	public static String getName(int id) {
		if (id == 0) {
			return null;
		} else if (!id2name.containsKey(id)) {
			throw new IllegalArgumentException("Don't know the name of node with id=" + id);
		} else {
			return id2name.get(id);
		}
	}

	public Node(String name) {
		if (name == null || name.length() == 0) {
			throw new IllegalArgumentException("The name of the node can not be null or an empty string");
		}
		if (Utilities.containsWhitespace(name)) {
			throw new IllegalArgumentException("The name of the node can not contain white spaces");
		}
		this.id = getId(name);
	}

	public Node(int aId) {
		if ((aId > 0) && (!id2name.containsKey(aId))) {
			throw new IllegalArgumentException("Don't know this id: " + aId);
		}
		this.id = aId;
	}

	public int compareTo(Node o) {
		return getName().compareTo(o.getName());
	}

	@Override
	public boolean equals(Object oOther) {
		return (oOther instanceof Node) && (id == ((Node) oOther).id);
	}

	public String getName() {
		return getName(id);
	}

	public int getId() {
		return id;
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public String toString() {
		return getName();
	}
}
