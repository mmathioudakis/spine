package edu.toronto.cs.propagation.util;

import org.apache.commons.lang.StringUtils;




/**
 * A propagation from a leader to a follower.
 * 
 */
public class ArcWithTimestamp extends Arc {
	
	long timestamp;
	
	public ArcWithTimestamp(int leader, int follower, long timestamp) {
		super(leader, follower);
		this.timestamp = timestamp;
	}
	
	public ArcWithTimestamp(Node leader, Node follower, long timestamp) {
		super(leader, follower);
		this.timestamp = timestamp;
	}
	
	public ArcWithTimestamp(String[] tokens) {
		this( tokens[0].length() > 0 ? new Node(tokens[0]) : null, tokens[1].length() > 0 ? new Node(tokens[1]) : null, Long.parseLong(tokens[2]));
	}

	public ArcWithTimestamp(String str) {
		this( StringUtils.splitPreserveAllTokens(str, '\t') );
	}

	@Override
	public int compareTo(Arc o) {
		if( o instanceof ArcWithTimestamp ) {
			int byTime = (int) Math.signum(this.timestamp - ((ArcWithTimestamp)o).timestamp);
			if (byTime != 0) {
				return byTime;
			} else {
				return super.compareTo(o);
			}
		} else {
			return super.compareTo(o);
		}
	}

	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public String toString() {
		return (getLeaderName() == null ? "" : getLeaderName() ) + "\t" + getFollowerName() + "\t" + timestamp;
	}

	public Arc toArc() {
		return new Arc( leader, follower );
	}
}
