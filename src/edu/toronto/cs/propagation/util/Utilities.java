package edu.toronto.cs.propagation.util;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.LineIterator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import cern.colt.function.DoubleDoubleFunction;
import cern.colt.function.IntIntDoubleFunction;
import cern.colt.list.IntArrayList;
import cern.colt.list.ObjectArrayList;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import cern.colt.matrix.impl.SparseObjectMatrix2D;
import edu.toronto.cs.propagation.SocialNetwork;
import edu.toronto.cs.propagation.ic.ICModel;

public class Utilities {

	/**
	 * Flips a coin (Bernoulli trial with probability d).
	 * 
	 * Examples: d=1.0 (always true), d=0.0 (always false), d=0.5 (fair coin).
	 * 
	 * @param p probability of obtaining "true"
	 * @return the outcome of the trial
	 */
	public static boolean coinFlip(double p) {
		return (Math.random() < p);
	}

	/**
	 * For two matrices A, B, computes sum( (a_ij - b_ij)^2 ).
	 * 
	 * @param mA
	 * @param mB
	 * @return
	 */
	public static double l2sq(SparseDoubleMatrix2D mA, SparseDoubleMatrix2D mB) {
		return mA.aggregate(mB, new DoubleDoubleFunction() {
			public double apply(double x, double y) {
				// sum
				return x + y;
			}
		}, new DoubleDoubleFunction() {
			public double apply(double x, double y) {
				// diff sq
				double d = x - y;
				return d * d;
			}
		});

	}

