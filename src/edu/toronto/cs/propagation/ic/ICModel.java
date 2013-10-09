package edu.toronto.cs.propagation.ic;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.logging.ProgressLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import cern.colt.matrix.impl.SparseDoubleMatrix2D;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;

import edu.toronto.cs.propagation.ObservationsReader;
import edu.toronto.cs.propagation.PropagationHistory;
import edu.toronto.cs.propagation.PropagationModel;
import edu.toronto.cs.propagation.SocialNetwork;
import edu.toronto.cs.propagation.ic.candidate_selection.CandidateSelectionPolicy;
import edu.toronto.cs.propagation.util.Arc;
import edu.toronto.cs.propagation.util.ArcWithTimestamp;
import edu.toronto.cs.propagation.util.Node;
import edu.toronto.cs.propagation.util.Utilities;

/**
 * Represents an Independent Cascade Model.
 */
public class ICModel extends PropagationModel {

	static Logger LOGGER = Logger.getLogger(ICModel.class);
	static {
		BasicConfigurator.resetConfiguration();
		BasicConfigurator.configure();
	}

	public static Class<ICModel> DEFAULT_MODEL = ICModel.class;

	/**
	 * The influence probabilities.
	 */
	SparseDoubleMatrix2D probs;

	/**
	 * Creates a model from a social network and a line iterator over a file
	 * with influence probabilities.
	 * 
	 * @param sn
	 *            the social network
	 * @param it
	 *            an iterator over a list of tab-separated probabilities.
	 */
	public ICModel(SocialNetwork sn, LineIterator it) {
		super(sn);

		// Read probabilities from iterator
		probs = Node.getSparseDoubleMatrix();
		while (it.hasNext()) {
			String str = it.next().toString();
			if (str.startsWith("#")) {
				continue;
			}
			String[] tokens = str.split("\t");
			if (tokens[0].equalsIgnoreCase("defaultProbability")) {
				if (Double.parseDouble(tokens[1]) > 0.0) {
					throw new IllegalArgumentException(
							"Deprecated: default probability must be zero");
				}
			} else {
				Node src = new Node(tokens[0]);
				Node dest = new Node(tokens[1]);
				double probability = Double.parseDouble(tokens[2]);
				probs.setQuick(src.getId(), dest.getId(), probability);
			}
		}
	}

	/**
	 * Creates a model from a social network and a matrix with influence
	 * probabilities.
	 * 
	 * @param sn
	 *            the social network.
	 * @param probs
	 *            the matrix containing influence probabilities.
	 */
	public ICModel(SocialNetwork sn, SparseDoubleMatrix2D probs) {
		super(sn);
		this.probs = probs;
	}

	@Override
	public PropagationHistory runModel(Node startNode) {
		if (!sn.containsNode(startNode)) {
			throw new IllegalArgumentException(
					"Start node does not belong to the social network");
		}
		ObjectArrayList<ArcWithTimestamp> propagations = new ObjectArrayList<ArcWithTimestamp>();

		int time = 0;
		Int2ObjectOpenHashMap<IntOpenHashSet> activated = new Int2ObjectOpenHashMap<IntOpenHashSet>();
		IntOpenHashSet everActivated = new IntOpenHashSet();

		IntOpenHashSet initiallyActivated = new IntOpenHashSet();
		initiallyActivated.add(startNode.getId());
		everActivated.add(startNode.getId());
		propagations.add(new ArcWithTimestamp(null, startNode, time));

		activated.put(time, initiallyActivated);

		while (activated.get(time).size() > 0) {
			time++;

			IntOpenHashSet oldActivations = activated.get(time - 1);
			IntOpenHashSet newActivations = new IntOpenHashSet();

			for (int parentId : oldActivations) {
				for (Arc childArc : sn.getFollowers(parentId)) {
					double prob = getProbability(childArc);
					if (prob <= 0) {
						continue;
					}

					int childId = childArc.getFollowerId();
					if (!everActivated.contains(childId)) {
						if (Utilities.coinFlip(prob)) {
							newActivations.add(childId);
							everActivated.add(childId);
							propagations.add(new ArcWithTimestamp(parentId,
									childId, time));
						}
					}
				}
			}
			activated.put(time, newActivations);
		}

		return new PropagationHistory(propagations);
	}

