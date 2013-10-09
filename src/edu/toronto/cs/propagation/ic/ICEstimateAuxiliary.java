package edu.toronto.cs.propagation.ic;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.logging.ProgressLogger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.Properties;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import cern.colt.matrix.impl.SparseDoubleMatrix2D;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;

import edu.toronto.cs.propagation.ObservationsReader;
import edu.toronto.cs.propagation.PropagationHistory;
import edu.toronto.cs.propagation.SocialNetwork;
import edu.toronto.cs.propagation.ic.candidate_selection.CandidateSelectionPolicy;
import edu.toronto.cs.propagation.ic.candidate_selection.CandidateSelectionPolicy.CandidateType;
import edu.toronto.cs.propagation.ic.candidate_selection.SelectByTimePrecedence;
import edu.toronto.cs.propagation.util.Arc;
import edu.toronto.cs.propagation.util.ArcWithTimestamp;
import edu.toronto.cs.propagation.util.Int2ArrayOfIntMap;
import edu.toronto.cs.propagation.util.Node;
import edu.toronto.cs.propagation.util.SparseIntArrayListMatrix2D;
import edu.toronto.cs.propagation.util.Utilities;

/**
 * Auxiliary data structures.
 * 
 */
public class ICEstimateAuxiliary {

	private static final String PROPERTIES_KEY_CANDIDATE_SELECTION_POLICY = "candidateSelectionPolicy";

	private static final String PROPERTIES_KEY_N_ACTIONS = "nActions";

	private static final String FILE_SUFFIX_NODE_ACTIONS = ".nodeActions";

	private static final String FILE_SUFFIX_ACTIVATION_TIME_PER_ACTION = ".activationTimePerAction";

	private static final String FILE_SUFFIX_A_PLUS = ".Aplus";

	private static final String FILE_SUFFIX_A_MINUS = ".Aminus";

	private static final String FILE_SUFFIX_B_PLUS = ".Bplus";

	private static final String FILE_SUFFIX_PROPERTIES = ".properties";

	static Logger LOGGER = Logger.getLogger(ICEstimateAuxiliary.class);
	static {
		BasicConfigurator.resetConfiguration();
		BasicConfigurator.configure();
	}

	/**
	 * The social network.
	 */
	private final SocialNetwork sn;

	/**
	 * The observations (propagations).
	 */
	private final ObservationsReader observationsReader;

	/**
	 * For every node <em>v</em>, it contains all the actions that activated
	 * this node.
	 */
	private Int2ObjectOpenHashMap<IntOpenHashSet> nodeActions;

	/**
	 * For every action <em>a</em>, for every node <em>v</em> that was activated
	 * by that action, it contains its activation time.
	 */
	private ObjectArrayList<Int2LongOpenHashMap> activationTimePerAction;

	/**
	 * For every arc (parent,child), it contains the actions for which that
	 * parent may have activated the child.
	 */
	private SparseIntArrayListMatrix2D Aplus;

	/**
	 * For every arc (parent,child), it contains the actions for which that
	 * parent certainly did not active the child.
	 */
	private SparseIntArrayListMatrix2D Aminus;

	/**
	 * For every action <em>a</em>, for every node <em>v</em>, it contains the
	 * set of nodes <em>u</em> such that <em>Aplus(u,v)</em> contains <em>a</em>
	 * .
	 */
	private ObjectArrayList<Int2ArrayOfIntMap> Bplus;

	/**
	 * The total number of actions seen
	 */
	private int nActions;

	/**
	 * Contains the policy to decide if a parent could have activated a child.
	 */
	private CandidateSelectionPolicy candidateSelectionPolicy;

	private Int2ObjectOpenHashMap<IntArrayList> cPlusOnline;

	private Int2ObjectOpenHashMap<IntArrayList> cMinusOnline;

