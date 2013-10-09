package edu.toronto.cs.propagation.util;

public class NodeScoreEntry implements Comparable<NodeScoreEntry> {
	int node;
	double logL;
	Arc arc;

	public NodeScoreEntry(int node, double logL) {
		this.node = node;
		this.logL = logL;
	}
	
	public NodeScoreEntry(int node, double logL, Arc arc) {
		this.node = node;
		this.logL = logL;
		this.arc = arc;
	}

	public Arc getArc() {
		return arc;
	}

	public int getNode() {
		return node;
	}

	public double getLogL() {
		return logL;
	}

	public int compareTo(NodeScoreEntry arg0) {
		if (this.logL > arg0.getLogL()) {
			return +1;
		} else if (this.logL == arg0.getLogL()) {
			return 0;
		} else {
			return -1;
		}
	}
	
}
