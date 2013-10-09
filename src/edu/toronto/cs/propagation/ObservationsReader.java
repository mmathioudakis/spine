package edu.toronto.cs.propagation;

import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.LineIterator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Provides an abstract way of iterating through a set of observations.
 */
public class ObservationsReader implements Iterable<PropagationHistory> {

	File file;
	List<PropagationHistory> propagations;
	int size = -1;

	public ObservationsReader(String fileName) {
		file = new File(fileName);
		readSize();
	}

	public ObservationsReader(URL url) {
		file = new File(url.getFile());
		readSize();
	}
	
	public ObservationsReader(List<PropagationHistory> propagations) {
		this.propagations = propagations;
		size = propagations.size();
	}
	
	/**
	 * Gets an unordered set of propagations.
	 * 
	 * @param propagations
	 */
	@Deprecated
	public ObservationsReader(Set<PropagationHistory> propagations) {
		this.propagations = new ArrayList<PropagationHistory>(propagations);
		size = propagations.size();
	}
	

	public Iterator<PropagationHistory> iterator() {
		if (file != null) {
			return new ObservationsIterator(file);
		} else {
			return propagations.iterator();
		}
	}

	public int size() {
		return size;
	}

	private void readSize() {
		size = 0;
		LineIterator lineIterator;
		try {
			lineIterator = new LineIterator(new FastBufferedReader(
					new FileReader(file)));
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("File not found: '" + file + "'");
		}
		while (lineIterator.hasNext()) {
			String str = lineIterator.next().toString();
			if (str.startsWith("\t")) {
				size++;
			}
		}
	}
}