	/**
	 * Creates a new set of auxiliary variables.
	 * 
	 * @param sn
	 *            the social network
	 * @param observations
	 *            the set of propagations observed
	 * @param candidateSelectionPolicy
	 *            a policy for selecting the candidate activators from the
	 *            parents of a node
	 */
	public ICEstimateAuxiliary(SocialNetwork sn,
			ObservationsReader observations,
			CandidateSelectionPolicy candidateSelectionPolicy) {
		this.sn = sn;
		this.observationsReader = observations;
		this.candidateSelectionPolicy = candidateSelectionPolicy;
		this.nActions = -1;
		this.Aplus = null;
		this.Aminus = null;
		this.Bplus = null;
		this.nodeActions = null;
		this.activationTimePerAction = null;
		this.cPlusOnline = null;
		this.cMinusOnline = null;
	}

	public void clear() {
		Aplus = null;
		Aminus = null;
		Bplus = null;
		nodeActions = null;
		activationTimePerAction = null;
	}

	public int getnActions() {
		if (nActions == -1) {
			computeNodeActions();
		}
		return nActions;
	}

	public CandidateSelectionPolicy getCandidateSelectionPolicy() {
		return this.candidateSelectionPolicy;
	}

	/**
	 * Pre-computes and returns {@link #nodeActions}
	 * 
	 * @return
	 */
	public Int2ObjectOpenHashMap<IntOpenHashSet> getNodeActions() {
		if (nodeActions == null) {
			computeNodeActions();
		}
		return nodeActions;
	}

	/**
	 * Computes {@link #nodeActions}
	 * 
	 * @return
	 */
	private void computeNodeActions() {
		nodeActions = new Int2ObjectOpenHashMap<IntOpenHashSet>();
		IntOpenHashSet distinctActions = new IntOpenHashSet();
		Iterator<PropagationHistory> iterator = observationsReader.iterator();
		for (int action = 0; action < observationsReader.size(); action++) {
			PropagationHistory history = iterator.next();
			for (ArcWithTimestamp propagation : history.getEvents()) {
				int childId = propagation.getFollowerId();
				if (!nodeActions.containsKey(childId)) {
					nodeActions.put(childId, new IntOpenHashSet());
				}
				nodeActions.get(childId).add(action);
				distinctActions.add(action);
			}
		}

		// Remove node omega
		nodeActions.remove(Node.DEFAULT_START_NODE.getId());
		nActions = distinctActions.size();
	}

	/**
	 * Pre-computes and returns {@link #activationTimePerAction}
	 * 
	 * @return
	 */
	public ObjectArrayList<Int2LongOpenHashMap> getActivationTimePerAction() {
		if (activationTimePerAction == null) {
			computeActivationTimePerAction();
		}
		return activationTimePerAction;
	}

	/**
	 * Computes {@link #activationTimePerAction}
	 */
	private void computeActivationTimePerAction() {
		if (nActions == -1) {
			computeNodeActions();
		}

		ProgressLogger pl = new ProgressLogger(LOGGER,
				ProgressLogger.TEN_SECONDS, "actions");
		pl.expectedUpdates = nActions;
		pl.start("Begin computing activation time per action");
		activationTimePerAction = new ObjectArrayList<Int2LongOpenHashMap>(
				nActions);
		Iterator<PropagationHistory> iterator = observationsReader.iterator();
		for (int action = 0; action < nActions; action++) {
			pl.update();
			PropagationHistory history = iterator.next();
			Int2LongOpenHashMap activationTime = new Int2LongOpenHashMap(
					history.size());
			activationTime.defaultReturnValue(-1);
			for (ArcWithTimestamp ev : history.getEvents()) {
				activationTime.put(ev.getFollowerId(), ev.getTimestamp());
			}
			activationTimePerAction.add(action, activationTime);
		}
		pl.stop("Done computing activation time per action");
	}

	/**
	 * Pre-computes and returns {@link #Aplus}
	 * 
	 * @return
	 */
	public SparseIntArrayListMatrix2D getAplus() {
		if (Aplus == null) {
			computeAplusAminusBplus();
		}
		return Aplus;
	}

	/**
	 * Pre-computes and returns {@link #Aminus}
	 * 
	 * @return
	 */
	public SparseIntArrayListMatrix2D getAminus() {
		if (Aminus == null) {
			computeAplusAminusBplus();
		}
		return Aminus;
	}