	public static void createPrefuseXML(String snFile, String modelFile,
			String xmlFile, Node startNode, double minProb, double maxProb)
			throws IOException {
		ICModel model = new ICModel(new SocialNetwork(getIterator(snFile)),
				getIterator(modelFile));
		System.out.println("Social network size: nodes="
				+ model.getSn().sizeNodes() + ", arcs="
				+ model.getSn().sizeArcs());

		PrintWriter pw = getPW(xmlFile);

		pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		pw.println("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">");
		pw.println("<graph edgedefault=\"directed\">");

		pw.println();
		pw.println("<!-- data schema -->");
		pw.println("<key id=\"name\" for=\"node\" attr.name=\"name\" attr.type=\"string\"/>");

		ObjectOpenHashSet<Node> modelNodes = new ObjectOpenHashSet<Node>();
		pw.println();
		pw.println("<!-- arcs -->");
		for (Arc arc : model.getSn().getArcs()) {
			int leaderId = arc.getLeaderId();
			if (leaderId == Node.getNullId() || leaderId == startNode.getId()) {
				continue;
			}
			int followerId = arc.getFollowerId();

			if (model.getProbability(leaderId, followerId) <= minProb
					|| model.getProbability(leaderId, followerId) > maxProb) {
				continue;
			}

			Node leader = new Node(leaderId);
			Node follower = new Node(followerId);
			pw.println("<edge source=\"" + leader.getName() + "\" target=\""
					+ follower.getName() + "\"></edge>");

			modelNodes.add(leader);
			modelNodes.add(follower);
		}

		pw.println();
		pw.println("<!-- nodes -->");
		for (Node u : modelNodes) {
			pw.println("<node id=\"" + u.getName() + "\"><data key=\"name\">"
					+ u.getName() + "</data></node>");
		}

		pw.println();
		pw.println("</graph>");
		pw.println("</graphml>");
		pw.close();
	}

	public static long getSecondsFromTimeString(String tString, Calendar cal) {
		cal.set(Integer.parseInt(tString.split(" ")[0].split("-")[0]),
				Integer.parseInt(tString.split(" ")[0].split("-")[1]),
				Integer.parseInt(tString.split(" ")[0].split("-")[2]),
				Integer.parseInt(tString.split(" ")[1].split(":")[0]),
				Integer.parseInt(tString.split(" ")[1].split(":")[1]),
				Integer.parseInt(tString.split(" ")[1].split(":")[2]));
		long whatSec = cal.getTimeInMillis() / 1000;
		return whatSec;
	}

	public static LineIterator getIterator(File file)
			throws FileNotFoundException {
		return new LineIterator(new FastBufferedReader(new FileReader(file)));
	}

	public static LineIterator getIterator(String filename)
			throws FileNotFoundException {
		return getIterator(new File(filename));
	}

	public static LineIterator getIterator(URL url)
			throws FileNotFoundException {
		return getIterator(new File(url.getFile()));
	}

	public static PrintWriter getPW(String filename) throws IOException {
		return new PrintWriter(new BufferedWriter(new FileWriter(new File(
				filename))));
	}

	public static int[] getRandomArrayOfNodes(Node[] allNodes) {
		int[] nodeIds = new int[allNodes.length];
		for (int i = 0; i < nodeIds.length; i++) {
			nodeIds[i] = allNodes[i].getId();
		}
		return getRandomArrayOfNodes(new IntOpenHashSet(nodeIds));
	}

	public static int[] getRandomArrayOfNodes(IntSet allNodes) {
		Random coin = new Random();
		TreeMap<Double, Integer> whereIsWho = new TreeMap<Double, Integer>();

		for (int u : allNodes) {
			double flip = coin.nextDouble();
			while (whereIsWho.containsKey(flip)) {
				flip = coin.nextDouble();
			}
			whereIsWho.put(flip, u);
		}

		int[] randomNodeArray = new int[allNodes.size()];
		int i = 0;
		for (double flip : whereIsWho.keySet()) {
			randomNodeArray[i] = whereIsWho.get(flip);
			i++;
		}

		return randomNodeArray;
	}

	public static <T> List<T> randomize(Set<T> set) {
		Random coin = new Random();
		TreeMap<Double, T> whereIsWho = new TreeMap<Double, T>();
		for (T element : set) {
			double flip = coin.nextDouble();
			while (whereIsWho.containsKey(flip)) {
				flip = coin.nextDouble();
			}
			whereIsWho.put(flip, element);
		}

		List<T> randomList = new ArrayList<T>();
		for (double flip : whereIsWho.keySet()) {
			randomList.add(whereIsWho.get(flip));
		}

		return randomList;
	}

	public static int min(int a, int b) {
		int result = a;
		if (b < result) {
			result = b;
		}
		return result;
	}

	public static ObjectOpenHashSet<IntOpenHashSet> partitionIntoChunks(
			int[] allNodes, int requestedNumOfChunks) {
		ObjectOpenHashSet<IntOpenHashSet> nodeChunks = new ObjectOpenHashSet<IntOpenHashSet>();

		int numOfChunks = requestedNumOfChunks;
		int totalNumOfNodes = allNodes.length;
		int nodesPerChunk = totalNumOfNodes / numOfChunks;
		// the number of chunks can be at most the number of nodes
		if (nodesPerChunk == 0) {
			nodesPerChunk = 1;
			numOfChunks = totalNumOfNodes;
		}

		// division
		int[] nodeDistribution = new int[numOfChunks];
		int remainingNodes = totalNumOfNodes;
		for (int i = 0; i < numOfChunks && remainingNodes > 0; i++) {
			nodeDistribution[i] = nodesPerChunk;
			remainingNodes -= nodesPerChunk;
		}

		// remainder
		for (int i = 0; i < numOfChunks && remainingNodes > 0; i++) {
			nodeDistribution[i] = nodeDistribution[i] + 1;
			remainingNodes--;
		}

		int index = 0;
		for (int i = 0; i < numOfChunks; i++) {
			IntOpenHashSet chunk = new IntOpenHashSet();
			for (int j = 0; j < nodeDistribution[i]; j++) {
				chunk.add(allNodes[index]);
				index++;
			}
			if (!chunk.isEmpty()) {
				nodeChunks.add(chunk);
			}
		}

		return nodeChunks;
	}

	public static <T> List<Set<T>> partition(List<T> originalList,
			int requestedNumOfPartitions) {
		List<Set<T>> chunks = new ArrayList<Set<T>>();

		int numOfChunks = requestedNumOfPartitions;
		int numOfElements = originalList.size();
		int nodesPerChunk = numOfElements / numOfChunks;
		// the number of chunks can be at most the number of nodes
		if (nodesPerChunk == 0) {
			nodesPerChunk = 1;
			numOfChunks = numOfElements;
		}

		// division
		int[] nodeDistribution = new int[numOfChunks];
		int remainingNodes = numOfElements;
		for (int i = 0; i < numOfChunks && remainingNodes > 0; i++) {
			nodeDistribution[i] = nodesPerChunk;
			remainingNodes -= nodesPerChunk;
		}

		// remainder
		for (int i = 0; i < numOfChunks && remainingNodes > 0; i++) {
			nodeDistribution[i] = nodeDistribution[i] + 1;
			remainingNodes--;
		}

		int index = 0;
		for (int i = 0; i < numOfChunks; i++) {
			Set<T> chunk = new HashSet<T>();
			for (int j = 0; j < nodeDistribution[i]; j++) {
				chunk.add(originalList.get(index));
				index++;
			}
			if (!chunk.isEmpty()) {
				chunks.add(chunk);
			}
		}

		return chunks;
	}

	public static String toString(Int2IntOpenHashMap structure) {
		String ret = "";

		if (structure == null) {
			return ret;
		}

		for (int v : structure.keySet()) {
			int num = structure.get(v);
			ret += Node.getName(v) + " :\t" + num + "\n";
		}

		return ret;
	}

	public static String toString(
			Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<SetOfParentsLogLEntry>> structure) {

		String ret = "";

		if (structure == null) {
			return ret;
		}

		for (int v : structure.keySet()) {
			Int2ObjectOpenHashMap<SetOfParentsLogLEntry> inter = structure
					.get(v);
			for (int size : inter.keySet()) {
				SetOfParentsLogLEntry entry = inter.get(size);
				ret += Node.getName(v) + "\t"
						+ entry.getSelectedParents().toString() + "\t"
						+ entry.getLogL() + "\n";
			}
		}

		return ret;

	}

	public static String toString_1(
			Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<SetOfParentsLogLEntry>> structure) {

		String ret = "";

		if (structure == null) {
			return ret;
		}

		for (int v : structure.keySet()) {
			Int2ObjectOpenHashMap<SetOfParentsLogLEntry> inter = structure
					.get(v);
			for (int size : inter.keySet()) {
				ret += Node.getName(v) + "\t<-\t" + size + "\t{ ";
				SetOfParentsLogLEntry entry = inter.get(size);
				for (int u : entry.getSelectedParents()) {
					ret += Node.getName(u) + " ";
				}
				ret += "}\t" + entry.getLogL() + "\n";
			}

		}

		return ret;

	}

	public static boolean containsWhitespace(String aString) {
		for (int i = 0; i < aString.length(); i++) {
			if (Character.isWhitespace(aString.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	/*
	 * Strips 'http://', trims whitespace, and returns up to first '/'.
	 */
	public static String getBaseUrl(final String url) {
		String outurl = url;

		if (outurl.startsWith("http://")) {
			outurl = outurl.substring(7);
		}
		int end = outurl.indexOf('/');
		if (end > -1) {
			outurl = outurl.substring(0, end);
		}
		outurl = outurl.trim();
		outurl = outurl.replaceAll("\\s", "");
		return outurl;
	}

	public static TreeSet<Arc> getSortedNonZeroArcs(SparseDoubleMatrix2D matrix) {
		final TreeSet<Arc> arcs = new TreeSet<Arc>();
		matrix.forEachNonZero(new IntIntDoubleFunction() {
			public double apply(int u, int v, double value) {
				if (value != 0.0) {
					arcs.add(new Arc(u, v));
				}
				return value;
			}
		});
		return arcs;
	}

	public static TreeSet<Arc> getSortedArcsWithHighProbability(
			SparseDoubleMatrix2D matrix, final double minProbability) {
		final TreeSet<Arc> arcs = new TreeSet<Arc>();
		matrix.forEachNonZero(new IntIntDoubleFunction() {
			public double apply(int u, int v, double value) {
				if (value > minProbability) {
					arcs.add(new Arc(u, v));
				}
				return value;
			}
		});
		return arcs;
	}

	public static TreeSet<Arc> getSortedNonZeroArcs(SparseObjectMatrix2D matrix) {
		final TreeSet<Arc> arcs = new TreeSet<Arc>();
		int cardinality = matrix.cardinality();
		IntArrayList rowList = new IntArrayList(cardinality), columnList = new IntArrayList(
				cardinality);
		ObjectArrayList valueList = new ObjectArrayList(cardinality);
		matrix.getNonZeros(rowList, columnList, valueList);
		for (int i = 0; i < rowList.size(); i++) {
			arcs.add(new Arc(rowList.get(i), columnList.get(i)));
		}
		return arcs;
	}

	public static void setToZero(final SparseDoubleMatrix2D matrix) {
		matrix.forEachNonZero(new IntIntDoubleFunction() {
			public double apply(int u, int v, double value) {
				matrix.setQuick(u, v, 0.0);
				return 0.0;
			}
		});
		matrix.trimToSize();
	}

	public static int max(int a, int b) {
		int result = a;
		if (b > result) {
			result = b;
		}
		return result;
	}

}
