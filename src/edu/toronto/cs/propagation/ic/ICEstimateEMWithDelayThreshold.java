package edu.toronto.cs.propagation.ic;

import edu.toronto.cs.propagation.SocialNetwork;
import edu.toronto.cs.propagation.ic.candidate_selection.CandidateSelectionPolicy;
import edu.toronto.cs.propagation.ic.candidate_selection.SelectByTimePrecedenceWithDelayThreshold;
import edu.toronto.cs.propagation.util.DelayThreshold;

/**
 * An estimator that considers that an activation cannot wait for too long.
 * <p>
 * This assumes that if a parent is activated before a child, BUT the child is activated long after
 * the parent, then that specific parent failed to activate the child and should be placed in A-
 * 
 */
public class ICEstimateEMWithDelayThreshold extends ICEstimateEM implements DelayThreshold {

	public final static double DEFAULT_DELAY_THRESHOLD_MULTIPLIER = 2.0;

	private int delayThreshold = -1;

	public ICEstimateEMWithDelayThreshold(SocialNetwork sn) {
		super(sn);
	}

	public void setDelayThreshold(int threshold) {
		this.delayThreshold = threshold;
	}

	@Override
	public CandidateSelectionPolicy getCandidateSelectionPolicy() {
		if( delayThreshold == -1 ) {
			throw new IllegalStateException("You need to set the delay threshold first");
		} else {
			return new SelectByTimePrecedenceWithDelayThreshold(delayThreshold);
		}
	}
}