	/**
	 * Pre-computes and returns {@link #Bplus}
	 * 
	 * @return
	 */
	public ObjectArrayList<Int2ArrayOfIntMap> getBplus() {
		if (Bplus == null) {
			computeAplusAminusBplus();
		}
		return Bplus;
	}

	/**
	 * Computes {@link #Aplus}, {@link #Aminus} and {@link #Bplus}.
	 */
	private void computeAplusAminusBplus() {
		if (candidateSelectionPolicy == null) {
			throw new IllegalArgumentException(
					"Can't compute Aplus and Aminus unless an edge placement policy is given");
		}
		if (activationTimePerAction == null) {
			computeActivationTimePerAction();
		}

		SparseDoubleMatrix2D AplusSize = Node.getSparseDoubleMatrix();
		SparseDoubleMatrix2D AminusSize = Node.getSparseDoubleMatrix();

		// B(action,v) = u such that ( action in A+(u,v) )
		Bplus = new ObjectArrayList<Int2ArrayOfIntMap>(nActions);

		ProgressLogger pl = new ProgressLogger(LOGGER,
				ProgressLogger.TEN_SECONDS, "actions");
		pl.start("Begin computing sizes of Aplus and Aminus; and Bplus using "
				+ candidateSelectionPolicy.toSpec());
		pl.expectedUpdates = nActions;
		Iterator<PropagationHistory> iterator = observationsReader.iterator();
		Int2ObjectOpenHashMap<IntOpenHashSet> bPlusAction = new Int2ObjectOpenHashMap<IntOpenHashSet>();
		for (int action = 0; action < nActions; action++) {
			pl.update();

			PropagationHistory history = iterator.next();
			Int2LongOpenHashMap activated = activationTimePerAction.get(action);
			bPlusAction.clear();

			for (ArcWithTimestamp ev : history.getEvents()) {
				int parentId = ev.getFollowerId();
				for (Arc childArc : sn.getFollowers(parentId)) {
					int childId = childArc.getFollowerId();

					CandidateType edgePlacement = candidateSelectionPolicy
							.decideCandidateType(activated, parentId, childId);

					switch (edgePlacement) {
					case COULD_HAVE_ACTIVATED:
						AplusSize.setQuick(parentId, childId,
								AplusSize.getQuick(parentId, childId) + 1);
						if (!bPlusAction.containsKey(childId)) {
							bPlusAction.put(childId, new IntOpenHashSet());
						}
						bPlusAction.get(childId).add(parentId);
						break;
					case FAILED_TO_ACTIVATE:
						AminusSize.setQuick(parentId, childId,
								AminusSize.getQuick(parentId, childId) + 1);

						break;
					case OTHER:
						break;
					default:
						throw new IllegalStateException();
					}
				}
			}
			Bplus.add(action, new Int2ArrayOfIntMap(bPlusAction));
		}
		pl.stop("Done computing sizes: number of arcs in Aplus="
				+ AplusSize.cardinality() + ", Aminus="
				+ AminusSize.cardinality() + ", Bplus=" + Bplus.size());

		// A+(u,v) = actions for which u was activated before v got activated
		Aplus = Node.getSparseObjectMatrix();
		// A-(u,v) = actions for which u was activated but v was not activated
		Aminus = Node.getSparseObjectMatrix();

		pl = new ProgressLogger(LOGGER, ProgressLogger.TEN_SECONDS, "arcs");
		pl.start("Begin allocating arrays for Aplus and Aminus");
		pl.expectedUpdates = AplusSize.cardinality() + AminusSize.cardinality();

		int aPlusSize = 0;
		for (Arc arc : Utilities.getSortedNonZeroArcs(AplusSize)) {
			pl.update();
			int size = (int) AplusSize.getQuick(arc.getLeaderId(),
					arc.getFollowerId());
			Aplus.setQuick(arc.getLeaderId(), arc.getFollowerId(),
					new IntArrayList(size));
			aPlusSize += size;
		}

		int aMinusSize = 0;
		for (Arc arc : Utilities.getSortedNonZeroArcs(AminusSize)) {
			pl.update();
			int size = (int) AminusSize.getQuick(arc.getLeaderId(),
					arc.getFollowerId());
			Aminus.setQuick(arc.getLeaderId(), arc.getFollowerId(),
					new IntArrayList(size));
			aMinusSize += size;
		}

		pl.stop("Done allocating arrays: actions/arc in Aplus="
				+ (double) aPlusSize / (double) (AplusSize.cardinality())
				+ " in Aminus=" + (double) aMinusSize
				/ (double) (AminusSize.cardinality()));
		Utilities.setToZero(AplusSize);
		AplusSize = null;
		Utilities.setToZero(AminusSize);
		AminusSize = null;

		pl = new ProgressLogger(LOGGER, ProgressLogger.TEN_SECONDS, "actions");
		pl.start("Begin computing Aplus and Aminus using "
				+ candidateSelectionPolicy.toSpec());
		pl.expectedUpdates = nActions;
		iterator = observationsReader.iterator();
		for (int action = 0; action < nActions; action++) {
			pl.update();

			PropagationHistory history = iterator.next();
			Int2LongOpenHashMap activated = activationTimePerAction.get(action);
			bPlusAction.clear();

			for (ArcWithTimestamp ev : history.getEvents()) {
				int parentId = ev.getFollowerId();
				for (Arc childArc : sn.getFollowers(parentId)) {
					int childId = childArc.getFollowerId();

					CandidateType edgePlacement = candidateSelectionPolicy
							.decideCandidateType(activated, parentId, childId);

					switch (edgePlacement) {
					case COULD_HAVE_ACTIVATED:
						((IntArrayList) Aplus.getQuick(parentId, childId))
								.add(action);

						break;
					case FAILED_TO_ACTIVATE:
						((IntArrayList) Aminus.getQuick(parentId, childId))
								.add(action);
						break;

					case OTHER:
						break;
					default:
						throw new IllegalStateException();
					}
				}
			}
		}
		pl.stop("Done.");
	}

