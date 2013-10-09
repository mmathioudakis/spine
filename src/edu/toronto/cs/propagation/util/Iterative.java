package edu.toronto.cs.propagation.util;

public interface Iterative {
	public final static int DEFAULT_MAX_ITERATIONS = 100000;

	public final static double DEFAULT_MIN_DIFFERENCE = 1e-9;

	public void setMaxIterations(int maxIterations);
	public void setMinDifference(double minDifference);
}
