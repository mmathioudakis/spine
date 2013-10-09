package edu.toronto.cs.propagation.sparse;

import gnu.trove.TIntDoubleHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.logging.ProgressLogger;

import java.util.Arrays;
import java.util.PriorityQueue;

import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import edu.toronto.cs.propagation.ObservationsReader;
import edu.toronto.cs.propagation.ic.ICModel;
import edu.toronto.cs.propagation.util.Arc;
import edu.toronto.cs.propagation.util.Node;
import edu.toronto.cs.propagation.util.NodeScoreEntry;
import edu.toronto.cs.propagation.util.Utilities;

public class GreedySparsifier extends Sparsifier {

	boolean incrementalLikelihoodComputation = false;

	public GreedySparsifier(ICModel model) {
		super(model);
	}

	ICModel sparsify(int k, ObservationsReader observations) {
		return sparsify(k, 1, observations, false);
	}

	public void setIncrementalLikelihoodComputation() {
		incrementalLikelihoodComputation = true;
	}

	@Override
	ICModel sparsify(int k, int numOfChunks, ObservationsReader observations, boolean reportPartial) {

		int[] allNodes = Utilities.getRandomArrayOfNodes(new IntOpenHashSet(auxiliary.getNodeActions().keySet()));
		ObjectOpenHashSet<IntOpenHashSet> nodeChunks = Utilities.partitionIntoChunks(allNodes, numOfChunks);

		int numOfNodes = 0;
		for (IntOpenHashSet chunk : nodeChunks) {
			numOfNodes += chunk.size();
		}
		if (numOfNodes != allNodes.length) {
			System.err.println("total: " + allNodes.length + ", we got: " + numOfNodes);
		}

		Int2ObjectOpenHashMap<PriorityQueue<NodeScoreEntry>> candidateParentsPerNode = new Int2ObjectOpenHashMap<PriorityQueue<NodeScoreEntry>>();
		Int2ObjectOpenHashMap<ObjectOpenHashSet<Arc>> chosenArcsPerNode = new Int2ObjectOpenHashMap<ObjectOpenHashSet<Arc>>();
		TIntDoubleHashMap logLPerNode = new TIntDoubleHashMap();

		int numOfBaseArcs = 0;
		double totalLogL = 0;
		
		LOGGER.info("GreedySparsifier 1/3: avoiding zero likelihood");

		for (IntOpenHashSet chunk : nodeChunks) {

			
			Int2ObjectOpenHashMap<IntOpenHashSet> candidateParentsPerChunkNode = new Int2ObjectOpenHashMap<IntOpenHashSet>();
			populateCandidateParents(candidateParentsPerChunkNode, chunk);

			Int2ObjectOpenHashMap<ObjectOpenHashSet<Arc>> chosenArcsPerChunkNode = new Int2ObjectOpenHashMap<ObjectOpenHashSet<Arc>>();
			double chunkLogL = getOutOfMinusInfinity(candidateParentsPerChunkNode, chosenArcsPerChunkNode, chunk, logLPerNode);
			

			// We gather the pieces
			for (int v : chosenArcsPerChunkNode.keySet()) {
				numOfBaseArcs += chosenArcsPerChunkNode.get(v).size();
			}
			totalLogL += chunkLogL;
			populateWithMaxLogL(candidateParentsPerChunkNode, candidateParentsPerNode);
			chosenArcsPerNode.putAll(chosenArcsPerChunkNode);
		}

		// Store partial log-likelihood
		if (reportPartial) {
			storePartialResult(Measure.LOG_L, numOfBaseArcs, totalLogL);
		}

		SparseDoubleMatrix2D newProbs = Node.getSparseDoubleMatrix();
		for (int v : chosenArcsPerNode.keySet()) {
			for (Arc arc : chosenArcsPerNode.get(v)) {
				double prob = originalModel.getProbability(arc.getLeaderId(), arc.getFollowerId());
				newProbs.setQuick(arc.getLeaderId(), arc.getFollowerId(), prob);
			}
		}

		// Store partial fractions
		if (reportPartial) {
			computeAndStorePartialFractionOfPropagations(observations, newProbs, numOfBaseArcs);
		}

		// Now that we are out of -Infinity for each block, we implement Greedy
		// for the remaining arcs.
		// Build priority queue with best arc to remove per block
		ProgressLogger pl = new ProgressLogger(LOGGER, ProgressLogger.TEN_SECONDS, "nodes");
		pl.start("GreedySparsifier 2/3: computing per block");
		pl.expectedUpdates = auxiliary.getNodeActions().keySet().size();
		PriorityQueue<NodeScoreEntry> pq = new PriorityQueue<NodeScoreEntry>();
		for (int v : auxiliary.getNodeActions().keySet()) {
			pl.update();
			addNextParentFromBlock(chosenArcsPerNode, candidateParentsPerNode, pq, v, logLPerNode);
		}
		pl.stop();

		pl = new ProgressLogger(LOGGER, ProgressLogger.TEN_SECONDS, "arcs");
		pl.start("GreedySparsifier 3/3: adding selected arcs and computing intermediate log likelihoods");
		int numOfArcsToAdd = k - numOfBaseArcs;
		pl.expectedUpdates = numOfArcsToAdd;
		for (int i = 0; i < numOfArcsToAdd && !pq.isEmpty(); i++) {
			pl.update();
			NodeScoreEntry entry = pq.poll();
			int v = entry.getNode();
			double logLIncrease = -entry.getLogL();
			totalLogL += logLIncrease;

			if (reportPartial) {
				storePartialResult(Measure.LOG_L, numOfBaseArcs + i + 1, totalLogL);
			}

			Arc arc = entry.getArc();
			double prob = originalModel.getProbability(arc.getLeaderId(), arc.getFollowerId());
			newProbs.setQuick(arc.getLeaderId(), arc.getFollowerId(), prob);
			addNextParentFromBlock(chosenArcsPerNode, candidateParentsPerNode, pq, v, logLPerNode);

			if (reportPartial) {
				computeAndStorePartialFractionOfPropagations(observations, newProbs, numOfBaseArcs + i + 1);
			}
		}
		pl.stop();

		return new ICModel(originalModel.getSn(), newProbs);
	}