	/**
	 * Contains for every node v, the arcs in {@link #Aminus} that have v as
	 * child.
	 */
	Int2ArrayOfIntMap aPlusParentsOfChild;

	/**
	 * Contains for every node v, the arcs in {@link #Aminus} that have v as
	 * child.
	 */
	Int2ArrayOfIntMap aMinusParentsOfChild;

	/**
	 * Computes for every arc in a map of arcs to lists of integers A, a map
	 * from every child to the arcs it participates on.
	 * 
	 * @param A
	 * @return
	 */
	private Int2ArrayOfIntMap computeParentsOfChild(SparseIntArrayListMatrix2D A) {
		Int2ObjectOpenHashMap<IntOpenHashSet> AArcsOfNode = new Int2ObjectOpenHashMap<IntOpenHashSet>();
		for (Arc arc : Utilities.getSortedNonZeroArcs(A)) {
			int node = arc.getFollowerId();
			if (AArcsOfNode.containsKey(node) == false) {
				AArcsOfNode.put(node, new IntOpenHashSet());
			}
			AArcsOfNode.get(node).add(arc.getLeaderId());
		}
		return new Int2ArrayOfIntMap(AArcsOfNode);
	}

	/**
	 * Gets the value of cPlus for a given node on-line. Slower, but less
	 * memory-intensive, than {@link #getCplusPrecomputing(int)}.
	 * 
	 * @param node
	 * @return
	 */
	public Int2ObjectOpenHashMap<IntArrayList> getCplusOnline(int node) {
		if (cPlusOnline == null) {
			if (nActions == -1) {
				computeNodeActions();
			}
			cPlusOnline = new Int2ObjectOpenHashMap<IntArrayList>(nActions);
		}
		return getCFromAOnline(getAplus(), cPlusOnline,
				getAplusParentsOfChild(node), node);
	}

	public int[] getAplusParentsOfChild(int node) {
		if (aPlusParentsOfChild == null) {
			aPlusParentsOfChild = computeParentsOfChild(getAplus());
		}
		return aPlusParentsOfChild.get(node);
	}