	@Override
	public PropagationHistory runModel(Set<Node> allStartNodes) {

		for (Node startNode : allStartNodes) {
			if (!sn.containsNode(startNode)) {
				throw new IllegalArgumentException("Start node "
						+ startNode.toString()
						+ " does not belong to the social network");
			}
		}
		ObjectArrayList<ArcWithTimestamp> propagations = new ObjectArrayList<ArcWithTimestamp>();

		int time = 0;
		Int2ObjectOpenHashMap<IntOpenHashSet> activated = new Int2ObjectOpenHashMap<IntOpenHashSet>();
		IntOpenHashSet everActivated = new IntOpenHashSet();

		IntOpenHashSet initiallyActivated = new IntOpenHashSet();
		for (Node startNode : allStartNodes) {
			initiallyActivated.add(startNode.getId());
			everActivated.add(startNode.getId());
			propagations.add(new ArcWithTimestamp(null, startNode, time));
		}

		activated.put(time, initiallyActivated);

		while (activated.get(time).size() > 0) {
			time++;

			IntOpenHashSet oldActivations = activated.get(time - 1);
			IntOpenHashSet newActivations = new IntOpenHashSet();

			for (int parentId : oldActivations) {

				for (Arc childArc : sn.getFollowers(parentId)) {
					double prob = getProbability(childArc);
					if (prob <= 0) {
						continue;
					}

					int childId = childArc.getFollowerId();
					if (!everActivated.contains(childId)) {
						if (Utilities.coinFlip(getProbability(childArc))) {
							newActivations.add(childId);
							everActivated.add(childId);
							propagations.add(new ArcWithTimestamp(parentId,
									childId, time));
						}
					}
				}
			}
			activated.put(time, newActivations);
		}

		return new PropagationHistory(propagations);
	}

	/**
	 * Computes log likelihood for a node across all actions.
	 * 
	 * @param v
	 *            the node
	 * @param vPlus
	 *            the parents of the node that could have activated it
	 * @param vMinus
	 *            the parents of the node that certainly did not active it
	 * @param selectedParents
	 *            a sub-set of (vPlus union vMinus) to consider in the
	 *            computation
	 * @return
	 */
	public double blockLogLikelihoodUsingCplusCminus(int v,
			Int2ObjectOpenHashMap<IntArrayList> vPlus,
			Int2ObjectOpenHashMap<IntArrayList> vMinus,
			IntOpenHashSet selectedParents) {

		double blockLogL = 0.0;

		if (vPlus != null) {
			for (int action : vPlus.keySet()) {
				double probA = 1.0;
				for (int u : vPlus.get(action)) {
					if (selectedParents.contains(u)) {
						double p = probs.getQuick(u, v);
						probA *= (1.0 - p);
					}
				}
				probA = 1.0 - probA;
				double logA = Math.log(probA);
				blockLogL += logA;
			}
		}

		if (vMinus != null) {
			for (int action : vMinus.keySet()) {
				double logA = 0.0;
				for (int u : vMinus.get(action)) {
					if (selectedParents.contains(u)) {
						double p = probs.getQuick(u, v);
						logA += Math.log(1.0 - p);
					}
				}
				blockLogL += logA;
			}
		}
		return blockLogL;
	}

	/**
	 * Computes log likelihood for a node across all actions.
	 * 
	 * Same as
	 * {@link #blockLogLikelihoodUsingCplusCminus(int, Int2ObjectOpenHashMap, Int2ObjectOpenHashMap, IntOpenHashSet)}
	 * but using arcs.
	 * 
	 * @param v
	 *            the node
	 * @param vPlus
	 *            the parents of the node that could have activated it
	 * @param vMinus
	 *            the parents of the node that certainly did not active it
	 * @param selectedParents
	 *            a sub-set of (vPlus union vMinus) expressed as arcs to
	 *            consider in the computation
	 * @return
	 */
	public double blockLogLikelihoodUsingCplusCminus(int v,
			Int2ObjectOpenHashMap<IntArrayList> vPlus,
			Int2ObjectOpenHashMap<IntArrayList> vMinus,
			ObjectOpenHashSet<Arc> selectedParents) {

		double blockLogL = 0.0;

		if (vPlus != null) {
			for (int action : vPlus.keySet()) {
				double probA = 1.0;
				for (int u : vPlus.get(action)) {
					Arc arc = new Arc(u, v);
					if (selectedParents.contains(arc)) {
						double p = probs.getQuick(u, v);
						probA *= (1.0 - p);
					}
				}
				probA = 1.0 - probA;
				double logA = Math.log(probA);
				blockLogL += logA;
			}
		}
		if (vMinus != null) {
			for (int action : vMinus.keySet()) {
				double logA = 0.0;
				for (int u : vMinus.get(action)) {
					Arc arc = new Arc(u, v);
					if (selectedParents.contains(arc)) {
						double p = probs.getQuick(u, v);
						logA += Math.log(1.0 - p);
					}
				}
				blockLogL += logA;
			}
		}

		return blockLogL;
	}

