package edu.toronto.cs.propagation.util;


public class ProbArcEntry implements Comparable<ProbArcEntry> {
	private double prob;
	private Arc arc;

	public ProbArcEntry(double prob, Arc arc) {
		if( prob < 0.0 || prob > 1.0 ) {
			throw new IllegalArgumentException("Probability out of range: '" + prob + "'");
		}
		this.prob = prob;
		this.arc = arc;
	}

	public double getProb() {
		return prob;
	}

	public int compareTo(ProbArcEntry o) {
		return (int)Math.signum(this.prob-o.prob);
	}

	public Arc getArc() {
		return arc;
	}
}
