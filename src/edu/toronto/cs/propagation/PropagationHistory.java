package edu.toronto.cs.propagation;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.io.PrintStream;
import java.util.Collection;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import cern.colt.GenericSorting;
import cern.colt.Swapper;
import cern.colt.function.IntComparator;
import edu.toronto.cs.propagation.util.Arc;
import edu.toronto.cs.propagation.util.ArcWithTimestamp;

/**
 * Describes the propagation of an action, including a series of time-stamped
 * events.
 */
public class PropagationHistory {

	static Logger LOGGER = Logger.getLogger(PropagationHistory.class);
	static {
		BasicConfigurator.resetConfiguration();
		BasicConfigurator.configure();
	}

	private String description;
	private ObjectArrayList<ArcWithTimestamp> events;

	/**
	 * Creates a propagation history, ensuring there are no duplicate
	 * propagations and that the history is sorted by timestamp.
	 * 
	 * @param reposts
	 */
	public PropagationHistory(String description, Collection<ArcWithTimestamp> reposts) {
		this.description = description;
		events = new ObjectArrayList<ArcWithTimestamp>();
		ObjectOpenHashSet<Arc> seen = new ObjectOpenHashSet<Arc>();
		for (ArcWithTimestamp repost : reposts) {
			Arc propagationArc = repost.toArc();
			try {
				boolean alreadySeen = false;
				try {
					alreadySeen = seen.contains(propagationArc);
				} catch (NullPointerException e) {
					System.out.println(e + "\t" + propagationArc + "\t"
							+ repost);
				}
				if (alreadySeen) {
					throw new IllegalArgumentException("This propagation: "
							+ repost.toFullString() + " has been already seen");
				} else {
					seen.add(propagationArc);
					events.add(repost);
				}
			} catch (NullPointerException e) {
				System.err.println(e);
			}
		}

		// Sort members unless already sorted
		// If the members are already sorted, there may be ties by timestamp --
		// we do not want to break their ordering
		if (!isSortedByNonDecreasingTimestamp()) {
			LOGGER.info("Sorting propagation history");
			sortByTimestamp();
		}

		IntOpenHashSet active = new IntOpenHashSet();
		// Check that everybody is reachable

		for (ArcWithTimestamp propagation : getEvents()) {
			// Check that the leader was active
			if (propagation.getLeaderName() != null) {
				// Except for self-activations
				if (!active.contains(propagation.getLeaderId())) {
					dump();
					throw new IllegalArgumentException(
							"This propagation could not have happened as the parent node was not active: "
									+ propagation);
				}
			}

			// Check that the follower was not yet active
			if (active.contains(propagation.getFollowerId())) {
				dump();
				throw new IllegalArgumentException(
						"This propagation activates a node that was already active: "
								+ propagation);
			}
			active.add(propagation.getFollowerId());
		}
	}

	/**
	 * Creates a propagation history, ensuring there are no duplicate
	 * propagations and that the history is sorted by timestamp.
	 * 
	 * @param reposts
	 */
	public PropagationHistory(Collection<ArcWithTimestamp> reposts) {
		this(null, reposts);
	}

	private boolean isSortedByNonDecreasingTimestamp() {
		for (int i = 0; i < events.size() - 1; i++) {
			if (events.get(i).getTimestamp() > events.get(i + 1).getTimestamp()) {
				return false;
			}
		}
		return true;
	}

	public void dump() {
		print(System.err);
	}

	public ObjectArrayList<ArcWithTimestamp> getEvents() {
		return events;
	}

	private void print(PrintStream ps) {
		for (int i = 0; i < events.size(); i++) {
			ps.println(events.get(i).toString());
		}
	}

	public void saveTo(PrintStream ps) {
		print(ps);
	}

	public int size() {
		return events.size();
	}

	public void sortByTimestamp() {
		GenericSorting.quickSort(0, events.size(), new IntComparator() {
			public int compare(int i, int j) {
				long ts1 = events.get(i).getTimestamp();
				long ts2 = events.get(j).getTimestamp();
				return (int) Math.signum(ts1 - ts2);
			}
		}, new Swapper() {
			public void swap(int i, int j) {
				ArcWithTimestamp tmpEvent = events.get(i);
				events.set(i, events.get(j));
				events.set(j, tmpEvent);
			}
		});
	}

	public String toString() {
		sortByTimestamp();
		StringBuffer sb = new StringBuffer();
		for (ArcWithTimestamp event : events) {
			if (sb.length() > 0) {
				sb.append("-(" + event.getTimestamp() + ")->");
			}
			sb.append(event.getFollowerName());
		}
		return sb.toString();
	}

	public String getDescription() {
		return description;
	}
}