	private void populateWithMaxLogL(Int2ObjectOpenHashMap<IntOpenHashSet> from, Int2ObjectOpenHashMap<PriorityQueue<NodeScoreEntry>> to) {
		for (int v : from.keySet()) {
			if (!to.containsKey(v)) {
				to.put(v, new PriorityQueue<NodeScoreEntry>());
			}
			for (int u : from.get(v)) {
				NodeScoreEntry entry = new NodeScoreEntry(u, -Double.MAX_VALUE);
				to.get(v).add(entry);
			}
		}
	}

	protected void addNextParentFromBlock(Int2ObjectOpenHashMap<ObjectOpenHashSet<Arc>> chosenArcsPerNode,
			Int2ObjectOpenHashMap<PriorityQueue<NodeScoreEntry>> candidateParentsPerNode, PriorityQueue<NodeScoreEntry> pq, int v, TIntDoubleHashMap logLPerNode) {

		if (incrementalLikelihoodComputation) {
			addNextParentFromBlockIncremental(chosenArcsPerNode, candidateParentsPerNode, pq, v, logLPerNode);
		} else {
			addNextParentFromBlockNonIncremental(chosenArcsPerNode, candidateParentsPerNode, pq, v, logLPerNode);
		}

	}

	/**
	 * Adds next parent from a block
	 * 
	 * @param chosenArcsPerNode
	 * @param candidateParentsPerNode
	 * @param pq
	 * @param v
	 * @param logLPerNode
	 */
	protected void addNextParentFromBlockNonIncremental(Int2ObjectOpenHashMap<ObjectOpenHashSet<Arc>> chosenArcsPerNode,
			Int2ObjectOpenHashMap<PriorityQueue<NodeScoreEntry>> candidateParentsPerNode, PriorityQueue<NodeScoreEntry> pq, int v, TIntDoubleHashMap logLPerNode) {

		Int2ObjectOpenHashMap<IntArrayList> cPlusV = auxiliary.getCplusOnline(v);
		Int2ObjectOpenHashMap<IntArrayList> cMinusV = auxiliary.getCminusOnline(v);

		double bestLogLIncrease = Double.NEGATIVE_INFINITY;

		NodeScoreEntry[] candidateEntries = candidateParentsPerNode.get(v).toArray(new NodeScoreEntry[0]);
		Arrays.sort(candidateEntries);

		for (NodeScoreEntry uEntry : candidateEntries) {
			if (-uEntry.getLogL() >= bestLogLIncrease) {
				ObjectOpenHashSet<Arc> updatedParents = new ObjectOpenHashSet<Arc>(chosenArcsPerNode.get(v));
				updatedParents.add(new Arc(uEntry.getNode(), v));
				double logLIncrease = blockLogLikelihood(v, cPlusV, cMinusV, updatedParents) - logLPerNode.get(v);
				if (logLIncrease > bestLogLIncrease) {
					bestLogLIncrease = logLIncrease;
				}
				candidateParentsPerNode.get(v).remove(uEntry);
				NodeScoreEntry updEntry = new NodeScoreEntry(uEntry.getNode(), -logLIncrease);
				candidateParentsPerNode.get(v).add(updEntry);
			}
		}

		NodeScoreEntry bestEntry = candidateParentsPerNode.get(v).poll();
		if (bestEntry != null) {
			Arc bestArc = new Arc(bestEntry.getNode(), v);
			pq.add(new NodeScoreEntry(v, -bestLogLIncrease, bestArc));
			chosenArcsPerNode.get(v).add(bestArc);
			logLPerNode.adjustOrPutValue(v, bestLogLIncrease, bestLogLIncrease);
		}
	}