	public double blockLogLikelihoodIncreaseUsingCplusCminus(int v,
			Int2ObjectOpenHashMap<IntArrayList> vPlus,
			Int2ObjectOpenHashMap<IntArrayList> vMinus,
			ObjectOpenHashSet<Arc> alreadySelectedParents, int extraParentId) {

		double increase = 0.0;

		if (vPlus != null) {
			for (int action : vPlus.keySet()) {
				if (vPlus.get(action).contains(extraParentId)) {
					double origProb = 1.0;
					for (int u : vPlus.get(action)) {
						Arc arc = new Arc(u, v);
						if (alreadySelectedParents.contains(arc)) {
							double p = probs.getQuick(u, v);
							origProb *= (1.0 - p);
						}
					}
					double xp = probs.getQuick(extraParentId, v);
					double updProb = origProb * (1.0 - xp);
					
					origProb = 1.0 - origProb;
					updProb = 1.0 - updProb;
					
					double origLog = Math.log(origProb);
					double updLog = Math.log(updProb);
					increase += (updLog - origLog);
				}
			}
		}
		if (vMinus != null) {
			for (int action : vMinus.keySet()) {
				if (vMinus.get(action).contains(extraParentId)) {
					double xp = probs.getQuick(extraParentId, v);
					increase += Math.log(1 - xp);
				}
			}
		}

		return increase;
	}

	/**
	 * Computes log likelihood for a node across all actions.
	 * 
	 * Same as
	 * {@link #blockLogLikelihoodUsingCplusCminus(int, Int2ObjectOpenHashMap, Int2ObjectOpenHashMap, IntOpenHashSet)}
	 * but considering that all parents with non-zero influence on this node are
	 * the selected parents.
	 * 
	 * @param v
	 *            the node
	 * @param auxiliary
	 *            an auxiliary data structure
	 * @return
	 */
	double computeBlockLogLikelihood(int v, ICEstimateAuxiliary auxiliary) {

		Int2ObjectOpenHashMap<IntArrayList> vPlus = auxiliary.getCplusOnline(v);
		Int2ObjectOpenHashMap<IntArrayList> vMinus = auxiliary
				.getCminusOnline(v);

		IntOpenHashSet candidateParents = new IntOpenHashSet();

		// Add as candidateParents all parents in vPlus
		if (vPlus != null) {
			for (int action : vPlus.keySet()) {
				for (int u : vPlus.get(action)) {
					candidateParents.add(u);
				}
			}
		}

		// Add as candidateParents all parents in vMinus
		if (vMinus != null) {
			for (int action : vMinus.keySet()) {
				for (int u : vMinus.get(action)) {
					candidateParents.add(u);
				}
			}
		}

		// Add as selectedParents all candidateParents that have non-zero
		// influence probability
		IntOpenHashSet selectedParents = new IntOpenHashSet();
		for (int u : candidateParents) {
			if (probs.getQuick(u, v) > 0.0) {
				selectedParents.add(u);
			}
		}

		return blockLogLikelihoodUsingCplusCminus(v, vPlus, vMinus,
				selectedParents);
	}

	public void dump() {
		System.out.println("#Social network");
		sn.dump();
		System.out.println();
		dumpProbabilities();
	}

	@SuppressWarnings("boxing")
	public void dumpComparisonWithAlternative(ICModel alternativeModel,
			boolean printModel) {

		System.out.println("#Comparison of probabilities"
				+ (printModel ? " (original, alternative)" : ""));

		double l2sq = 0.0;
		for (Arc arc : Utilities.getSortedNonZeroArcs(probs)) { // sorted by
																// arcs
			double actual = probs.getQuick(arc.getLeaderId(),
					arc.getFollowerId());
			double estimated = alternativeModel.getProbability(arc);
			l2sq += Math.pow(actual - estimated, 2.0);
			if (printModel) {
				System.out.format("%s\t%s\t%.2g\t%.2g\n", arc.getLeaderName(),
						arc.getFollowerName(), actual, estimated);
			}
		}
		if (!printModel) {
			System.out.println("#Model omitted, |E| = " + probs.cardinality());
		}
		System.out.println("#L2 Squared over |E| = " + l2sq
				/ probs.cardinality());
	}