	/**
	 * Gets the value of cMinus for a given node on-line. Slower, but less
	 * memory-intensive, than {@link #getCminusPrecomputing(int)}.
	 * 
	 * @param node
	 * @return
	 */
	public Int2ObjectOpenHashMap<IntArrayList> getCminusOnline(int node) {
		if (cMinusOnline == null) {
			if (nActions == -1) {
				computeNodeActions();
			}
			cMinusOnline = new Int2ObjectOpenHashMap<IntArrayList>(nActions);
		}
		return getCFromAOnline(getAminus(), cMinusOnline,
				getAminusParentsOfChild(node), node);
	}

	public int[] getAminusParentsOfChild(int node) {
		if (aMinusParentsOfChild == null) {
			aMinusParentsOfChild = computeParentsOfChild(getAminus());
		}
		return aMinusParentsOfChild.get(node);
	}

	/**
	 * Given a map from arcs to lists of actions, and a specific child node,
	 * computes a map from actions to parents for the arcs that end in that
	 * child node.
	 * 
	 * Compared to the methods that pre-compute, we avoid storing the list of
	 * actions on each arc, but store only the arcs.
	 * 
	 * @param A
	 * @param cOnline
	 * @param parentsOfChild
	 * @param node
	 * @return
	 */
	private Int2ObjectOpenHashMap<IntArrayList> getCFromAOnline(
			SparseIntArrayListMatrix2D A,
			Int2ObjectOpenHashMap<IntArrayList> cOnline, int[] parentsOfChild,
			int node) {
		// The node is not the child of any arc in A
		if (parentsOfChild == null) {
			return null;
		}

		cOnline.clear(); // It is important because other methods may (i)
							// iterate through the keys
							// of this array, or (ii) expect to find a null
							// somewhere instead of an
							// empty list
		int size = 0;
		for (int leaderId : parentsOfChild) {
			for (int action : A.getQuick(leaderId, node)) {
				if (cOnline.containsKey(action) == false) {
					cOnline.put(action, new IntArrayList());
				}
				cOnline.get(action).add(leaderId);
				size++;
			}
		}
		return (size > 0) ? cOnline : null;
	}

	/**
	 * Gets the log-likelihood of a block.
	 * 
	 * @param model
	 * @param v
	 * @param nodeActionsV
	 * @param cPlusV
	 * @param cMinusV
	 * @param selectedParents
	 * @return
	 */
	public double blockLogLikelihood(ICModel model, int v,
			Int2ObjectOpenHashMap<IntArrayList> cPlusV,
			Int2ObjectOpenHashMap<IntArrayList> cMinusV,
			IntOpenHashSet selectedParents) {
		return model.blockLogLikelihoodUsingCplusCminus(v, cPlusV, cMinusV,
				selectedParents);
	}

	/**
	 * Gets the log-likelihood of a block.
	 * 
	 * @param model
	 * @param v
	 * @param nodeActionsV
	 * @param cPlusV
	 * @param cMinusV
	 * @param selectedParents
	 * @return
	 */
	public double blockLogLikelihood(ICModel model, int v,
			Int2ObjectOpenHashMap<IntArrayList> cPlusV,
			Int2ObjectOpenHashMap<IntArrayList> cMinusV,
			ObjectOpenHashSet<Arc> selectedParents) {
		return model.blockLogLikelihoodUsingCplusCminus(v, cPlusV, cMinusV,
				selectedParents);
	}

	public double blockLogLikelihoodIncrease(ICModel model, int v,
			Int2ObjectOpenHashMap<IntArrayList> cPlusV,
			Int2ObjectOpenHashMap<IntArrayList> cMinusV,
			ObjectOpenHashSet<Arc> alreadySelectedParents, int extraParentId) {
		return model.blockLogLikelihoodIncreaseUsingCplusCminus(v, cPlusV,
				cMinusV, alreadySelectedParents, extraParentId);
	}

	/**
	 * Obtains the {@link #observationsReader}
	 * 
	 * @return
	 */
	public ObservationsReader getObservationsReader() {
		return observationsReader;
	}

