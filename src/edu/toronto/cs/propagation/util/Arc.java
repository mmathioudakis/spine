package edu.toronto.cs.propagation.util;

/**
 * A directed arc in a social network, from a "leader" to a "follower"
 * 
 */
public class Arc implements Comparable<Arc> {

	int follower;

	int leader;

	public Arc(Node leader, Node follower) {
		if( leader == null && follower == null ) {
			throw new IllegalArgumentException("The members of the arc can not be both null");
		} else if( leader != null && follower != null && leader.equals(follower) ) {
			throw new IllegalArgumentException("The members of the arc can not be equal: " + leader + "==" + follower );
		}
		this.leader = leader == null ? 0 : leader.getId();
		this.follower = follower == null ? 0 : follower.getId();
		
	}

	public Arc(int leader, int follower) {
		this.leader = leader;
		this.follower = follower;
	}

	public int compareTo(Arc o) {
		int c1 = this.getLeaderName().compareTo(o.getLeaderName());
		if (c1 == 0) {
			return this.getFollowerName().compareTo(o.getFollowerName());
		} else {
			return c1;
		}
	}

	public boolean equals(Object oOther) {
		if (oOther instanceof Arc) {
			Arc aOther = (Arc) oOther;
			return ( (leader == aOther.leader) && (follower == aOther.follower));
		} else {
			return false;
		}
	}

	public String getFollowerName() {
		return Node.getName(follower);
	}

	public String getLeaderName() {
		return Node.getName(leader);
	}
	
	public int getLeaderId() {
		return leader;
	}
	
	public int getFollowerId() {
		return follower;
	}

	@Override
	public int hashCode() {
		return leader ^ follower;
	}

	@Override
	public String toString() {
		return "(" + leader + "," + follower + ")";
	}
	
	public String toFullString() {
		return "(" + getLeaderName() + "," + getFollowerName() + ")";
	}

	public boolean hasNullLeader() {
		return (leader == 0);
	}
}
