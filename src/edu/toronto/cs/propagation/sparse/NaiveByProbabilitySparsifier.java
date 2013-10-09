package edu.toronto.cs.propagation.sparse;

import java.util.Comparator;

import edu.toronto.cs.propagation.ic.ICModel;
import edu.toronto.cs.propagation.util.ProbArcEntry;

/**
 * 
 * This sparsifier adds the arc in order of decreasing probability.
 *
 */
public class NaiveByProbabilitySparsifier extends NaiveSparsifier {

	public NaiveByProbabilitySparsifier(ICModel model) {
		super(model);
	}

	Comparator<ProbArcEntry> getComparator() {
		return new Comparator<ProbArcEntry>() {
			public int compare(ProbArcEntry e1, ProbArcEntry e2) {
				return (int) Math.signum(e2.getProb() - e1.getProb());
			}
		};
	}
}
