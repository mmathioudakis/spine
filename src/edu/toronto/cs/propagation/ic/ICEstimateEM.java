package edu.toronto.cs.propagation.ic;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.logging.ProgressLogger;

import org.apache.log4j.Logger;

import cern.colt.function.IntIntDoubleFunction;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import cern.colt.matrix.impl.SparseObjectMatrix2D;
import edu.toronto.cs.propagation.SocialNetwork;
import edu.toronto.cs.propagation.ic.candidate_selection.CandidateSelectionPolicy;
import edu.toronto.cs.propagation.ic.candidate_selection.SelectByTimePrecedence;
import edu.toronto.cs.propagation.util.Arc;
import edu.toronto.cs.propagation.util.Int2ArrayOfIntMap;
import edu.toronto.cs.propagation.util.Iterative;
import edu.toronto.cs.propagation.util.Node;
import edu.toronto.cs.propagation.util.SparseIntArrayListMatrix2D;
import edu.toronto.cs.propagation.util.Utilities;

/**
 * An estimator that assumes you can not observe which was the parent that
 * activated each node, but just the social network and timestamps
 * 
 */
public class ICEstimateEM extends ICEstimate implements Iterative {

	public final static double INITIAL_PROBABILITY = 1.0;

	private static double pAlpha(int v, int[] BactionV,
			SparseDoubleMatrix2D currentProbs) {
		double aux = 1.0;
		for (int u : BactionV) {
			aux *= (1.0 - currentProbs.getQuick(u,v));
		}
		return 1.0 - aux;
	}

	int nActions;

	private int maxIterations = DEFAULT_MAX_ITERATIONS;

	private double minDifference = DEFAULT_MIN_DIFFERENCE;

	private SparseIntArrayListMatrix2D Aplus;

	private SparseIntArrayListMatrix2D Aminus;

	private ObjectArrayList<Int2ArrayOfIntMap> Bplus;

	public ICEstimateEM(SocialNetwork sn) {
		super(sn);
		setMaxIterations(DEFAULT_MAX_ITERATIONS);
		setMinDifference(DEFAULT_MIN_DIFFERENCE);
	}

	@Override
	public CandidateSelectionPolicy getCandidateSelectionPolicy() {
		return new SelectByTimePrecedence();
	}

	@Override
	public
	ICModel estimate(int numOfChunks) {
		return estimate(null, numOfChunks);
	}

	@Override
	public ICModel estimate(Logger logger, int numOfChunks) {

		// Initialize
		nActions = auxiliary.getnActions();

		// Initialize sets Aplus and Aminus
		Aplus = auxiliary.getAplus();
		Aminus = auxiliary.getAminus();
		Bplus = auxiliary.getBplus();
		
		// Compute probabilities
		SparseDoubleMatrix2D probs;
		probs = iterate(logger, numOfChunks);

		return new ICModelConstantWaitingTime(sn, probs);
	}

	public SparseObjectMatrix2D getAminus() {
		return Aminus;
	}

	public SparseObjectMatrix2D getAplus() {
		return Aplus;
	}

	SparseDoubleMatrix2D iterate(Logger logger) {
		return iterate(logger, 1);
	}

	SparseDoubleMatrix2D iterate(Logger logger, int numOfChunks) {

		int[] allNodes = Utilities.getRandomArrayOfNodes(sn.getNodes().toArray(new Node[] {}));
		ObjectOpenHashSet<IntOpenHashSet> nodeChunks = Utilities
				.partitionIntoChunks(allNodes, numOfChunks);

		// Initialize probabilities
		ObjectOpenHashSet<SparseDoubleMatrix2D> currentProbsInChunks = new ObjectOpenHashSet<SparseDoubleMatrix2D>();
		for (IntOpenHashSet chunk : nodeChunks) {
			SparseDoubleMatrix2D currentProbsOfChunk = Node.getSparseDoubleMatrix();
			for (int u : chunk) {
				for (Arc inlink : sn.getLeaders(u)) {
					int leaderId = inlink.getLeaderId();
					int followerId = inlink.getFollowerId();
					int aPlusSize = (Aplus.getQuick(leaderId, followerId) != null) ? Aplus.getQuick(leaderId, followerId).size() : 0;
					// ignore arcs that would get zero probability
					if (aPlusSize > 0) {
						currentProbsOfChunk.setQuick(leaderId, followerId, INITIAL_PROBABILITY);
					}
				}
			}
			currentProbsInChunks.add(currentProbsOfChunk);
		}

		nodeChunks.clear();

		ProgressLogger pl = null;
		if (logger != null) {
			pl = new ProgressLogger(logger, ProgressLogger.ONE_SECOND,
					"iterations");
			pl.expectedUpdates = maxIterations;
			pl.start("Iterating EM method");
		}

		for (SparseDoubleMatrix2D currentProbsOfChunk : currentProbsInChunks) {

			// Iterate up to maxIterations, -1 means infinity
			for (int iteration = 0; (iteration < maxIterations || maxIterations == -1); iteration++) {
				SparseDoubleMatrix2D newProbs = Node.getSparseDoubleMatrix();

				for (Arc arc : Utilities.getSortedNonZeroArcs(currentProbsOfChunk)) {
					int leaderId = arc.getLeaderId();
					int followerId = arc.getFollowerId();

					int aPlusSize = Aplus.getListSize(leaderId,followerId);
					int aMinusSize = Aminus.getListSize(leaderId,followerId);

					if (aPlusSize == 0) {
						newProbs.setQuick(leaderId, followerId, 0.0);
					} else {
						double sumFactor = 0.0;
						for (int action : Aplus.getQuick(leaderId,followerId)) {
							sumFactor += 1.0 / pAlpha(followerId,
									Bplus.get(action).get(followerId),
									currentProbsOfChunk);
						}

						double prob = currentProbsOfChunk.getQuick(leaderId, followerId)
								/ (double) (aPlusSize + aMinusSize);
						prob *= sumFactor;

						newProbs.setQuick(leaderId, followerId, prob);
					}
				}

				if (logger != null) {
					pl.update();
				}
				if ((minDifference > 0)
						&& (Utilities.l2sq(currentProbsOfChunk, newProbs) < minDifference)) {
					if (logger != null) {
						logger.info("Difference is "
								+ Utilities.l2sq(currentProbsOfChunk, newProbs) + " < "
								+ minDifference);
					}
					break;
				}
				Utilities.setToZero(currentProbsOfChunk);
				currentProbsOfChunk.assign(newProbs);
			}
		}

		// Join all partial matrices
		final SparseDoubleMatrix2D probEstimates = Node.getSparseDoubleMatrix();
		for (SparseDoubleMatrix2D probsOfChunk : currentProbsInChunks) {
			probsOfChunk.forEachNonZero(new IntIntDoubleFunction() {
				public double apply(int leader, int follower, double value) {
					probEstimates.setQuick(leader, follower, value);
					return value;
				}});
		}

		if (logger != null) {
			pl.stop();
		}

		return probEstimates;
	}

	public void setMaxIterations(int maxIterations) {
		this.maxIterations = maxIterations;
	}

	public void setMinDifference(double minDifference) {
		this.minDifference = minDifference;
	}
}
