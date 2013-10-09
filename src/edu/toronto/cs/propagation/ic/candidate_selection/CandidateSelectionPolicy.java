package edu.toronto.cs.propagation.ic.candidate_selection;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import edu.toronto.cs.propagation.util.Reflection;

/**
 * Describes a policy to decide if a parent could have activated a child.
 *
 */
public abstract class CandidateSelectionPolicy {

	public enum CandidateType {
		/**
		 * This parent could have activated the child.
		 */
		COULD_HAVE_ACTIVATED,
		
		/**
		 * This parent certainly failed to activate the child.
		 */
		FAILED_TO_ACTIVATE,
		
		/**
		 * None of the above.
		 */
		OTHER
	}

	public abstract CandidateType decideCandidateType(Int2LongOpenHashMap activationTime, int parent, int child);
	
	public static CandidateSelectionPolicy DEFAULT_CANDIDATE_SELECTION_POLICY = new SelectByTimePrecedence();

	public String toSpec() {
		return this.getClass().getSimpleName();
	}
	
	public static CandidateSelectionPolicy fromSpec(String spec) {
		return (CandidateSelectionPolicy) Reflection.instantiate(CandidateSelectionPolicy.class, spec);
	}
}
