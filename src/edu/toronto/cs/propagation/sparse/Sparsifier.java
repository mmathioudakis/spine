package edu.toronto.cs.propagation.sparse;

import gnu.trove.TIntDoubleHashMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.logging.ProgressLogger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import cern.colt.matrix.impl.SparseDoubleMatrix2D;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;

import edu.toronto.cs.propagation.ObservationsReader;
import edu.toronto.cs.propagation.SocialNetwork;
import edu.toronto.cs.propagation.ic.ICEstimateAuxiliary;
import edu.toronto.cs.propagation.ic.ICModel;
import edu.toronto.cs.propagation.ic.candidate_selection.CandidateSelectionPolicy;
import edu.toronto.cs.propagation.util.Arc;
import edu.toronto.cs.propagation.util.KeepMaximum;
import edu.toronto.cs.propagation.util.Node;
import edu.toronto.cs.propagation.util.Reflection;
import edu.toronto.cs.propagation.util.Utilities;

public abstract class Sparsifier {

	public enum Measure {
		LOG_L, FRACTION_OF_PROPAGATIONS;
	}

	static Logger LOGGER = Logger.getLogger(Sparsifier.class);
	static {
		BasicConfigurator.resetConfiguration();
		BasicConfigurator.configure();
	}

	public static Class<GreedySparsifier> DEFAULT_SPARSIFIER = GreedySparsifier.class;

	/**
	 * The original model being sparsified.
	 */
	ICModel originalModel;

	/**
	 * Contains auxiliary variables used in the computation.
	 */
	protected ICEstimateAuxiliary auxiliary;

	/**
	 * Stores measures attained at different steps of the sparsification process.
	 */
	public final Object2ObjectOpenHashMap<Measure, Int2DoubleOpenHashMap> partialResults;

	/**
	 * Indicates if the partial fraction of covered propagations should be computed.
	 */
	private boolean computeFractionOfPropagations = true;

	Object2ObjectOpenHashMap<Node, IntOpenHashSet> relatedActions = new Object2ObjectOpenHashMap<Node, IntOpenHashSet>();

	public Sparsifier(ICModel originalModel) {
		this.originalModel = originalModel;
		this.partialResults = new Object2ObjectOpenHashMap<Sparsifier.Measure, Int2DoubleOpenHashMap>();
	}

	public void computeAuxiliary(ObservationsReader observations, CandidateSelectionPolicy candidateSelectionPolicy) {
		if (this.auxiliary != null) {
			throw new IllegalStateException("Auxiliary was already set");
		}
		this.auxiliary = new ICEstimateAuxiliary(originalModel.getSn(), observations, candidateSelectionPolicy);
	}

	public void useAuxiliary(ICEstimateAuxiliary theAuxiliary) {
		if (this.auxiliary != null) {
			throw new IllegalStateException("Auxiliary was already set");
		} else if (theAuxiliary == null) {
			throw new IllegalArgumentException("Can't set to null");
		}
		this.auxiliary = theAuxiliary;
	}

	ICEstimateAuxiliary getAuxiliary() {
		return this.auxiliary;
	}

	public ICModel getOriginalModel() {
		return originalModel;
	}

	protected void storePartialResult(Measure m, int k, double logLikelihood) {
		if (!partialResults.containsKey(m)) {
			partialResults.put(m, new Int2DoubleOpenHashMap());
		}

		partialResults.get(m).put(k, logLikelihood);
	}

	/**
	 * Computes and stores the partial fraction of covered propagations.
	 * 
	 * Works only if {@link #computeFractionOfPropagations} is true, otherwise does nothing.
	 * 
	 * @param observations the observations
	 * @param newProbs the probabilities of the current model
	 * @param i a number indicating the number of edges so far
	 */
	protected void computeAndStorePartialFractionOfPropagations(ObservationsReader observations, SparseDoubleMatrix2D newProbs, int i) {
		if (computeFractionOfPropagations) {
			ICModel tempModel = new ICModel(originalModel.getSn(), newProbs);
			double sparsifiedFraction = tempModel.getTotalFraction(observations);
			storePartialResult(Measure.FRACTION_OF_PROPAGATIONS, i, sparsifiedFraction);
		}
	}

