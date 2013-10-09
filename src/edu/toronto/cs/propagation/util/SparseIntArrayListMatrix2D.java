package edu.toronto.cs.propagation.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import cern.colt.matrix.impl.SparseObjectMatrix2D;

public class SparseIntArrayListMatrix2D extends SparseObjectMatrix2D {

	private static final long serialVersionUID = 1L;

	public SparseIntArrayListMatrix2D(int rows, int columns, int initialCapacity, double minLoadFactor, double maxLoadFactor) {
		super(rows, columns, initialCapacity, minLoadFactor, maxLoadFactor);
	}
	
	@Override
	public
	IntArrayList getQuick(int row, int col) {
		return ((IntArrayList) super.getQuick(row,col));
	}

	public int getListSize(int row, int col) {
		IntArrayList list = getQuick(row,col);
		if( list == null ) {
			return 0;
		} else {
			return list.size();
		}
	}
	
	
}
