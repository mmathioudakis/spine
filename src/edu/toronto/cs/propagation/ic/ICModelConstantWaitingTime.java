package edu.toronto.cs.propagation.ic;

import it.unimi.dsi.io.LineIterator;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import edu.toronto.cs.propagation.SocialNetwork;
import edu.toronto.cs.propagation.util.distribution.ConstantDistribution;

/**
 * Independent cascades with constant waiting time is constant.
 * 
 */
public class ICModelConstantWaitingTime extends ICModelTime {

	public final static int WAITING_TIME = 1;

	@Override
	void initWaitingTimeDistributions() {
		postWaitingTimeDistribution = new ConstantDistribution(WAITING_TIME);
		repostWaitingTimeDistribution = new ConstantDistribution(WAITING_TIME);
	}
	
	public ICModelConstantWaitingTime(SocialNetwork sn, SparseDoubleMatrix2D probs) {
		super(sn, probs);
		initWaitingTimeDistributions();
	}

	public ICModelConstantWaitingTime(SocialNetwork sn, LineIterator it) {
		super(sn, it);
		initWaitingTimeDistributions();
	}
}
