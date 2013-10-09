package edu.toronto.cs.propagation.util.distribution;

public class ConstantDistribution implements TimeDistribution {
	int x;
	public ConstantDistribution(int x) {
		this.x = x;
	}
	public double sample() {
		return x;
	}
}