	/**
	 * Obtains the number of nodes in the underlying social network {@link #sn}.
	 * 
	 * @return
	 */
	public int getSizeNodes() {
		return sn.sizeNodes();
	}

	public void write(String basename) throws IOException {
		PrintWriter pwNodeActions = new PrintWriter(new BufferedWriter(
				new FileWriter(new File(basename + FILE_SUFFIX_NODE_ACTIONS))));
		PrintWriter pwActivationTimePerAction = new PrintWriter(
				new BufferedWriter(new FileWriter(new File(basename
						+ FILE_SUFFIX_ACTIVATION_TIME_PER_ACTION))));
		PrintWriter pwAplus = new PrintWriter(new BufferedWriter(
				new FileWriter(new File(basename + FILE_SUFFIX_A_PLUS))));
		PrintWriter pwAminus = new PrintWriter(new BufferedWriter(
				new FileWriter(new File(basename + FILE_SUFFIX_A_MINUS))));
		PrintWriter pwBplus = new PrintWriter(new BufferedWriter(
				new FileWriter(new File(basename + FILE_SUFFIX_B_PLUS))));
		write(pwNodeActions, pwActivationTimePerAction, pwAplus, pwAminus,
				pwBplus);
		pwNodeActions.close();
		pwActivationTimePerAction.close();
		pwAplus.close();
		pwAminus.close();
		pwBplus.close();

		Properties properties = new Properties();
		properties.setProperty(PROPERTIES_KEY_N_ACTIONS,
				Integer.toString(nActions));
		properties.setProperty(PROPERTIES_KEY_CANDIDATE_SELECTION_POLICY,
				candidateSelectionPolicy.toSpec());
		FileOutputStream out = new FileOutputStream(new File(basename
				+ FILE_SUFFIX_PROPERTIES));
		properties.store(out,
				"Created by " + ICEstimateAuxiliary.class.getSimpleName());
	}

	private void write(PrintWriter outNodeActions,
			PrintWriter outActivationTimePerAction, PrintWriter outAplus,
			PrintWriter outAminus, PrintWriter outBplus) {
		writeNodeActions(outNodeActions);
		writeActivationTimePerAction(outActivationTimePerAction);
		writeA(outAplus, getAplus());
		writeA(outAminus, getAminus());
		writeBplus(outBplus);
	}

	private void writeNodeActions(PrintWriter out) {
		getNodeActions();
		for (int nodeId : nodeActions.keySet()) {
			if (nodeActions.get(nodeId) != null) {
				for (int action : nodeActions.get(nodeId)) {
					out.write(Node.getName(nodeId) + "\t" + action + "\n");
				}
			}
		}
	}

	private void writeActivationTimePerAction(PrintWriter out) {
		getActivationTimePerAction();
		for (int action = 0; action < activationTimePerAction.size(); action++) {
			Int2LongOpenHashMap activationTime = activationTimePerAction
					.get(action);
			for (int nodeId : activationTime.keySet()) {
				long time = activationTime.get(nodeId);
				out.write(action + "\t" + Node.getName(nodeId) + "\t" + time
						+ "\n");
			}
		}
	}

	private void writeA(PrintWriter out, SparseIntArrayListMatrix2D A) {
		cern.colt.list.IntArrayList rowList = new cern.colt.list.IntArrayList();
		cern.colt.list.IntArrayList columnList = new cern.colt.list.IntArrayList();
		cern.colt.list.ObjectArrayList valueList = new cern.colt.list.ObjectArrayList();
		A.getNonZeros(rowList, columnList, valueList);
		for (int i = 0; i < rowList.size(); i++) {
			int parentId = rowList.get(i);
			int childId = columnList.get(i);
			IntArrayList actions = (IntArrayList) valueList.get(i);
			for (int action : actions) {
				out.write(Node.getName(parentId) + "\t" + Node.getName(childId)
						+ "\t" + action + "\n");
			}
		}
	}

	private void writeBplus(PrintWriter out) {
		getBplus();
		for (int action = 0; action < Bplus.size(); action++) {
			for (int childId : Bplus.get(action).keySet()) {
				for (int parentId : Bplus.get(action).get(childId)) {
					out.write(action + "\t" + Node.getName(childId) + "\t"
							+ Node.getName(parentId) + "\n");
				}
			}
		}
	}