	/**
	 * Disables the computation of covered propagations.
	 */
	private void disableComputationOfPartialFractionOfPropagations() {
		computeFractionOfPropagations = false;
	}

	protected double computeAndStorePartialLogLikelihood(int k, SparseDoubleMatrix2D newProbs) {
		ICModel model = new ICModel(originalModel.getSn(), newProbs);
		double logLikelihood = model.getLogLikelihoodIgnoringParentInformation(auxiliary);
		storePartialResult(Measure.LOG_L, k, logLikelihood);
		return logLikelihood;
	}

	private PrintWriter debugFile;

	private void openDebugFile(String debugFilename) throws IOException {
		this.debugFile = new PrintWriter(new BufferedWriter(new FileWriter(new File(debugFilename))));
	}

	protected void debug(String str) {
		if (debugFile != null) {
			debugFile.println(str);
		}
	}

	private void closeDebugFile() {
		if (debugFile != null) {
			this.debugFile.close();
		}
	}

	public double blockLogLikelihood(int v, Int2ObjectOpenHashMap<IntArrayList> cPlusV, Int2ObjectOpenHashMap<IntArrayList> cMinusV, IntOpenHashSet selectedParents) {
		return auxiliary.blockLogLikelihood(originalModel, v, cPlusV, cMinusV, selectedParents);
	}

	public double blockLogLikelihood(int v, Int2ObjectOpenHashMap<IntArrayList> cPlusV, Int2ObjectOpenHashMap<IntArrayList> cMinusV,
			ObjectOpenHashSet<Arc> selectedParents) {
		return auxiliary.blockLogLikelihood(originalModel, v, cPlusV, cMinusV, selectedParents);
	}

	public double blockLogLikelihoodIncrease(int v, Int2ObjectOpenHashMap<IntArrayList> cPlusV, Int2ObjectOpenHashMap<IntArrayList> cMinusV,
			ObjectOpenHashSet<Arc> selectedParents, int extraParent) {
		return auxiliary.blockLogLikelihoodIncrease(originalModel, v, cPlusV, cMinusV, selectedParents, extraParent);
	}

	/*
	 * For each block, find a 'base' subset of parents for which the block's logLikelihood is not
	 * -Infinity
	 */
	/**
	 * For each block (child node), find a 'base' subset of parents for which the block's
	 * logLikelihood is not -Infinity
	 * 
	 * @param candidateParentsPerNode
	 * @param chosenArcsPerNode
	 * @return
	 */
	protected double getOutOfMinusInfinity(Int2ObjectOpenHashMap<IntOpenHashSet> candidateParentsPerNode, Int2ObjectOpenHashMap<ObjectOpenHashSet<Arc>> chosenArcsPerNode) {
		return getOutOfMinusInfinity(candidateParentsPerNode, chosenArcsPerNode, new IntOpenHashSet(candidateParentsPerNode.keySet()), null);
	}

