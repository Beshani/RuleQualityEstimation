package edu.rit.goal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.MutableSet;
import org.jgrapht.graph.DirectedMultigraph;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;

import edu.rit.goal.Experiments.Dataset;
import edu.rit.goal.Experiments.Experiment;
import edu.rit.goal.Experiments.Policy;
import edu.rit.goal.estimator.BinomialEstimator;
import edu.rit.goal.estimator.EstimatorFactory;
import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;
import edu.rit.goal.estimator.EstimatorFactory.NonstatisticalEstimatorType;
import edu.rit.goal.estimator.EstimatorFactory.StatisticalEstimatorType;
import edu.rit.goal.estimator.EstimatorMemento;
import edu.rit.goal.estimator.Sampling;
import edu.rit.goal.graph.GraphDatabase;
import edu.rit.goal.graph.LabeledEdge;
import edu.rit.goal.graph.LatticeGraph;
import edu.rit.goal.metric.Rule;
import edu.rit.goal.metric.RuleMetric;
import edu.rit.goal.metric.RulePCAConfidence;
import edu.rit.goal.metric.RuleSupport;
import edu.rit.goal.mining.RWMining;
import edu.rit.goal.mining.RWMining.RWMiningHyperparameters;
import edu.rit.goal.mining.reward.MaxPolicy;
import edu.rit.goal.mining.reward.ProbabilityPolicy;
import edu.rit.goal.mining.reward.Reward;
import edu.rit.goal.mining.reward.RuleMetricsReward;
import edu.rit.goal.mining.reward.SamplingEffortReward;
import edu.rit.goal.mining.reward.StaticProbabilityPolicy;
import edu.rit.goal.visitor.EstimatedRuleMetricListener;
import edu.rit.goal.visitor.ExactRuleMetricListener;
import edu.rit.goal.visitor.GraphVisitor;
import edu.rit.goal.visitor.RuleMetricFactory;
import edu.rit.goal.visitor.RuleMetricListener;
import edu.rit.goal.visitor.confidence.EstimatedPCAConfidenceVisitor;
import edu.rit.goal.visitor.confidence.EstimatedPCAConfidenceVisitor.SampleSelection;
import edu.rit.goal.visitor.confidence.PCAConfidenceVisitor;
import edu.rit.goal.visitor.support.EstimatedSupportVisitor;
import edu.rit.goal.visitor.support.ExactSupportVisitor;

public class RuleMiningEstimates {

	public class RuleToProcess {
		GraphDatabase db;
		DirectedMultigraph<Integer, LabeledEdge> walk;
		LabeledEdge head;
		int ruleId;
	}

