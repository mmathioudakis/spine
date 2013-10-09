package edu.toronto.cs.propagation.ic.candidate_selection;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/**
 * Under this policy, a parent could have activated a child if the parent was activated before the child.
 *
 */
public class SelectByTimePrecedence extends CandidateSelectionPolicy {
	
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
				return CandidateType.COULD_HAVE_ACTIVATED;

			} else {
				// parent was activated, child was already activated
				return CandidateType.OTHER;
			}

		} else {
			// parent was activated, child was not activated
			return CandidateType.FAILED_TO_ACTIVATE;
		}
	}
}

