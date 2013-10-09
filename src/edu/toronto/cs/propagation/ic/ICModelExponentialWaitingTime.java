package edu.toronto.cs.propagation.ic;

import it.unimi.dsi.io.LineIterator;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import edu.toronto.cs.propagation.SocialNetwork;
import edu.toronto.cs.propagation.util.distribution.ExponentialDistribution;

public class ICModelExponentialWaitingTime extends ICModelTime {
	
	static double MEAN_POST_WAITING_TIME = 60;
	
	static double MEAN_REPOST_WAITING_TIME = 60;

	public static double getMEAN_POST_WAITING_TIME() {
		return MEAN_POST_WAITING_TIME;
	}

	public static double getMEAN_REPOST_WAITING_TIME() {
		return MEAN_REPOST_WAITING_TIME;
	}

	@Override
	void initWaitingTimeDistributions() {
		postWaitingTimeDistribution = new ExponentialDistribution(1.0 / MEAN_POST_WAITING_TIME);
		repostWaitingTimeDistribution = new ExponentialDistribution(1.0 / MEAN_REPOST_WAITING_TIME);
	}

	public ICModelExponentialWaitingTime(SocialNetwork sn, SparseDoubleMatrix2D probs) {
		super(sn, probs);
		initWaitingTimeDistributions();
	}

	public ICModelExponentialWaitingTime(SocialNetwork sn, LineIterator it) {
		super(sn, it);
		initWaitingTimeDistributions();
	}
}