	/**
	 * @deprecated
	 * @return 0
	 */
	double getDefaultProbability() {
		return 0.0;
	}

	/**
	 * Gets the log likelihood of a certain set of observations, considering the
	 * information in the observations about the single parent that activated
	 * each of the nodes.
	 * 
	 * @param observations
	 *            a set of observation
	 * @return the log likelihood of that set
	 */
	double getLogLikelihoodConsideringParentInformation(
			ObjectArrayList<PropagationHistory> observations) {
		throw new IllegalStateException("Not implemented");
	}

	/**
	 * Gets the log likelihood of a certain set of observations, ignoring the
	 * information in the observations about the single parent that activated
	 * each of the nodes. In other words, considering that any active parent may
	 * have activated a node.
	 * 
	 * @param observations
	 *            a set of observation
	 * @return the log likelihood of that set
	 */
	public double getLogLikelihoodIgnoringParentInformation(
			ObservationsReader observations) {
		return getLogLikelihoodIgnoringParentInformation(new ICEstimateAuxiliary(
				getSn(), observations,
				CandidateSelectionPolicy.DEFAULT_CANDIDATE_SELECTION_POLICY));
	}

	public static double computeLogLikelihoodIgnoringParentInformation(
			SocialNetwork sn, LineIterator probsIter,
			ObservationsReader observations) {
		ICModel model = new ICModel(sn, probsIter);
		double logLikelihood = model
				.getLogLikelihoodIgnoringParentInformation(observations);
		return logLikelihood;
	}

	/**
	 * Gets the log likelihood of a certain set of observations, ignoring the
	 * information in the observations about the single parent that activated
	 * each of the nodes. In other words, considering that any active parent may
	 * have activated a node.
	 * 
	 * @param auxiliary
	 *            a set of auxiliary variables
	 * @return the log likelihood of the observations from which the set of
	 *         auxiliary variables was created
	 */
	public double getLogLikelihoodIgnoringParentInformation(
			ICEstimateAuxiliary auxiliary) {
		double logL = 0;
		Int2ObjectOpenHashMap<IntOpenHashSet> nodeActions = auxiliary
				.getNodeActions();
		IntSet nodesInvolved = nodeActions.keySet();
		for (int v : nodesInvolved) {
			double blockLogL = computeBlockLogLikelihood(v, auxiliary);
			logL += blockLogL;
		}
		return logL;
	}

	public double getTotalFraction(ObservationsReader observations) {
		double propBFS = 0.0;
		int totalSum = 0;
		Iterator<PropagationHistory> iterator = observations.iterator();
		while (iterator.hasNext()) {
			PropagationHistory propagation = iterator.next();
			propBFS += getTotalFraction(propagation);
			int sizeOfPropagation = propagation.size();
			totalSum += sizeOfPropagation;
		}
		return propBFS / (double) (totalSum);
	}

	double getTotalFraction(PropagationHistory propagation) {
		ObjectArrayList<ArcWithTimestamp> events = propagation.getEvents();
		if (events.get(0).getLeaderId() != Node.getNullId()) {
			throw new IllegalArgumentException(
					"The leader of the first event must be null");
		}

		int numOfValidActivations = 0;

		for (int eventNum = 1; eventNum < events.size(); eventNum++) {
			ArcWithTimestamp arc = events.get(eventNum);
			if (probs.getQuick(arc.getLeaderId(), arc.getFollowerId()) > 0.0) {
				numOfValidActivations++;
			}
		}
		return numOfValidActivations;
	}

	/**
	 * @deprecated
	 */
	public double getFractionBFS(ObservationsReader observations) {
		double propBFS = 0.0;
		int totalSum = 0;
		Iterator<PropagationHistory> iterator = observations.iterator();
		while (iterator.hasNext()) {
			PropagationHistory propagation = iterator.next();
			propBFS += getFractionBFS(propagation);
			int sizeOfPropagation = propagation.size();
			totalSum += sizeOfPropagation;
		}
		return propBFS / (double) (totalSum);
	}

