package edu.toronto.cs.propagation.sparse;

import java.util.Comparator;

import edu.toronto.cs.propagation.ic.ICModel;
import edu.toronto.cs.propagation.util.Arc;
import edu.toronto.cs.propagation.util.ProbArcEntry;

/**
 * The random sparsifier adds the arcs in random order, using {@link Arc#hashCode()}.
 *
 */
public class NaiveByRandomSparsifier extends NaiveSparsifier {

	public NaiveByRandomSparsifier(ICModel model) {
		super(model);
	}

	@Override
	Comparator<ProbArcEntry> getComparator() {
		return new Comparator<ProbArcEntry>() {
			public int compare(ProbArcEntry e1, ProbArcEntry e2) {
				// Random order, EXCEPT zeros that should be at the end
				if( e2.getProb() == 0 ) {					
					if( e1.getProb() == 0 ) {
						// e1 and e2 are 0 => equal
						return 0;
					} else {
						// e1 is non-zero but e2 is non-zero => e2 is larger 
						return -1;
					}
				} else if( e1.getProb() == 0 ) {
					// e1 is zero but e2 is non-zero => e1 is larger
					return 1;
				} else {
					// both are non-zero: random order
					return (int) Math.signum(e1.getArc().hashCode() - e2.getArc().hashCode());
				}
			}
		};
	}
}