	/**
	 * For a specific sub-set of blocks (child nodes), find a 'base' subset of parents for which the
	 * block's logLikelihood is not -Infinity
	 * 
	 * @param candidateParentsPerNode
	 * @param chosenArcsPerNode
	 * @param setOfBlocks
	 * @return
	 */
	protected double getOutOfMinusInfinity(Int2ObjectOpenHashMap<IntOpenHashSet> candidateParentsPerNode,
			Int2ObjectOpenHashMap<ObjectOpenHashSet<Arc>> chosenArcsPerNode, IntOpenHashSet setOfBlocks, TIntDoubleHashMap logLPerNode) {
		double totalLogL = 0;

		ProgressLogger pl = new ProgressLogger(LOGGER, ProgressLogger.TEN_SECONDS, "blocks");
		pl.start("Begin initializing, to avoid zero likelihood, using set-cover heuristic");
		pl.expectedUpdates = setOfBlocks.size();
		int nArcs = 0;
		for (int v : setOfBlocks) {
			pl.update();

			IntOpenHashSet vParents = candidateParentsPerNode.get(v);

			Int2ObjectOpenHashMap<IntOpenHashSet> parentActions = new Int2ObjectOpenHashMap<IntOpenHashSet>();

			Int2ObjectOpenHashMap<IntArrayList> cPlusV = auxiliary.getCplusOnline(v);
			Int2ObjectOpenHashMap<IntArrayList> cMinusV = auxiliary.getCminusOnline(v);

			if (cPlusV != null) {
				IntSet actions = cPlusV.keySet();
				// Heuristic: first add the parents that participate in A+ for
				// most actions
				for (int action : actions) {
					for (int u : cPlusV.get(action)) {
						if (!parentActions.containsKey(u)) {
							parentActions.put(u, new IntOpenHashSet());
						}
						parentActions.get(u).add(action);
					}
				}
			}

			KeepMaximum km = new KeepMaximum();
			km.addAllKey2Listsize(parentActions);

			IntOpenHashSet baseSetOfParents = new IntOpenHashSet();
			double logL = Double.NEGATIVE_INFINITY;
			while (logL == Double.NEGATIVE_INFINITY && (km.getMaximumKey() != -1)) {
				int u = km.getMaximumKey();
				if (baseSetOfParents.contains(u)) {
					throw new IllegalStateException("Attempted to add twice the same parent");
				}
				baseSetOfParents.add(u);
				logL = blockLogLikelihood(v, cPlusV, cMinusV, baseSetOfParents);
				IntOpenHashSet uActions = parentActions.get(u);
				for (int parent : vParents) {
					parentActions.get(parent).removeAll(uActions);
				}
				vParents.remove(u);
				parentActions.remove(u);
				km.reset();
				km.addAllKey2Listsize(parentActions);
			}

			// keep track of the likelihood
			totalLogL += logL;
			if (logLPerNode != null) {
				logLPerNode.put(v, logL);
			}

			chosenArcsPerNode.put(v, new ObjectOpenHashSet<Arc>());
			for (int u : baseSetOfParents) {
				nArcs++;
				chosenArcsPerNode.get(v).add(new Arc(u, v));
			}
		}
		pl.stop("Done initialization. Added " + nArcs + " arcs, logLikelihood=" + totalLogL);
		return totalLogL;
	}

	protected void populateCandidateParents(Int2ObjectOpenHashMap<IntOpenHashSet> candidateParentsPerNode) {
		if (auxiliary == null) {
			throw new IllegalStateException("Auxiliary is null");
		}
		populateCandidateParents(candidateParentsPerNode, new IntOpenHashSet(auxiliary.getNodeActions().keySet()));
	}

	protected void populateCandidateParents(Int2ObjectOpenHashMap<IntOpenHashSet> candidateParentsPerNode, IntOpenHashSet chunk) {
		for (int v : chunk) {
			IntOpenHashSet candidateParents = new IntOpenHashSet();
			Int2ObjectOpenHashMap<IntArrayList> cPlusV = auxiliary.getCplusOnline(v);
			if (cPlusV != null) {
				for (int action : cPlusV.keySet()) {
					for (int u : cPlusV.get(action)) {
						candidateParents.add(u);
					}
				}
			}
			candidateParentsPerNode.put(v, candidateParents);
		}
	}

	/**
	 * Creates a sparse model with up to k arcs with non-zero probability
	 * 
	 * @param k the number of arcs to maintain
	 * @param numOfChunks the number of chunks to parallelize sparsification
	 * @return a model with up to k arcs with non-zero probability
	 */
	abstract ICModel sparsify(int k, int numOfChunks, ObservationsReader observations, boolean reportPartial);