	double getFractionBFS(PropagationHistory propagation) {
		ObjectArrayList<ArcWithTimestamp> events = propagation.getEvents();
		if (events.get(0).getLeaderId() != Node.getNullId()) {
			throw new IllegalArgumentException(
					"The leader of the first event must be null");
		}

		IntOpenHashSet bfsNodes = new IntOpenHashSet();
		int omega = events.get(0).getFollowerId();
		bfsNodes.add(omega);

		for (int eventNum = 1; eventNum < events.size(); eventNum++) {
			ArcWithTimestamp arc = events.get(eventNum);
			if (probs.getQuick(arc.getLeaderId(), arc.getFollowerId()) > 0.0
					&& bfsNodes.contains(arc.getLeaderId())) {
				bfsNodes.add(arc.getFollowerId());
			}
		}
		return bfsNodes.size();
	}

	/**
	 * Gets the weighted fraction of propagations that are uninterrupted. An
	 * "uninterrupted" propagation is one where every arc has non-zero
	 * probability.
	 * 
	 * @param observations
	 *            the list of observation
	 * @return the sumation of
	 *         {@link #getFractionUninterrupted(PropagationHistory)} for each
	 *         action, divided by the number of actions
	 * @deprecated
	 */
	public double getFractionUninterruptedPropagations(
			ObservationsReader observations) {
		double sumFractions = 0.0;
		Iterator<PropagationHistory> iterator = observations.iterator();
		while (iterator.hasNext()) {
			PropagationHistory propagation = iterator.next();
			sumFractions += getFractionUninterrupted(propagation);
		}
		return sumFractions / (double) (observations.size());
	}

	/**
	 * Gets the fraction of this propagation that was uninterrupted. An
	 * "uninterrupted" propagation up to node k is one where the first k nodes
	 * can be reached by a path of non-zero probabilities.
	 * 
	 * @param propagation
	 *            a single propagation.
	 * @return the number of nodes that can be reached by a path of non-zero
	 *         probabilities, divided by the total number of nodes affected.
	 */
	double getFractionUninterrupted(PropagationHistory propagation) {
		if (propagation == null) {
			throw new IllegalArgumentException("Propagation is null");
		} else if (propagation.size() == 0) {
			throw new IllegalArgumentException("Propagation has no events");
		}

		ObjectArrayList<ArcWithTimestamp> events = propagation.getEvents();
		if (events.get(0).getLeaderId() != Node.getNullId()) {
			throw new IllegalArgumentException(
					"The leader of the first event must be null");
		}
		for (int eventNum = 1; eventNum < events.size(); eventNum++) {
			ArcWithTimestamp arc = events.get(eventNum);
			if (probs.getQuick(arc.getLeaderId(), arc.getFollowerId()) == 0.0) {
				return (double) (eventNum - 1) / (double) (events.size() - 1);
			}
		}
		return 1.0;
	}

	public double getProbability(int leaderId, int followerId) {
		return probs.getQuick(leaderId, followerId);
	}

	/**
	 * @deprecated
	 * @param arc
	 * @return
	 */
	public double getProbability(Arc arc) {
		return probs.getQuick(arc.getLeaderId(), arc.getFollowerId());
	}

	public SparseDoubleMatrix2D getProbs() {
		return probs;
	}

	/**
	 * Prints the probabilities to stdout
	 */
	public void dumpProbabilities() {
		dumpProbabilities(new PrintWriter(System.out));
	}

	/**
	 * Prints the probability to a specific output writer.
	 * 
	 * @param pw
	 *            the output writer.
	 */
	public void dumpProbabilities(PrintWriter pw) {
		pw.println("#Propagation probabilities");
		for (Arc arc : Utilities.getSortedNonZeroArcs(probs)) { // sorted by
																// arcs
			pw.println(arc.getLeaderName() + "\t" + arc.getFollowerName()
					+ "\t"
					+ probs.getQuick(arc.getLeaderId(), arc.getFollowerId()));
		}
	}

