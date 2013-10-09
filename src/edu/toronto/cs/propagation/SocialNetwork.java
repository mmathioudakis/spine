package edu.toronto.cs.propagation;

import it.unimi.dsi.io.LineIterator;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.TreeMap;
import java.util.TreeSet;

import cern.colt.matrix.impl.SparseDoubleMatrix2D;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;

import edu.toronto.cs.propagation.ic.ICModel;
import edu.toronto.cs.propagation.util.Arc;
import edu.toronto.cs.propagation.util.Node;
import edu.toronto.cs.propagation.util.Utilities;

/**
 * A social network
 */
public class SocialNetwork {

	private TreeSet<Arc> arcs = new TreeSet<Arc>();

	private TreeMap<Node, TreeSet<Arc>> followerArcs = new TreeMap<Node, TreeSet<Arc>>();

	private TreeMap<Node, TreeSet<Arc>> leaderArcs = new TreeMap<Node, TreeSet<Arc>>();

	private TreeSet<Node> nodes = new TreeSet<Node>();

	public SocialNetwork(LineIterator it) {
		while (it.hasNext()) {
			String str = it.next().toString();
			try {
				if (str.startsWith("#")) {
					continue;
				}
				String[] tokens = str.split("\t");
				Node leader = new Node(tokens[0]);
				Node follower = new Node(tokens[1]);
				Arc arc = new Arc(leader, follower);
				if (!nodes.contains(leader)) {
					nodes.add(leader);
				}
				if (!nodes.contains(follower)) {
					nodes.add(follower);
				}
				if (!arcs.contains(arc)) {
					arcs.add(arc);
					if (!followerArcs.containsKey(leader)) {
						followerArcs.put(leader, new TreeSet<Arc>());
					}
					followerArcs.get(leader).add(arc);
					if (!leaderArcs.containsKey(follower)) {
						leaderArcs.put(follower, new TreeSet<Arc>());
					}
					leaderArcs.get(follower).add(arc);
				}
			} catch (IllegalArgumentException e) {
				System.err.println(str);
				e.printStackTrace();
			}
		}
	}
	
	public SocialNetwork(ICModel model, double minProbability) {
		SparseDoubleMatrix2D probs = model.getProbs();
		TreeSet<Arc> selectedArcs = Utilities.getSortedArcsWithHighProbability(probs, minProbability);
		for (Arc arc: selectedArcs) {
			Node leader = new Node(arc.getLeaderName());
			Node follower = new Node(arc.getFollowerName());
			if (!nodes.contains(leader)) {
				nodes.add(leader);
			}
			if (!nodes.contains(follower)) {
				nodes.add(follower);
			}
			if (!arcs.contains(arc)) {
				arcs.add(arc);
				if (!followerArcs.containsKey(leader)) {
					followerArcs.put(leader, new TreeSet<Arc>());
				}
				followerArcs.get(leader).add(arc);
				if (!leaderArcs.containsKey(follower)) {
					leaderArcs.put(follower, new TreeSet<Arc>());
				}
				leaderArcs.get(follower).add(arc);
			}
		}
	}

	public boolean containsNode(Node startNode) {
		return nodes.contains(startNode);
	}

	public boolean containsNode(String nodeName) {
		return containsNode(new Node(nodeName));
	}

	public void dump() {
		for (Arc arc : arcs) {
			System.out.println(arc.getLeaderName() + "\t" + arc.getFollowerName());
		}
	}
	
	public void dump(PrintWriter pw) {
		for (Arc arc : arcs) {
			pw.println(arc.getLeaderName() + "\t" + arc.getFollowerName());
		}
	}

	public TreeSet<Arc> getArcs() {
		return arcs;
	}

	public TreeSet<Node> getNodes() {
		return nodes;
	}

	public int sizeNodes() {
		return nodes.size();
	}

	public int sizeArcs() {
		return arcs.size();
	}

	/**
	 * Gets the list of people following a leader ("followers")
	 * 
	 * @param leader
	 *            the leader
	 * @return a set of people represented as edges in the social network
	 */
	public TreeSet<Arc> getFollowers(Node leader) {
		if (followerArcs.containsKey(leader)) {
			return followerArcs.get(leader);
		} else {
			return new TreeSet<Arc>();
		}
	}
	
	public TreeSet<Arc> getFollowers(int nodeid) {
		return getFollowers(new Node(nodeid));
	}

	/**
	 * Gets the list of people that a follower is following ("followees")
	 * 
	 * @param follower
	 *            the follower
	 * @return a set of people represented as edges in the social network
	 */
	public TreeSet<Arc> getLeaders(Node follower) {
		if (leaderArcs.containsKey(follower)) {
			return leaderArcs.get(follower);
		} else {
			return new TreeSet<Arc>();
		}
	}
	
	public TreeSet<Arc> getLeaders(int nodeid) {
		return getLeaders(new Node(nodeid));
	}

	public static void main(String[] args) throws JSAPException,
			FileNotFoundException {
		final SimpleJSAP jsap = new SimpleJSAP(
				SocialNetwork.class.getName(),
				"Reads and parses a social network graph, for debugging purposes.",
				new Parameter[] { new FlaggedOption("social-network",
						JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED,
						's', "social-network",
						"The file containing the social network graph"), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) {
			return;
		}

		String inputFilename = jsapResult.getString("social-network");
		SocialNetwork sn = new SocialNetwork(
				Utilities.getIterator(inputFilename));
		sn.dump();
	}
}