	public static void main(String[] args) throws Exception {

		final SimpleJSAP jsap = new SimpleJSAP(Sparsifier.class.getName(), "Estimates and sparsifies a propagation model from a set of observations.",
				new Parameter[] {
						new FlaggedOption("social-network", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 's', "social-network",
								"The file containing the social network graph"),
						new FlaggedOption("probabilities", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'p', "probabilities",
								"The file containing the propagation probabilities"),
						new FlaggedOption("candidate-selection-policy", JSAP.STRING_PARSER, CandidateSelectionPolicy.DEFAULT_CANDIDATE_SELECTION_POLICY.getClass()
								.getSimpleName(), JSAP.REQUIRED, 'c', "candidate-selection-policy", "The name of the candidate selection policy"),
						new FlaggedOption("auxiliary-basename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NO_SHORTFLAG, "auxiliary-basename",
								"The base name for reading a pre-computed auxiliary structure"),
						new FlaggedOption("input", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'i', "input", "The file containing the observations"),
						new FlaggedOption("sparsifier", JSAP.STRING_PARSER, DEFAULT_SPARSIFIER.getSimpleName(), JSAP.NOT_REQUIRED, 'f', "sparsifier",
								"The sparsifier to run, from this list: " + StringUtils.join(Reflection.subClasses(Sparsifier.class), ',')),
						new FlaggedOption("sparse-model-size", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'k', "sparse-model-size",
								"The size of the sparse model"),
						new FlaggedOption("number-of-chunks", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'r', "number-of-chunks",
								"The number of chunks to be sparsified in parralel"),
						new FlaggedOption("output", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "output", "File to dump sparsified model to"),
						new FlaggedOption("measures-file", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'z', "measures-file",
								"Save measures of partial models to file"),
						new FlaggedOption("debug-file", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'd', "debug-file", "Save debug information to file"),
						new Switch("with-fraction", 'n', "with-fraction", "Disable the computation of the 'fraction of covered propagations'."),
						new Switch("incremental-likelihood", JSAP.NO_SHORTFLAG, "incremental-likelihood",
								"Performs incremental computation of likelihood, for sparsifications methods that support this option (faster, experimental)."),

				});

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) {
			return;
		}

		// Load social network and input
		String snFilename = jsapResult.getString("social-network");
		SocialNetwork socNet = new SocialNetwork(Utilities.getIterator(snFilename));
		String obsFilename = jsapResult.getString("input");
		ObservationsReader observations = new ObservationsReader(obsFilename);

		// Load original model
		ICModel originalModel = new ICModel(socNet, Utilities.getIterator(jsapResult.getString("probabilities")));

		// Load candidate selection policy
		String selectionPolicyName = jsapResult.getString("candidate-selection-policy");
		Class<?> selectionPolicyClass = Class.forName(CandidateSelectionPolicy.class.getPackage().getName() + "." + selectionPolicyName);
		CandidateSelectionPolicy candidateSelectionPolicy = (CandidateSelectionPolicy) selectionPolicyClass.getConstructor(new Class[] {}).newInstance(new Object[] {});

		// Create sparsifier
		String sparsifierName = jsapResult.getString("sparsifier");
		Class<?> sparsifierClass = Class.forName(Sparsifier.class.getPackage().getName() + "." + sparsifierName);
		Sparsifier sparsifier = (Sparsifier) sparsifierClass.getConstructor(new Class[] { originalModel.getClass() }).newInstance(new Object[] { originalModel });
		LOGGER.info("Created a " + sparsifier.getClass().getSimpleName());

		// Set sparsifier options
		if (!jsapResult.getBoolean("with-fraction")) {
			sparsifier.disableComputationOfPartialFractionOfPropagations();
			LOGGER.info("Disabled the computation of fraction of propagations");
		}
		if (jsapResult.getBoolean("incremental-likelihood") ) {
			if( sparsifier instanceof GreedySparsifier ) {
				((GreedySparsifier)sparsifier).setIncrementalLikelihoodComputation();
				LOGGER.info("Enabled incremental computation of likelihood (faster, experimental)");
			} else {
				LOGGER.warn("This type of sparsifier does not accept the --incrementa-likelihood switch, ignoring");
			}
		}