	public void read(String basename) throws IOException {
		FileInputStream in = new FileInputStream(new File(basename
				+ FILE_SUFFIX_PROPERTIES));
		Properties properties = new Properties();
		properties.load(in);

		nActions = Integer.parseInt(properties
				.getProperty(PROPERTIES_KEY_N_ACTIONS));
		candidateSelectionPolicy = CandidateSelectionPolicy.fromSpec(properties
				.getProperty(PROPERTIES_KEY_CANDIDATE_SELECTION_POLICY));

		LineIterator itNodeActions = new LineIterator(new FastBufferedReader(
				new FileReader(new File(basename + FILE_SUFFIX_NODE_ACTIONS))));
		LineIterator itActivationTimePerAction = new LineIterator(
				new FastBufferedReader(new FileReader(new File(basename
						+ FILE_SUFFIX_ACTIVATION_TIME_PER_ACTION))));
		LineIterator itAplus = new LineIterator(new FastBufferedReader(
				new FileReader(new File(basename + FILE_SUFFIX_A_PLUS))));
		LineIterator itAminus = new LineIterator(new FastBufferedReader(
				new FileReader(new File(basename + FILE_SUFFIX_A_MINUS))));
		LineIterator itBplus = new LineIterator(new FastBufferedReader(
				new FileReader(new File(basename + FILE_SUFFIX_B_PLUS))));
		read(itNodeActions, itActivationTimePerAction, itAplus, itAminus,
				itBplus);
	}

	private void read(LineIterator inNodeActions,
			LineIterator inActivationTimePerAction, LineIterator inAplus,
			LineIterator inAminus, LineIterator inBplus) {
		readNodeActions(inNodeActions);
		readActivationTimePerAction(inActivationTimePerAction);
		Aplus = readA(inAplus);
		Aminus = readA(inAminus);
		readBplus(inBplus);
	}

	private void readNodeActions(LineIterator in) {
		nodeActions = new Int2ObjectOpenHashMap<IntOpenHashSet>();
		String line;
		while (in.hasNext()) {
			line = in.next().toString();
			String[] tokens = line.split("\t", 2);
			int childId = Node.getId(tokens[0]);
			int action = Integer.parseInt(tokens[1]);
			if (!nodeActions.containsKey(childId)) {
				nodeActions.put(childId, new IntOpenHashSet());
			}
			nodeActions.get(childId).add(action);
		}

		// Remove node omega
		nodeActions.remove(Node.DEFAULT_START_NODE.getId());
	}

	private void readActivationTimePerAction(LineIterator in) {
		activationTimePerAction = new ObjectArrayList<Int2LongOpenHashMap>(
				nActions);
		String line;
		int lastAction = -1;
		Int2LongOpenHashMap activationTime = new Int2LongOpenHashMap();
		activationTime.defaultReturnValue(-1);
		while (in.hasNext()) {
			line = in.next().toString();
			String[] tokens = line.split("\t", 3);
			int action = Integer.parseInt(tokens[0]);
			int nodeId = Node.getId(tokens[1]);
			long time = Long.parseLong(tokens[2]);
			if (action != lastAction && lastAction != -1) {
				activationTimePerAction.add(lastAction,
						(Int2LongOpenHashMap) activationTime.clone());
				activationTime.clear();
			}
			activationTime.put(nodeId, time);
			lastAction = action;
		}
		if (lastAction != -1) {
			activationTimePerAction.add(lastAction,
					(Int2LongOpenHashMap) activationTime.clone());
		}
	}

