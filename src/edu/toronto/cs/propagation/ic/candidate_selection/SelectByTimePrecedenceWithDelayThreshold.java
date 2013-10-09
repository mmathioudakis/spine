package edu.toronto.cs.propagation.ic.candidate_selection;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/**
 * Under this policy, a parent could have activated a child if the parent was activated before the child, but no more than {@link #delayThreshold} time units before.
 *
 */
public class SelectByTimePrecedenceWithDelayThreshold extends CandidateSelectionPolicy {
	
	private int delayThreshold;

	public SelectByTimePrecedenceWithDelayThreshold(int delayThreshold) {
		this.delayThreshold = delayThreshold;
	}
	
	@Override
	public CandidateType decideCandidateType(Int2LongOpenHashMap activationTime, int parent, int child) {
		// Parent was not active, do nothing
		if (!activationTime.containsKey(parent) ) {
			return CandidateType.OTHER;
		}
		
		if (activationTime.containsKey(child)) {
			long childActivationTime = activationTime.get(child);
			long parentActivationTime = activationTime.get(parent);
			if (childActivationTime >= parentActivationTime) {
				// parent was activated, child was activated after

				if (childActivationTime <= parentActivationTime + delayThreshold) {
					// parent was activated, and it may have succeeded because
					// it activated child before the timeout
					return CandidateType.COULD_HAVE_ACTIVATED;

				} else {
					// parent was activated, but failed because too long time
					// passed before child was activated
					return CandidateType.FAILED_TO_ACTIVATE;
				}
			} else {
				return CandidateType.OTHER;
			}

		} else {
			// parent was activated, child was not activated
			return CandidateType.FAILED_TO_ACTIVATE;
		}
	}
	
	void setDelayThreshold(int delayThreshold) {
		this.delayThreshold = delayThreshold;
	}
	
	int getDelayThreshold() {
		return delayThreshold;
	}
	
	public static CandidateSelectionPolicy fromSpec(String spec) {
		String[] tokens = spec.split(",", 2 );
		CandidateSelectionPolicy candidateSelectionPolicy= CandidateSelectionPolicy.fromSpec(tokens[0]);
		
		((SelectByTimePrecedenceWithDelayThreshold)candidateSelectionPolicy).setDelayThreshold(Integer.parseInt(tokens[1]));
		return candidateSelectionPolicy;
	}
	
	public String toSpec() {
		return super.toSpec() + "," + getDelayThreshold();
	}
}

