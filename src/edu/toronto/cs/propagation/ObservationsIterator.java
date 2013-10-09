package edu.toronto.cs.propagation;

import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.LineIterator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.URL;
import java.util.Iterator;
import java.util.Vector;

import edu.toronto.cs.propagation.util.ArcWithTimestamp;

public class ObservationsIterator implements Iterator<PropagationHistory> {
	
	final LineIterator lineIterator;
	
	public ObservationsIterator(File file) {
		try {
			lineIterator = new LineIterator(new FastBufferedReader(new FileReader(file)));
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException( "File not found: '" + file + "'");
		}
	}
	
	public ObservationsIterator(String obsFilename) {
		this(new File(obsFilename));
	}
	
	public ObservationsIterator(URL url) {
		this(new File(url.getFile()));
	}

	public boolean hasNext() {
		return lineIterator.hasNext() || (nextLine != null);
	}
	
	String nextLine = null;

	public PropagationHistory next() {
		String description = null;
		Vector<ArcWithTimestamp> propagations = new Vector<ArcWithTimestamp>();
		if (nextLine != null) { // we found an @ meme line or tab
			if (nextLine.startsWith("@")) {
				description = nextLine.substring(1).trim();
			} else {
				propagations.add(new ArcWithTimestamp(nextLine));
			}
			nextLine = null;
		}
		while (lineIterator.hasNext()) {
			String str = lineIterator.next().toString();
			if (str.startsWith("#")) {
				continue;
			}
			if (str.startsWith("@")) {
				if (propagations.size() > 0) {
					nextLine = str;
					return new PropagationHistory(description, propagations);
				} else {
					description = str.substring(1).trim();
					continue;
				}
			}
			if (str.startsWith("\t") && propagations.size() > 0 ) {
				nextLine = str;
				return new PropagationHistory(description, propagations);
			}
			propagations.add(new ArcWithTimestamp(str));
		}
		return new PropagationHistory(description, propagations);
	}

	public void remove() {
		throw new IllegalStateException("Can't do this operation");
	}

}