	/**
	 * Given a model, it extract a social network that consists of arcs that
	 * exceed a probability threshold.
	 * 
	 * @param model
	 *            The input model.
	 * @param minProbability
	 *            The probability threshold.
	 */
	public static void dumpSocialNetworkOfHighProbabilityArcs(ICModel model,
			double minProbability, PrintWriter pw) {
		SocialNetwork sn = new SocialNetwork(model, minProbability);
		sn.dump(pw);
	}

	/**
	 * Main method to test this class.
	 * 
	 * @param args
	 * @throws JSAPException
	 * @throws IllegalArgumentException
	 * @throws SecurityException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	public static void main(String[] args) throws JSAPException,
			IllegalArgumentException, SecurityException,
			InstantiationException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException,
			ClassNotFoundException, IOException {
		final SimpleJSAP jsap = new SimpleJSAP(
				ICModelConstantWaitingTime.class.getName(),
				"Reads and parses a social network grap and an independent cascade model, for debugging purposes.",
				new Parameter[] {
						new FlaggedOption("social-network", JSAP.STRING_PARSER,
								JSAP.NO_DEFAULT, JSAP.REQUIRED, 's',
								"social-network",
								"The file containing the social network graph"),
						new FlaggedOption("probabilities", JSAP.STRING_PARSER,
								JSAP.NO_DEFAULT, JSAP.REQUIRED, 'p',
								"probabilities",
								"The file containing the propagation probabilities"),
						new FlaggedOption("model", JSAP.STRING_PARSER,
								DEFAULT_MODEL.getSimpleName(),
								JSAP.NOT_REQUIRED, 'm', "model",
								"The propagation model to use"),
						new Switch("dump", 'd', "dump", "Dump the graph"),
						new Switch("run", 'r', "run", "Run propagation"),
						new Switch("likelihood", 'l', "likelihood",
								"Compute log-likelihood given some observations"),
						new FlaggedOption("input", JSAP.STRING_PARSER,
								JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'i',
								"input", "The file containing the observations"),
						new FlaggedOption("start-node", JSAP.STRING_PARSER,
								Node.DEFAULT_START_NODE.getName(),
								JSAP.NOT_REQUIRED, 'n', "start-node",
								"Node to start propagation from"),
						new FlaggedOption("count", JSAP.INTEGER_PARSER, Integer
								.toString(1), JSAP.NOT_REQUIRED, 'c', "count",
								"Number of propagations to run"),
						new FlaggedOption("output", JSAP.STRING_PARSER,
								JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o',
								"output", "File to write propagations to"), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) {
			return;
		}

		String snFilename = jsapResult.getString("social-network");
		SocialNetwork socNet = new SocialNetwork(
				Utilities.getIterator(snFilename));
		String probFilename = jsapResult.getString("probabilities");
		String modelName = jsapResult.getString("model");

		ICModel model = (ICModel) Class
				.forName(ICModel.class.getPackage().getName() + "." + modelName)
				.getConstructor(
						new Class[] { socNet.getClass(), LineIterator.class })
				.newInstance(
						new Object[] { socNet,
								Utilities.getIterator(probFilename) });

		String output = jsapResult.userSpecified("output") ? jsapResult
				.getString("output") : null;
		PrintStream ps = output == null ? System.out : new PrintStream(
				new FileOutputStream(new File(output)));

		if (jsapResult.getBoolean("dump")) {
			model.dump();
		} else if (jsapResult.getBoolean("run")) {
			if (!jsapResult.userSpecified("start-node")) {
				System.err.println("Must specify starting node");
				return;
			}
			Node startNode = new Node(jsapResult.getString("start-node"));
			int count = jsapResult.getInt("count");
			ProgressLogger pl = new ProgressLogger(LOGGER,
					ProgressLogger.TEN_SECONDS, "actions");
			pl.expectedUpdates = count;
			pl.start();
			for (int i = 0; i < count; i++) {
				PropagationHistory history = model.runModel(startNode);
				if (output == null) {
					history.dump();
				} else {
					history.saveTo(ps);
				}
				pl.update();
			}
			pl.stop();
		} else if (jsapResult.getBoolean("likelihood")) {
			String obsFilename = jsapResult.getString("input");
			ObservationsReader observations = new ObservationsReader(
					obsFilename);
			double logLikelihood = model
					.getLogLikelihoodIgnoringParentInformation(observations);
			LOGGER.info("log likelihood (ignoring parent information)="
					+ logLikelihood);

		} else {
			System.err.println("Indicate one operation");
			return;
		}
	}
}