	/**
	 * Adds next parent from a block; this method exploits submodularity of logL to avoid
	 * re-computation.
	 * 
	 * @param chosenArcsPerNode
	 * @param candidateParentsPerNode
	 * @param pq
	 * @param v
	 * @param logLPerNode
	 */
	protected void addNextParentFromBlockIncremental(Int2ObjectOpenHashMap<ObjectOpenHashSet<Arc>> chosenArcsPerNode,
			Int2ObjectOpenHashMap<PriorityQueue<NodeScoreEntry>> candidateParentsPerNode, PriorityQueue<NodeScoreEntry> pq, int v, TIntDoubleHashMap logLPerNode) {

		Int2ObjectOpenHashMap<IntArrayList> cPlusV = auxiliary.getCplusOnline(v);
		Int2ObjectOpenHashMap<IntArrayList> cMinusV = auxiliary.getCminusOnline(v);

		double bestLogLIncrease = Double.NEGATIVE_INFINITY;

		NodeScoreEntry[] candidateEntries = candidateParentsPerNode.get(v).toArray(new NodeScoreEntry[0]);
		Arrays.sort(candidateEntries);

		for (NodeScoreEntry uEntry : candidateEntries) {
			if (-uEntry.getLogL() >= bestLogLIncrease) {
				int extraParentId = uEntry.getNode();
				double logLIncrease = blockLogLikelihoodIncrease(v, cPlusV, cMinusV, chosenArcsPerNode.get(v), extraParentId);
				if (logLIncrease > bestLogLIncrease) {
					bestLogLIncrease = logLIncrease;
				}
				candidateParentsPerNode.get(v).remove(uEntry);
				NodeScoreEntry updEntry = new NodeScoreEntry(uEntry.getNode(), -logLIncrease);
				candidateParentsPerNode.get(v).add(updEntry);
			}
		}

		NodeScoreEntry bestEntry = candidateParentsPerNode.get(v).poll();
		if (bestEntry != null) {
			Arc bestArc = new Arc(bestEntry.getNode(), v);
			pq.add(new NodeScoreEntry(v, -bestLogLIncrease, bestArc));
			chosenArcsPerNode.get(v).add(bestArc);
			logLPerNode.adjustOrPutValue(v, bestLogLIncrease, bestLogLIncrease);
		}
	}
}