	private SparseIntArrayListMatrix2D readA(LineIterator in) {
		SparseIntArrayListMatrix2D A = Node.getSparseObjectMatrix();
		String line;
		int lastParent = -1;
		int lastChild = -1;
		IntOpenHashSet actions = new IntOpenHashSet();
		while (in.hasNext()) {
			line = in.next().toString();
			String[] tokens = line.split("\t", 3);
			int parentId = Node.getId(tokens[0]);
			int childId = Node.getId(tokens[1]);
			int action = Integer.parseInt(tokens[2]);
			if (parentId != lastParent || childId != lastChild) {
				A.setQuick(lastParent, lastChild, new IntArrayList(actions));
				actions.clear();
			}
			actions.add(action);
			lastParent = parentId;
			lastChild = childId;
		}
		if (lastParent != -1 && lastChild != -1 && actions.size() > 0) {
			A.setQuick(lastParent, lastChild, new IntArrayList(actions));
		}
		return A;
	}

	private void readBplus(LineIterator in) {
		Bplus = new ObjectArrayList<Int2ArrayOfIntMap>(nActions);
		String line;
		int lastAction = -1;
		Int2ObjectOpenHashMap<IntOpenHashSet> bAction = new Int2ObjectOpenHashMap<IntOpenHashSet>();
		while (in.hasNext()) {
			line = in.next().toString();
			String[] tokens = line.split("\t", 3);
			int action = Integer.parseInt(tokens[0]);
			int childId = Node.getId(tokens[1]);
			int parentId = Node.getId(tokens[2]);
			if (action != lastAction && lastAction != -1) {
				for (int i = Bplus.size(); i < lastAction; i++) {
					// Pad with empty
					Bplus.add(i, null);
				}
				Bplus.add(lastAction, new Int2ArrayOfIntMap(bAction));
				bAction.clear();
			}
			if (!bAction.containsKey(childId)) {
				bAction.put(childId, new IntOpenHashSet());
			}
			bAction.get(childId).add(parentId);
			lastAction = action;
		}
		if (lastAction != -1 && bAction.size() > 0) {
			Bplus.add(lastAction, new Int2ArrayOfIntMap(bAction));
		}
	}

	public static void main(String[] args) throws JSAPException,
			InstantiationException, IllegalAccessException,
			ClassNotFoundException, IllegalArgumentException,
			SecurityException, InvocationTargetException,
			NoSuchMethodException, IOException {

		final SimpleJSAP jsap = new SimpleJSAP(
				ICEstimateAuxiliary.class.getName(),
				"Creates an auxiliary data structure from a set of propagations.",
				new Parameter[] {
						new FlaggedOption("social-network", JSAP.STRING_PARSER,
								JSAP.NO_DEFAULT, JSAP.REQUIRED, 's',
								"social-network",
								"The file containing the social network graph"),
						new FlaggedOption("output-basename",
								JSAP.STRING_PARSER, JSAP.NO_DEFAULT,
								JSAP.NOT_REQUIRED, 'o', "output-basename",
								"The base output filename to write the auxiliary structure"),
						new FlaggedOption("input", JSAP.STRING_PARSER,
								JSAP.NO_DEFAULT, JSAP.REQUIRED, 'i', "input",
								"The file containing the observations"), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) {
			return;
		}

		// Load social network and input
		String snFilename = jsapResult.getString("social-network");
		SocialNetwork socNet = new SocialNetwork(
				Utilities.getIterator(snFilename));
		String obsFilename = jsapResult.getString("input");
		ObservationsReader observationsReader = new ObservationsReader(
				obsFilename);

		LOGGER.info("Input data: nodes=" + socNet.sizeNodes() + ", arcs="
				+ socNet.sizeArcs() + ", actions=" + observationsReader.size());
		CandidateSelectionPolicy candidateSelectionPolicy = new SelectByTimePrecedence();
		LOGGER.info("Candidate selection policy: " + candidateSelectionPolicy);

		LOGGER.info("Creating auxiliary data structure");
		ICEstimateAuxiliary auxiliary = new ICEstimateAuxiliary(socNet,
				observationsReader, new SelectByTimePrecedence());
		auxiliary.getNodeActions();
		auxiliary.getActivationTimePerAction();
		auxiliary.getAplus();
		auxiliary.getAminus();
		auxiliary.getBplus();

		LOGGER.info("Writing to file");
		if (jsapResult.userSpecified("output-basename")) {
			String basename = jsapResult.getString("output-basename");
			auxiliary.write(basename);
		}
	}
}
