package edu.toronto.cs.propagation.sparse;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.logging.ProgressLogger;

import java.util.Comparator;
import java.util.PriorityQueue;

import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import edu.toronto.cs.propagation.ObservationsReader;
import edu.toronto.cs.propagation.ic.ICModel;
import edu.toronto.cs.propagation.util.Arc;
import edu.toronto.cs.propagation.util.Node;
import edu.toronto.cs.propagation.util.ProbArcEntry;
import edu.toronto.cs.propagation.util.Utilities;

/**
 * 
 * The Naive sparsifier adds the arcs in some fixed order determined by
 * {@link #getComparator()}
 * 
 */
public abstract class NaiveSparsifier extends Sparsifier {

	Node OMEGA = Node.DEFAULT_START_NODE;
	int MAX_REPORT_POINTS = 30;

	public NaiveSparsifier(ICModel model) {
		super(model);
	}

	/**
	 * Gets a comparator to order the arcs
	 * 
	 * @return the comparator used to order arcs
	 */
	abstract Comparator<ProbArcEntry> getComparator();

	@Override
	ICModel sparsify(int k, int numOfChunks, ObservationsReader observations,
			boolean reportPartial) {
		// ignores request for paralel computation with specified number of
		// chunks
		return sparsify(k, observations, reportPartial);
	}

	ICModel sparsify(int k, ObservationsReader observations,
			boolean reportPartial) {

		Int2ObjectOpenHashMap<IntOpenHashSet> candidateParentsPerNode = new Int2ObjectOpenHashMap<IntOpenHashSet>();
		populateCandidateParents(candidateParentsPerNode);

		Int2ObjectOpenHashMap<ObjectOpenHashSet<Arc>> chosenArcsPerNode = new Int2ObjectOpenHashMap<ObjectOpenHashSet<Arc>>();

		getOutOfMinusInfinity(candidateParentsPerNode, chosenArcsPerNode);
		int numberInitialArcs = 0;
		for (int v : chosenArcsPerNode.keySet()) {
			numberInitialArcs += chosenArcsPerNode.get(v).size();
		}
		LOGGER.info("Number of initial arcs: " + numberInitialArcs);

		// The new probabilities
		SparseDoubleMatrix2D newProbs = Node.getSparseDoubleMatrix();
		int numInitializationArcs = 0;

		int numProbs = originalModel.getProbs().cardinality();
		PriorityQueue<ProbArcEntry> queue = new PriorityQueue<ProbArcEntry>(
				numProbs, getComparator());
		ProgressLogger pl = new ProgressLogger(LOGGER,
				ProgressLogger.TEN_SECONDS, "arcs");
		pl.start("NaiveSparsifier 1/3: adding initial " + numberInitialArcs
				+ " arcs");
		pl.expectedUpdates = numberInitialArcs;
		ObjectOpenHashSet<Arc> initializationArcs = new ObjectOpenHashSet<Arc>();
		for (int v : chosenArcsPerNode.keySet()) {
			for (Arc arc : chosenArcsPerNode.get(v)) {
				pl.update();
				initializationArcs.add(arc);

				// Compute likelihood
				double prob = originalModel.getProbability(arc.getLeaderId(),
						arc.getFollowerId());
				newProbs.setQuick(arc.getLeaderId(), arc.getFollowerId(), prob);
				numInitializationArcs++;
			}
		}
		if (reportPartial) {
			computeAndStorePartialLogLikelihood(numInitializationArcs, newProbs);
		}
		pl.stop();
		LOGGER.info("Inserted " + numInitializationArcs + " arcs");
		pl.start("NaiveSparsifier 2/3: inserting in priority queue rest of arcs");
		pl.expectedUpdates = numProbs;
		for (Arc arc : Utilities.getSortedNonZeroArcs(originalModel.getProbs())) {
			pl.update();
			if (!initializationArcs.contains(arc)) {
				double prob = originalModel.getProbability(arc.getLeaderId(),
						arc.getFollowerId());
				queue.add(new ProbArcEntry(prob, arc));
			}
		}
		pl.stop();
		initializationArcs.clear();

		int arcsToGo = k - numInitializationArcs + 1;
		if (arcsToGo > 0) {
			pl.expectedUpdates = k - numInitializationArcs + 1;
		} else {
			arcsToGo = 1; // patch
		}

		// Decide how often to report, by default every
		// sqrt(k-numInitializationArcs+1) points, unless that yields more
		// reporting points that we want to (given that each report takes a long
		// time).
		// Note that we do NOT report likelihood while getting out of minus
		// infinity
		int reportEvery = (int) Math.sqrt(arcsToGo);
		if ((double) arcsToGo / (double) reportEvery > (double) MAX_REPORT_POINTS) {
			reportEvery = (int) ((double) arcsToGo / (double) MAX_REPORT_POINTS);
		}
		if (reportEvery < 1) {
			reportEvery = 1;
		}

		pl.start("NaiveSparsifier 3/3: extracting from priority queue "
				+ arcsToGo + " arcs, report likelihood every " + reportEvery
				+ " arcs");
		int i;
		for (i = numInitializationArcs + 1; i <= k && !queue.isEmpty(); i++) {
			pl.update();
			ProbArcEntry entry = queue.poll();
			Arc arc = entry.getArc();
			double p = entry.getProb();
			newProbs.setQuick(arc.getLeaderId(), arc.getFollowerId(), p);
			if ((i % reportEvery) == 0 && reportPartial) {
				computeAndStorePartialLogLikelihood(i, newProbs);
				computeAndStorePartialFractionOfPropagations(observations, newProbs, i);
			}
		}
		pl.stop();
		if (reportPartial) {
			computeAndStorePartialLogLikelihood(i - 1, newProbs);
			computeAndStorePartialFractionOfPropagations(observations, newProbs, i - 1);
		}

		ICModel spg = new ICModel(originalModel.getSn(), newProbs);
		return spg;
	}

}
