package edu.toronto.cs.propagation.ic;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;

import edu.toronto.cs.propagation.ObservationsReader;
import edu.toronto.cs.propagation.SocialNetwork;
import edu.toronto.cs.propagation.ic.candidate_selection.CandidateSelectionPolicy;
import edu.toronto.cs.propagation.util.DelayThreshold;
import edu.toronto.cs.propagation.util.Iterative;
import edu.toronto.cs.propagation.util.Utilities;

/**
 * An abstract estimator for an independent cascade model, given a set of observations.
 * 
 */
public abstract class ICEstimate {

	static Logger LOGGER = Logger.getLogger(ICEstimate.class);
	static {
		BasicConfigurator.resetConfiguration();
		BasicConfigurator.configure();
	}

	public static Class<ICEstimateEM> DEFAULT_ESTIMATOR = ICEstimateEM.class;

	ICEstimateAuxiliary auxiliary = null;

	final SocialNetwork sn;

	public ICEstimate(SocialNetwork sn) {
		this.sn = sn;
	}

	public void useAuxiliary(ICEstimateAuxiliary theAuxiliary) {
		if( auxiliary != null ) {
			throw new IllegalStateException("Can't set the auxiliary variable again");
		}
		this.auxiliary = theAuxiliary;
	}

	public void computeAuxiliary(ObservationsReader observations) {
		if( auxiliary != null ) {
			throw new IllegalStateException("Can't set the auxiliary variable again");
		}
		this.auxiliary = new ICEstimateAuxiliary(sn, observations, getCandidateSelectionPolicy());
	}
	
	private void clear() {
		if (auxiliary != null) {
			auxiliary.clear();
		}
	}

	public int getnActions() {
		return auxiliary.getnActions();
	}
	
	public double getLogLikelihoodIgnoringParentInformation(ICModel estimatedModel) {
		return estimatedModel.getLogLikelihoodIgnoringParentInformation(auxiliary);
	}
	
	abstract CandidateSelectionPolicy getCandidateSelectionPolicy();

	public abstract ICModel estimate(int numOfChunks);

	public abstract ICModel estimate(Logger logger, int numOfChunks);
	