	private synchronized static void printResultToFile(List<String> toReport, String file) {
		try {
			PrintWriter writer = new PrintWriter(new FileOutputStream(new File(file), true));
			for (String report : toReport)
				writer.println(report);
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private synchronized static void printRuleToFile(Rule rule, String file) {
		try {
			Map<Integer, String> mapToVar = new HashMap<>();
			char lastVar = 'a';

			mapToVar.put(rule.getRule().getEdgeSource(rule.getHead()), "" + lastVar++);
			mapToVar.put(rule.getRule().getEdgeTarget(rule.getHead()), "" + lastVar++);

			for (Integer v : rule.getRule().vertexSet())
				if (!mapToVar.containsKey(v))
					mapToVar.put(v, "" + lastVar++);

			Function<LabeledEdge, String> printAtom = e -> {
				return e.predicate + "(" + mapToVar.get(rule.getRule().getEdgeSource(e)) + ", "
						+ mapToVar.get(rule.getRule().getEdgeTarget(e)) + ")";
			};

			StringBuffer toPrint = new StringBuffer("Rule " + rule.getRuleId() + ": ");
			toPrint.append(printAtom.apply(rule.getHead()));
			toPrint.append(" <= ");

			toPrint.append(rule.getRule().edgeSet().stream().filter(e -> e != rule.getHead())
					.map(e -> printAtom.apply(e)).collect(Collectors.joining(", ")));

			PrintWriter writer = new PrintWriter(new FileOutputStream(new File(file), true));
			writer.println(toPrint);
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException {
		final String neo4jFolder = args[0], latticeFolder = args[1], resultsFolder = args[2];
		final int number = Integer.valueOf(args[3]);

		List<Integer> workerIds = new ArrayList<>();

		int threads = 4, maxRuleLength = 4, minSamples = 50;
		double minHC = .05;

		int initId = 0, endId = threads - 1;

		if (args.length == 5) {
			initId = Integer.valueOf(args[4].split("\\-")[0]);
			endId = Integer.valueOf(args[4].split("\\-")[1]);
		}

		for (int i = initId; i <= endId; i++)
			workerIds.add(i);

		// This is how many times we will run the same estimation.
		int runs = 5;

		// How many steps per thread.
		int budget = 1000;

		Integer split = null;

		// TODO Uncomment!
//		final String neo4jFolder = "db", latticeFolder = "lattice", resultsFolder = "results";
//		final int number = 8;
//
//		List<Integer> workerIds = List.of(1);
//
//		int maxRuleLength = 5, minSamples = 50;
//		double minHC = .05;
//

		Experiment exp = Experiments.getEmptyExperiment(Dataset.values()[number], budget);

		// Some exploration is fine.
		exp.epsilon = .1;
		exp.forceClosingRW = true;
		exp.policy = Experiments.Policy.Probability;
		exp.profile = Experiments.Profile.Head;
		exp.reward = Experiments.Reward.SupportConfLength;

		Experiments.configureProfile(exp);
		Experiments.configureReward(exp);

		Dataset dataset = exp.dataset;

		RWMining mining = RWMining.getInstance();
		RWMiningHyperparameters params = mining.new RWMiningHyperparameters();

		params.maxRuleLength = maxRuleLength;
		params.walkBudget = exp.budget;

		params.forceClosing = exp.forceClosingRW;

		params.onlyHead = exp.onlyHead;

		params.split = split;

		if (exp.policy.equals(Policy.Maximum))
			params.policy = new MaxPolicy();

		if (exp.policy.equals(Policy.Probability)) {
			// We are fixing the window size.
			exp.windowSize = 5;
			params.policy = new ProbabilityPolicy();
		}

		if (exp.policy.equals(Policy.StaticProbability)) {
			exp.windowSize = (int) (budget * 1.0 / workerIds.size());
			params.policy = new StaticProbabilityPolicy(exp.windowSize);
		}

		params.policy.setEpsilon(exp.epsilon);

		double[] accuracyValues = new double[] { .05, .10, .15 }, confidenceValues = new double[] { .01, .05, .10 };

		String db = neo4jFolder + File.separator + dataset, lattice = Experiments.getLatticeFolder(exp, latticeFolder);
		String copyDb = neo4jFolder + File.separator + dataset + "_" + number;
		String resultsFile = Experiments.getResultsFile(exp, resultsFolder, File.separator);

		// Copy DB into a new folder!
		if (new File(copyDb).exists())
			FileUtils.deleteDirectory(new File(copyDb));
		FileUtils.copyDirectory(new File(db), new File(copyDb));

		Class<? extends Reward> rewardClass = null;

		if (exp.useRwEffort)
			rewardClass = SamplingEffortReward.class;
		else
			rewardClass = RuleMetricsReward.class;

		DatabaseManagementService dbService = getService(copyDb), latticeService = getService(lattice);

		GraphDatabase graphDb = new GraphDatabase(dbService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME),
				split);
		LatticeGraph latticeDb = new LatticeGraph(latticeService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME));

		params.policy.initPolicy(maxRuleLength, exp.usePrfHead ? graphDb.getPredicates() : null, exp.usePrfLength,
				exp.usePrfNumberOfVars, exp.usePrfNumberOfPreds, rewardClass);

		if (exp.useRwF1) {
			params.policy.addHeadCoverageToReward();
			params.policy.addConfidenceToReward();
		}

		if (exp.useRwConf)
			params.policy.addConfidenceToReward();

		if (exp.useRwSupport)
			params.policy.addSupportToReward();

		if (exp.useRwLength)
			params.policy.addLengthToReward();

		if (exp.useRwPredSize)
			params.policy.addPredicateSizesToReward(graphDb.getPredicateSizes());

		if (!exp.useRwEffort)
			params.policy.setWindowSizeInReward(exp.windowSize);

		System.out.println(new Date() + " -- Mining rules over " + dataset + " using " + workerIds.size()
				+ " threads, max. rule length of " + maxRuleLength + ", min. head coverage of " + minHC
				+ " and algorithm " + exp.algo + " with budget " + exp.budget);

		params.processRule = (toProcess, step, workerId) -> {
			System.out.println(
					"\tWorker: " + workerId + "; Processing Rule " + toProcess.getRuleId() + " -- " + new Date());

			GraphVisitor queryVisitor = new GraphVisitor(graphDb, toProcess.getRule(), toProcess.getHead(), split);

			System.out.println("\tWorker: " + workerId + "; Computing exact support of Rule " + toProcess.getRuleId()
					+ " -- " + new Date());

			printRuleToFile(toProcess, resultsFile);

			ExactSupportVisitor exactSupportVisitor = RuleMetricFactory.getExactSupport(queryVisitor);

			RuleMetricListener exactSupportListener = new ExactRuleMetricListener(
					new RuleSupport(queryVisitor.getHeadSize()));
			exactSupportVisitor.compute(List.of(exactSupportListener));

			RuleSupport support = (RuleSupport) exactSupportListener.getMetric();
			AtomicReference<String> atomicCorruptStr = new AtomicReference<>();

			toProcess.setSupport(support);

			// Printing to file as soon as we compute is problematic when resuming. We will
			// keep everything we have computed stored until the end. We may waste
			// computations if the job is canceled, but we should be able to resume in
			// cleaner way. Also, the file is only opened once.
			List<String> allToReport = new ArrayList<>();
			TriFunction<String, RuleMetricListener, RulePCAConfidence, Void> reportResult = (technique, listener,
					exactConf) -> {
				Map<String, Object> toReport = new TreeMap<>();

				toReport.put("RuleId", toProcess.getRuleId());
				toReport.put("Technique", technique);

				RuleMetric metric = listener.getMetric();

				if (metric instanceof RuleSupport) {
					RuleSupport s = (RuleSupport) metric;
					toReport.put("Value", s.support);
					toReport.put("Calls", s.supportMatchingCalls);
					toReport.put("HC", s.getHeadCoverage());
				}

				if (metric instanceof RulePCAConfidence) {
					RulePCAConfidence p = (RulePCAConfidence) metric;

					toReport.put("Value" + atomicCorruptStr.get(), p.pcaPrime);
					toReport.put("Calls" + atomicCorruptStr.get(), p.pcaPrimeMatchingCalls);

					RuleSupport supportToUse = support;

					// For AnyBURL, let's get the support from the pairs found.
					if (technique.contains("AnyBURL")) {
						RuleSupport otherSupport = new RuleSupport(
								graphDb.getPredicateSizes().get(toProcess.getHead().predicate));

						MutableSet<Entry<Integer, Integer>> pairs = Sets.mutable.ofAll(p.getPositivePairs());

						otherSupport.support = BigDecimal.valueOf(pairs.size());

						supportToUse = otherSupport;
					}

					toReport.put("PCAConfidence", p.getPCAConfidence(supportToUse.support));

					// Report absolute error using the exact support.
					toReport.put("AbsoluteError", p.getPCAConfidence(supportToUse.support)
							.subtract(exactConf.getPCAConfidence(support.support)));
				}

				toReport.put("CPU", listener.getTimeElapsed());
				toReport.putAll(metric.getExtraStuff());

				allToReport.add(toReport.toString());

				return null;
			};

			reportResult.apply("SupportExact", exactSupportListener, null);

			System.out.println("\tWorker: " + workerId + "; Computing estimated support of Rule "
					+ toProcess.getRuleId() + " -- " + new Date());

			// Two options: replacement no/yes.
			for (boolean withReplacement : new boolean[] { false, true }) {
				EstimatorMemento memento = new EstimatorMemento();

				EstimatedSupportVisitor visitor = RuleMetricFactory.getEstimatedSupport(queryVisitor, memento,
						new Sampling<>(true, withReplacement, null));

				// Get all listeners.
				Collection<RuleMetricListener> listeners = new ArrayList<>();
				Map<RuleMetricListener, String> listenerNames = new HashMap<>();

				for (StatisticalEstimatorType estimatorType : EstimatorFactory
						.getEstimatorsReplacement(withReplacement))
					for (EstimatorLimit limitType : List.of(EstimatorLimit.ConfidenceInterval, EstimatorLimit.Chernoff))
						for (double accuracy : accuracyValues)
							for (double confidence : confidenceValues) {
								EstimatedRuleMetricListener listener = new EstimatedRuleMetricListener(
										new RuleSupport(queryVisitor.getHeadSize()),
										EstimatorFactory.getStatisticalEstimator(estimatorType, accuracy, confidence,
												limitType, minSamples, memento));

								Supplier<String> getTechnique = () -> {
									return "Support_" + estimatorType + "_" + limitType + "_Acc" + accuracy + "_Conf"
											+ confidence;
								};

								listeners.add(listener);
								listenerNames.put(listener, getTechnique.get());
							}

				System.out.println("\tWorker: " + workerId + "; Computing estimated support using replacement "
						+ withReplacement + " for Rule " + toProcess.getRuleId() + " -- " + new Date());

				for (int i = 0; i < runs; i++) {
					visitor.compute(listeners);

					for (RuleMetricListener l : listeners)
						reportResult.apply(listenerNames.get(l), l, null);
				}
			}

			queryVisitor = null;

			if (support.getHeadCoverage().compareTo(BigDecimal.valueOf(minHC)) >= 0) {
				int x = toProcess.getRule().getEdgeSource(toProcess.getHead()),
						y = toProcess.getRule().getEdgeTarget(toProcess.getHead());
				String corruptX = "x", corruptY = "y";

				for (int corrupt : List.of(x, y)) {
					String corruptStr = corrupt == x ? corruptX : corruptY;
					atomicCorruptStr.set(corruptStr);

					GraphVisitor pcaQueryVisitor = GraphVisitor.getPCAConfVisitor(graphDb, toProcess.getRule(),
							toProcess.getHead(), corrupt, split);

					BigInteger total = BigInteger.valueOf(pcaQueryVisitor.getVariableCandidates(x).size())
							.multiply(BigInteger.valueOf(pcaQueryVisitor.getVariableCandidates(y).size()));

					// We will always use Binomial+CLT with certain accuracy and confidence.
					BinomialEstimator binEstimator = new BinomialEstimator(new EstimatorMemento(), .005, .0005,
							EstimatorLimit.CentralLimitTheorem, minSamples);

					RulePCAConfidence exactConf;

					RuleMetricListener exactPCAListener;
					PCAConfidenceVisitor exactPCAVisitor;
					String pcaComputation;

					if (total.compareTo(binEstimator.getCLTBound()) >= 0) {
						exactPCAListener = new EstimatedRuleMetricListener(exactConf = new RulePCAConfidence(corrupt),
								binEstimator);

						EstimatorMemento cltMemento = new EstimatorMemento();
						exactPCAVisitor = RuleMetricFactory.getEstimatedPCAConfidence(pcaQueryVisitor, cltMemento, x, y,
								corrupt, SampleSelection.Random, new Sampling<>(true, true, null),
								new Sampling<>(true, true, null), false);

						pcaComputation = "approx.";
					} else {
						exactPCAListener = new ExactRuleMetricListener(exactConf = new RulePCAConfidence(corrupt));
						exactPCAVisitor = RuleMetricFactory.getExactPCAConfidence(pcaQueryVisitor, x, y, corrupt);

						pcaComputation = "exact";
					}

					System.out.println("\tWorker: " + workerId + "; Computing " + pcaComputation
							+ " PCA confidence of Rule " + toProcess.getRuleId() + " (total: " + total + "; CLT bound: "
							+ binEstimator.getCLTBound() + ") -- " + new Date());

					exactPCAVisitor.compute(List.of(exactPCAListener));
					reportResult.apply("PCAExact", exactPCAListener, exactConf);

					if (corrupt == x)
						toProcess.setConfidenceX(exactConf);
					if (corrupt == y)
						toProcess.setConfidenceY(exactConf);

					// Choices: sample selection and with/without replacement.
					for (SampleSelection choice : List.of(SampleSelection.Minimum, SampleSelection.Random,
							SampleSelection.NonCorrupt))
						for (boolean withReplacement : new boolean[] { false, true }) {
							EstimatorMemento memento = new EstimatorMemento();

							EstimatedPCAConfidenceVisitor pcaVisitor = RuleMetricFactory.getEstimatedPCAConfidence(
									pcaQueryVisitor, memento, x, y, corrupt, choice,
									new Sampling<>(true, withReplacement, null),
									new Sampling<>(true, withReplacement, null), true);

							// Get all listeners.
							Collection<RuleMetricListener> listeners = new ArrayList<>();
							Map<RuleMetricListener, String> listenerNames = new HashMap<>();

							// AnyBURL can be understood as with replacement.
							if (withReplacement) {
								EstimatedRuleMetricListener listener = new EstimatedRuleMetricListener(
										new RulePCAConfidence(corrupt), EstimatorFactory.getNonstatisticalEstimator(
												NonstatisticalEstimatorType.AnyBURL, memento));

								Supplier<String> getTechnique = () -> {
									return "PCA_AnyBURL_" + "_" + choice;
								};

								listeners.add(listener);
								listenerNames.put(listener, getTechnique.get());
							}

							for (StatisticalEstimatorType estimatorType : EstimatorFactory
									.getEstimatorsReplacement(withReplacement))
								for (EstimatorLimit limitType : List.of(EstimatorLimit.Chernoff,
										EstimatorLimit.ConfidenceInterval))
									for (double accuracy : accuracyValues)
										for (double confidence : confidenceValues) {
											EstimatedRuleMetricListener listener = new EstimatedRuleMetricListener(
													new RulePCAConfidence(corrupt),
													EstimatorFactory.getStatisticalEstimator(estimatorType, accuracy,
															confidence, limitType, minSamples, memento));

											Supplier<String> getTechnique = () -> {
												return "PCA_" + estimatorType + "_" + limitType + "_" + choice + "_Acc"
														+ accuracy + "_Conf" + confidence;
											};

											listeners.add(listener);
											listenerNames.put(listener, getTechnique.get());
										}

							System.out.println(
									"\tWorker: " + workerId + "; Computing estimated PCA conf using sample choice "
											+ choice + " and replacement " + withReplacement + " for Rule "
											+ toProcess.getRuleId() + " -- " + new Date());

							for (int i = 0; i < runs; i++) {
								pcaVisitor.compute(listeners);

								for (RuleMetricListener l : listeners)
									reportResult.apply(listenerNames.get(l), l, exactConf);
							}
						}

					pcaQueryVisitor = null;
				}
			}

			printResultToFile(allToReport, resultsFile);

			System.out.println(
					"\tWorker: " + workerId + "; Done with Rule " + toProcess.getRuleId() + " -- " + new Date());
		};

		// Do nothing!
		params.processDiversity = (list) -> {
		};

		try {
			System.out.println("Rule mining started");

			mining.mine(latticeDb, graphDb, workerIds, params);

			System.out.println("Rule mining is done");
		} finally {
			if (dbService != null)
				dbService.shutdown();
			if (latticeService != null)
				latticeService.shutdown();

			// Always delete!
			FileUtils.deleteDirectory(new File(copyDb));
			// We delete the lattice database as well to reduce space.
			FileUtils.deleteDirectory(new File(lattice));
		}

		System.out.println(new Date() + " -- Done mining rules!");
	}

	private static DatabaseManagementService getService(String path) {
		DatabaseManagementService service = new DatabaseManagementServiceBuilder(Path.of(path))
				.setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
				.setConfig(GraphDatabaseSettings.preallocate_logical_logs, false)
				// .setConfig(GraphDatabaseSettings.read_only_database_default, true)
				.build();

		registerShutdownHook(service);

		return service;
	}

	private static void registerShutdownHook(final DatabaseManagementService service) {
		// Registers a shutdown hook for the Neo4j instance so that it
		// shuts down nicely when the VM exits (even if you "Ctrl-C" the
		// running application).
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				service.shutdown();
			}
		});
	}

}