		if (jsapResult.userSpecified("auxiliary-basename")) {
			// Use existing auxiliary file
			String auxiliaryBasename = jsapResult.getString("auxiliary-basename");
			ICEstimateAuxiliary auxiliary = new ICEstimateAuxiliary(socNet, observations, null);
			LOGGER.info("Loading pre-computed auxiliary variables");
			auxiliary.read(auxiliaryBasename);
			sparsifier.useAuxiliary(auxiliary);
			if (auxiliary.getCandidateSelectionPolicy().toSpec().equals(candidateSelectionPolicy.toSpec())) {
				LOGGER.info("Candidate selection policy: " + auxiliary.getCandidateSelectionPolicy().toSpec());
			} else {
				throw new IllegalArgumentException("The candidate selection policies do not match: auxiliary has '" + auxiliary.getCandidateSelectionPolicy().toSpec()
						+ "', sparsifier has '" + candidateSelectionPolicy.toSpec() + "'");
			}
		} else {
			// Compute auxiliary variables
			LOGGER.info("Computing auxiliary variables");
			sparsifier.computeAuxiliary(observations, candidateSelectionPolicy);
		}

		int maxSparseSize;
		if (jsapResult.userSpecified("sparse-model-size")) {
			maxSparseSize = jsapResult.getInt("sparse-model-size");
		} else {
			maxSparseSize = originalModel.getProbs().cardinality();
			LOGGER.info("Setting target number of arcs to number of arcs with non-zero probability in the original model");
		}

		// Open debug file
		if (jsapResult.userSpecified("debug-file")) {
			String debugFilename = jsapResult.getString("debug-file");
			LOGGER.info("Will write debug output to " + debugFilename);
			sparsifier.openDebugFile(debugFilename);
		}

		int numOfChunks = 1;
		if (jsapResult.userSpecified("number-of-chunks")) {
			numOfChunks = jsapResult.getInt("number-of-chunks");
		}

		ICModel sparseModel = runSparsifier(socNet, observations, originalModel, maxSparseSize, sparsifier, numOfChunks, true);

		// Write partial results to file if necessary
		if (jsapResult.userSpecified("measures-file")) {
			for (Measure m : sparsifier.partialResults.keySet()) {
				String logFilename = jsapResult.getString("measures-file");
				switch (m) {
				case LOG_L:
					logFilename = logFilename + ".logL";
					break;
				case FRACTION_OF_PROPAGATIONS:
					logFilename = logFilename + ".frac";
					break;
				default:
					break;
				}
				PrintWriter report = new PrintWriter(new BufferedWriter(new FileWriter(new File(logFilename))));
				LOGGER.info("Writing partial " + m.toString() + " results to " + logFilename);
				report.println("#k\t" + m.toString());
				int[] ks = sparsifier.partialResults.get(m).keySet().toArray(new int[] {});
				Arrays.sort(ks);
				for (int k : ks) {
					report.println(k + "\t" + sparsifier.partialResults.get(m).get(k));
				}
				report.close();
			}
		}

		// Dump probabilities
		if (jsapResult.userSpecified("output")) {
			String probsFilename = jsapResult.getString("output");
			PrintWriter pw = Utilities.getPW(probsFilename);
			LOGGER.info("Dumping probabilities to " + probsFilename);
			sparseModel.dumpProbabilities(pw);
			pw.close();
		}

		sparsifier.closeDebugFile();
	}

	public static ICModel runSparsifier(SocialNetwork socNet, ObservationsReader observations, ICModel originalModel, int sparseSize, Sparsifier sparse, int numOfChunks,
			boolean reportPartial) {
		LOGGER.info("Arcs: total in social network=" + socNet.getArcs().size() + ", with non-zero probability=" + originalModel.getProbs().cardinality() + ", target k="
				+ sparseSize);

		LOGGER.info("Begin creating sparsified model");
		ICModel sparseModel = sparse.sparsify(sparseSize, numOfChunks, observations, reportPartial);
		LOGGER.info("Done creating sparsified model");

		LOGGER.info("Begin measuring the resulting sparsified model");

		// Report change in log likelihood
		double sparsifiedLogL = sparseModel.getLogLikelihoodIgnoringParentInformation(sparse.getAuxiliary());
		LOGGER.info("SPARSIFIED log likelihood (ignoring parent information)=" + sparsifiedLogL);
		double originalLogL = originalModel.getLogLikelihoodIgnoringParentInformation(sparse.getAuxiliary());
		LOGGER.info("ORIGINAL log likelihood (ignoring parent information)=" + originalLogL);

		LOGGER.info("End measuring the resulting sparsified model");
		return sparseModel;
	}
}
