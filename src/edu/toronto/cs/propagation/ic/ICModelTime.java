package edu.toronto.cs.propagation.ic;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import it.unimi.dsi.io.LineIterator;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import edu.toronto.cs.propagation.PropagationHistory;
import edu.toronto.cs.propagation.SocialNetwork;
import edu.toronto.cs.propagation.util.Arc;
import edu.toronto.cs.propagation.util.ArcWithTimestamp;
import edu.toronto.cs.propagation.util.Node;
import edu.toronto.cs.propagation.util.Utilities;
import edu.toronto.cs.propagation.util.distribution.TimeDistribution;

public abstract class ICModelTime extends ICModel {

	TimeDistribution postWaitingTimeDistribution = null;
	TimeDistribution repostWaitingTimeDistribution = null;
	
	public ICModelTime(SocialNetwork sn, SparseDoubleMatrix2D probs) {
		super(sn, probs);
		initWaitingTimeDistributions();
	}

	public ICModelTime(SocialNetwork sn, LineIterator it) {
		super(sn, it);
		initWaitingTimeDistributions();
	}
	
	abstract void initWaitingTimeDistributions();
	
	@Override
	public PropagationHistory runModel(Node startNode) {
		if (!sn.containsNode(startNode)) {
			throw new IllegalArgumentException("Start node does not belong to the social network");
		}
		ObjectArrayList<ArcWithTimestamp> propagations = new ObjectArrayList<ArcWithTimestamp>();
		IntArrayList activated = new IntArrayList();
		activated.add(startNode.getId());
		propagations.add(new ArcWithTimestamp(null, startNode, 0));

		ObjectHeapPriorityQueue<ArcWithTimestamp> activationAttempts = new ObjectHeapPriorityQueue<ArcWithTimestamp>();
		insertPropagationAttempts(0, startNode.getId(), activated, activationAttempts, postWaitingTimeDistribution);

		while (!activationAttempts.isEmpty()) {
			ArcWithTimestamp attempt = activationAttempts.dequeue();
			int followerId = attempt.getFollowerId();
			if (!activated.contains(followerId)) {
				activated.add(followerId);
				propagations.add(new ArcWithTimestamp(attempt.getLeaderId(), followerId, attempt.getTimestamp()));
				insertPropagationAttempts(attempt.getTimestamp(), followerId, activated, activationAttempts, repostWaitingTimeDistribution);
			}
		}

		return new PropagationHistory(propagations);
	}
	
	private void insertPropagationAttempts(long currentTime, int leaderId, IntArrayList activated, ObjectHeapPriorityQueue<ArcWithTimestamp> activationAttempts,
			TimeDistribution waitingTimeDistribution) {
		for (Arc followerArc : sn.getFollowers(leaderId)) {
			int followerId = followerArc.getFollowerId();
			if (!activated.contains(followerId)) {
				if (Utilities.coinFlip(getProbability(leaderId, followerId))) {
					long activationTime = currentTime + (long) waitingTimeDistribution.sample();
					activationAttempts.enqueue(new ArcWithTimestamp(leaderId, followerId, activationTime));
				}
			}
		}
	}
}
