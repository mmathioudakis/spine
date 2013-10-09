package edu.toronto.cs.propagation.util;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

public class SetOfParentsLogLEntry {
	double logL;
	
	IntOpenHashSet selectedParents;

	public SetOfParentsLogLEntry(double logL, IntOpenHashSet selectedParents) {
		this.logL = logL;
		this.selectedParents = selectedParents;
	}

	public double getLogL() {
		return logL;
	}

	public IntOpenHashSet getSelectedParents() {
		return selectedParents;
	}
}