	public static void main(String[] args) throws JSAPException, InstantiationException, IllegalAccessException, ClassNotFoundException, IllegalArgumentException,
			SecurityException, InvocationTargetException, NoSuchMethodException, IOException {

		final SimpleJSAP jsap = new SimpleJSAP(ICEstimate.class.getName(), "Estimates a propagation model from a set of observations.", new Parameter[] {
				new FlaggedOption("social-network", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 's', "social-network",
						"The file containing the social network graph"),
				new FlaggedOption("estimator", JSAP.STRING_PARSER, DEFAULT_ESTIMATOR.getSimpleName(), JSAP.NOT_REQUIRED, 'e', "estimator", "The estimator to run"),
				new FlaggedOption("actual-probabilities", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'a', "actual-probabilities",
						"The actual probabilities from where the observations were generated, to be used for printing a comparison"),
				new FlaggedOption("max-iterations", JSAP.INTEGER_PARSER, Integer.toString(ICEstimateEM.DEFAULT_MAX_ITERATIONS), JSAP.NOT_REQUIRED, 'm', "max-iterations",
						"The maximum number of iterations allowed for iterative methods (-1=infinite)"),
				new FlaggedOption("number-of-chunks", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'r', "number-of-chunks",
						"The number of chunks to be sparsified in parralel"),
				new FlaggedOption("min-difference", JSAP.DOUBLE_PARSER, Double.toString(ICEstimateEM.DEFAULT_MIN_DIFFERENCE), JSAP.NOT_REQUIRED, 'd', "min-difference",
						"The minimum difference between the L2 norm in two iterations to continue iterating (0=ignore)"),
				new FlaggedOption("delay-threshold-multiplier", JSAP.DOUBLE_PARSER, Double.toString(ICEstimateEMWithDelayThreshold.DEFAULT_DELAY_THRESHOLD_MULTIPLIER),
						JSAP.NOT_REQUIRED, 't', "delay-threshold-multiplier",
						"The delay-threshold multiplier (will be multiplied by the mean delay to compute the threshold)"),
				new FlaggedOption("auxiliary-basename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NO_SHORTFLAG, "auxiliary-basename",
						"The base name for reading a pre-computed auxiliary structure"),
				new Switch("debug-recompute-ll", JSAP.NO_SHORTFLAG, "debug-recompute-ll", "Re-compute the log-likelihood of the estimated model from the input data, use for debugging"), 
				new FlaggedOption("output-file", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "output-file", "The output file to write the model to"),
				new FlaggedOption("input", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'i', "input", "The file containing the observations"), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) {
			return;
		}

		// Load social network
		String snFilename = jsapResult.getString("social-network");
		SocialNetwork socNet = new SocialNetwork(Utilities.getIterator(snFilename));
		LOGGER.info("Social network size: nodes=" + socNet.sizeNodes() + ", arcs=" + socNet.sizeArcs() );

		// Load estimator
		String estimatorName = jsapResult.getString("estimator");
		Class<?> modelClass = Class.forName(ICEstimate.class.getPackage().getName() + "." + estimatorName);
		ICEstimate estimator = (ICEstimate) modelClass.getConstructor(new Class[] { socNet.getClass() }).newInstance(new Object[] { socNet });
		LOGGER.info("Estimation method: " + estimatorName);
		
		// Open observations
		String obsFilename = jsapResult.getString("input");
		ObservationsReader observations = new ObservationsReader(obsFilename);

		// See if we have an auxiliary file
		if (jsapResult.userSpecified("auxiliary-basename")) {
			
			// Use existing auxiliary file
			String auxiliaryBasename = jsapResult.getString("auxiliary-basename");
			ICEstimateAuxiliary auxiliary = new ICEstimateAuxiliary(socNet, observations, null);
			
			LOGGER.info("Loading pre-computed auxiliary variables");
			auxiliary.read(auxiliaryBasename);
			estimator.useAuxiliary(auxiliary);
			
			if( auxiliary.getCandidateSelectionPolicy().toSpec().equals(estimator.getCandidateSelectionPolicy().toSpec()) ) {
				LOGGER.info("Candidate selection policy: " + auxiliary.getCandidateSelectionPolicy().toSpec() );
			} else {
				throw new IllegalArgumentException("The candidate selection policies do not match: auxiliary has '" + auxiliary.getCandidateSelectionPolicy().toSpec() + "', estimator has '" + estimator.getCandidateSelectionPolicy().toSpec() + "'");
			}
			
		} else {
			
			// Variables that affect the auxiliary generator
			if (jsapResult.userSpecified("delay-threshold-multiplier")) {
				int delayThreshold = (int) (jsapResult.getDouble("delay-threshold-multiplier") * (double) ICModelExponentialWaitingTime.getMEAN_REPOST_WAITING_TIME());
				try {
					((DelayThreshold) estimator).setDelayThreshold(delayThreshold);
				} catch (ClassCastException e) {
					LOGGER.error("This type of estimator does not accept the delay-threshold-multiplier parameter");
					return;
				}
				LOGGER.info("Estimation delayThreshold: " + delayThreshold);
			}
			
			// Compute auxiliary variables
			LOGGER.info("Computing auxiliary variables");
			estimator.computeAuxiliary(observations);
		}
		LOGGER.info("Number of actions: " + estimator.getnActions() );
			
		// Configure estimator
		if (jsapResult.userSpecified("max-iterations")) {
			int maxIterations = jsapResult.getInt("max-iterations");
			try {
				((Iterative) estimator).setMaxIterations(maxIterations);
			} catch (ClassCastException e) {
				LOGGER.error("This type of estimator does not accept the max-iterations parameter");
				return;
			}
			LOGGER.info("Estimation maxIterations: " + maxIterations);
		}
		if (jsapResult.userSpecified("min-difference")) {
			double minDifference = jsapResult.getDouble("min-difference");
			try {
				((Iterative) estimator).setMinDifference(minDifference);
			} catch (ClassCastException e) {
				LOGGER.error("This type of estimator does not accept the min-difference parameter");
				return;
			}
			LOGGER.info("Estimation minDifference: " + minDifference);
		}
		int numOfChunks = 1;
		if (jsapResult.userSpecified("number-of-chunks")) {
			numOfChunks = jsapResult.getInt("number-of-chunks");
			LOGGER.info("Estimation  number of chunks: " + numOfChunks);
		}

		LOGGER.info("BEGIN estimation");
		ICModel estimatedModel = estimator.estimate(LOGGER, numOfChunks);
		LOGGER.info("DONE estimation");

		if (jsapResult.userSpecified("output-file")) {
			String filename = jsapResult.getString("output-file");
			LOGGER.info("Writing model to " + filename);
			PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(new File(filename))));
			estimatedModel.dumpProbabilities(pw);
			pw.close();
		}

		if (jsapResult.userSpecified("actual-probabilities")) {
			// Print comparison
			ICModel actualModel = new ICModel(socNet, Utilities.getIterator(jsapResult.getString("actual-probabilities")));
			actualModel.dumpComparisonWithAlternative(estimatedModel, true);
		}

		double logLikelihood = estimator.getLogLikelihoodIgnoringParentInformation(estimatedModel);
		LOGGER.info("Estimated model: log likelihood (ignoring parent information)=" + logLikelihood);
		
		if( jsapResult.getBoolean("debug-recompute-ll") ) {
			estimator.clear();
			estimator = null;
			logLikelihood = estimatedModel.getLogLikelihoodIgnoringParentInformation(observations);
			LOGGER.info("Estimated model: log likelihood (ignoring parent information)=" + logLikelihood);
		}

	}
}
