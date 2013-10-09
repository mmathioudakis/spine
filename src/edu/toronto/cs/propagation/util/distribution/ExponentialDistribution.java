package edu.toronto.cs.propagation.util.distribution;

public class ExponentialDistribution implements TimeDistribution {
	double lambda;
	public ExponentialDistribution(double lambda) {
		this.lambda = lambda;
	}
	public double sample() {
		return -Math.log(Math.random())/lambda;
	}
}
