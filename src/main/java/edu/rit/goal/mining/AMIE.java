package edu.rit.goal.mining;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jgrapht.graph.DirectedMultigraph;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;

import edu.rit.goal.estimator.BinomialEstimator;
import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;
import edu.rit.goal.estimator.EstimatorMemento;
import edu.rit.goal.estimator.Sampling;
import edu.rit.goal.graph.GraphDatabase;
import edu.rit.goal.graph.LabeledEdge;
import edu.rit.goal.graph.LanguageBias;
import edu.rit.goal.graph.LatticeGraph;
import edu.rit.goal.graph.signature.GraphSignature;
import edu.rit.goal.graph.signature.GraphSignature.SignatureResult;
import edu.rit.goal.metric.Rule;
import edu.rit.goal.metric.RulePCAConfidence;
import edu.rit.goal.metric.RuleSupport;
import edu.rit.goal.visitor.EstimatedRuleMetricListener;
import edu.rit.goal.visitor.ExactRuleMetricListener;
import edu.rit.goal.visitor.GraphVisitor;
import edu.rit.goal.visitor.RuleMetricFactory;
import edu.rit.goal.visitor.RuleMetricListener;
import edu.rit.goal.visitor.confidence.EstimatedPCAConfidenceVisitor.SampleSelection;
import edu.rit.goal.visitor.confidence.PCAConfidenceVisitor;
import edu.rit.goal.visitor.support.ExactSupportVisitor;

public class AMIE {
	// Hyperparameters.
	public int maxRuleLength = 3;
	public double minHeadCoverage = 0.1;

	private static AMIE instance;

	// Two language biases: one in general and one to compute metrics.
	// 1) We only allow a single dangling edge. Multiple dangling edges will never
	// generate closed rules.
	// 2) Metrics are only computed for closed rules.
	private List<Predicate<DirectedMultigraph<Integer, LabeledEdge>>> languageBias = new ArrayList<>(),
			metricsLanguageBias = new ArrayList<>();

	private AMIE() {
		languageBias.add(LanguageBias.getIsConnected());

		metricsLanguageBias.add(LanguageBias.getIsClosed());
	}

	public static AMIE getInstance() {
		if (instance == null)
			synchronized (AMIE.class) {
				if (instance == null)
					instance = new AMIE();
			}

		return instance;
	}

	public static LabeledEdge getHead(DirectedMultigraph<Integer, LabeledEdge> g) {
		return g.edgeSet().stream().filter(e -> e.pid == 0).findFirst().get();
	}

	public void grow(LatticeGraph lattice, DirectedMultigraph<Integer, LabeledEdge> rule, int u, int v, String p) {
		// The predicate was not there. Add it!
		DirectedMultigraph<Integer, LabeledEdge> grown = LanguageBias.cloneSubgraph(rule);

		for (int x : List.of(u, v))
			if (!grown.containsVertex(x))
				grown.addVertex(x);

		grown.addEdge(u, v, new LabeledEdge(p, rule.edgeSet().size()));

		LabeledEdge newHead = getHead(grown);

		// Skip if it does not fulfill the language bias.
		if (!LanguageBias.check(grown, languageBias))
			return;

		// Only add if the grown rule is minimal.
		SignatureResult signature = GraphSignature.getSignature(grown, newHead);

		if (isMinimal(signature))
			synchronized (AMIE.class) {
				Integer id = lattice.findRule(grown, newHead);

				// If it does not exist, add to the lattice.
				if (id == null) {
					id = lattice.addToLattice(grown, newHead);
					lattice.addPending(id);
				}
			}
	}

	public static boolean isMinimal(SignatureResult sig) {
		// It is minimal if there is no permutation, i.e., the key-value pairs
		// in the mapping contain the same values.
		return sig.mapping.entrySet().stream().filter(e -> e.getKey() != e.getValue()).count() == 0;
	}

	public void mine(String latticeFolder, String dbFolder, int nWorkers, Consumer<Rule> report) {
		Integer split = null;

		DatabaseManagementService latticeService = new DatabaseManagementServiceBuilder(Path.of(latticeFolder))
				.setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
				.setConfig(GraphDatabaseSettings.preallocate_logical_logs, false).build();
		registerShutdownHook(latticeService);

		LatticeGraph lattice = new LatticeGraph(latticeService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME));

		// Read only!
		DatabaseManagementService dbService = new DatabaseManagementServiceBuilder(Path.of(dbFolder))
				.setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
				.setConfig(GraphDatabaseSettings.preallocate_logical_logs, false)
				// .setConfig(GraphDatabaseSettings.read_only_database_default, true)
				.build();
		registerShutdownHook(dbService);

		GraphDatabase db = new GraphDatabase(dbService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME), null);

		if (lattice.isEmpty()) {
			System.out.println(new Date() + " -- Initializing: creating L2...");

			// Let's create Level 2.
			for (String p1 : db.getPredicates())
				for (String p2 : db.getPredicates()) {
					List<DirectedMultigraph<Integer, LabeledEdge>> graphsToAdd = new ArrayList<>();

					// Avoid isomorphs.
					if (p1.compareTo(p2) > 0) {
						DirectedMultigraph<Integer, LabeledEdge> g = new DirectedMultigraph<>(LabeledEdge.class);

						g.addVertex(0);
						g.addVertex(1);
						g.addEdge(0, 1, new LabeledEdge(p1, 0));
						g.addEdge(0, 1, new LabeledEdge(p2, 1));

						graphsToAdd.add(g);
					}

					// Avoid isomorphs.
					if (p1.compareTo(p2) >= 0) {
						DirectedMultigraph<Integer, LabeledEdge> g = new DirectedMultigraph<>(LabeledEdge.class);

						g.addVertex(0);
						g.addVertex(1);
						g.addEdge(0, 1, new LabeledEdge(p1, 0));
						g.addEdge(1, 0, new LabeledEdge(p2, 1));

						graphsToAdd.add(g);
					}

					// Avoid isomorphs.
					if (p1.compareTo(p2) >= 0) {
						DirectedMultigraph<Integer, LabeledEdge> g = new DirectedMultigraph<>(LabeledEdge.class);

						g.addVertex(0);
						g.addVertex(1);
						g.addVertex(2);
						g.addEdge(0, 1, new LabeledEdge(p1, 0));
						g.addEdge(0, 2, new LabeledEdge(p2, 1));

						graphsToAdd.add(g);
					}

					// Avoid isomorphs.
					if (p1.compareTo(p2) >= 0) {
						DirectedMultigraph<Integer, LabeledEdge> g = new DirectedMultigraph<>(LabeledEdge.class);

						g.addVertex(0);
						g.addVertex(1);
						g.addVertex(2);
						g.addEdge(0, 1, new LabeledEdge(p1, 0));
						g.addEdge(1, 2, new LabeledEdge(p2, 1));

						graphsToAdd.add(g);
					}

					// Avoid isomorphs.
					if (p1.compareTo(p2) >= 0) {
						DirectedMultigraph<Integer, LabeledEdge> g = new DirectedMultigraph<>(LabeledEdge.class);

						g.addVertex(0);
						g.addVertex(1);
						g.addVertex(2);
						g.addEdge(1, 0, new LabeledEdge(p1, 0));
						g.addEdge(2, 0, new LabeledEdge(p2, 1));

						graphsToAdd.add(g);
					}

					// All these should not be in the lattice.
					for (DirectedMultigraph<Integer, LabeledEdge> g : graphsToAdd) {
						int id = lattice.addToLattice(g, getHead(g));
						lattice.addPending(id);
					}
				}

			System.out.println(new Date() + " -- L2 is created!");
		} else {
			System.out.println(new Date() + " -- Resuming...");
			lattice.resumeProcessing();
			System.out.println(new Date() + " -- Resume done!");
		}

		// Create the workers.
		Thread[] workers = new Thread[nWorkers];
		for (int i = 0; i < nWorkers; i++) {
			workers[i] = new Thread(new Runnable() {
				Integer workerId = null;

				// https://stackoverflow.com/questions/362424/accessing-constructor-of-an-anonymous-class
				Runnable initialize(int workerId) {
					this.workerId = workerId;
					return this;
				}

				@Override
				public void run() {
					int step = 0;

					// Keep running until no more pending.
					while (true) {
						step++;

						Integer next = lattice.nextPending();

						if (next == null)
							// We assume we are done; it may not be the case if others are working on it.
							break;

						System.out.println(new Date() + " -- Worker: " + workerId + " processing rule " + next);

						// Get graph from lattice.
						DirectedMultigraph<Integer, LabeledEdge> rule = lattice.getRule(next);
						LabeledEdge head = getHead(rule);

						boolean pruned = false;

						// Compute metrics. Only if it fulfills the language bias.
						if (LanguageBias.check(rule, metricsLanguageBias)) {
							System.out.println(
									new Date() + " -- Worker: " + workerId + " computing metrics for rule " + next);

							GraphVisitor queryVisitor = new GraphVisitor(db, rule, head, split);

							ExactSupportVisitor exactSupportVisitor = RuleMetricFactory.getExactSupport(queryVisitor);

							RuleMetricListener exactSupportListener = new ExactRuleMetricListener(
									new RuleSupport(queryVisitor.getHeadSize()));
							exactSupportVisitor.compute(List.of(exactSupportListener));

							queryVisitor = null;

							RuleSupport support = (RuleSupport) exactSupportListener.getMetric();
							List<RulePCAConfidence> confidenceMeasures = new ArrayList<>();
							// This helps us determine whether we computed the confidence using CLT.
							Map<RulePCAConfidence, String> confType = new HashMap<>();

							if (support.getHeadCoverage().compareTo(BigDecimal.valueOf(minHeadCoverage)) >= 0) {
								// Compute PCA confidence only if it does not create a disconnected query!
								if (LanguageBias.getNoDanglingPCAConf().test(rule, head)) {
									int x = rule.getEdgeSource(head), y = rule.getEdgeTarget(head);

									for (int corrupt : List.of(x, y)) {
										GraphVisitor pcaQueryVisitor = GraphVisitor.getPCAConfVisitor(db, rule, head,
												corrupt, split);

										BigInteger total = BigInteger
												.valueOf(pcaQueryVisitor.getVariableCandidates(x).size())
												.multiply(BigInteger
														.valueOf(pcaQueryVisitor.getVariableCandidates(y).size()));

										// We will use Binomial+CLT with accuracy=0.001 and confidence=0.0001.
										BinomialEstimator binEstimator = new BinomialEstimator(new EstimatorMemento(),
												.001, .0001, EstimatorLimit.CentralLimitTheorem, 50);

										RuleMetricListener exactPCAListener;
										PCAConfidenceVisitor exactPCAVisitor;

										RulePCAConfidence conf = new RulePCAConfidence(corrupt);
										confidenceMeasures.add(conf);

										if (total.compareTo(binEstimator.getCLTBound()) >= 0) {
											exactPCAListener = new EstimatedRuleMetricListener(conf, binEstimator);

											EstimatorMemento cltMemento = new EstimatorMemento();
											exactPCAVisitor = RuleMetricFactory.getEstimatedPCAConfidence(
													pcaQueryVisitor, cltMemento, x, y, corrupt, SampleSelection.Random,
													new Sampling<>(true, true, null), new Sampling<>(true, true, null),
													false);

											confType.put(conf, "approx.");
										} else {
											exactPCAListener = new ExactRuleMetricListener(conf);
											exactPCAVisitor = RuleMetricFactory.getExactPCAConfidence(pcaQueryVisitor,
													x, y, corrupt);

											confType.put(conf, "exact");
										}

										exactPCAVisitor.compute(List.of(exactPCAListener));

										pcaQueryVisitor = null;
									}
								}
							} else
								pruned = true;

							System.out.println(
									new Date() + " -- Worker: " + workerId + " metrics computed for rule " + next);

							Rule toReport = new Rule(next, head, rule);

							toReport.setPruned(pruned);

							toReport.setSupport(support);

							if (!confidenceMeasures.isEmpty()) {
								toReport.setConfidenceX(confidenceMeasures.get(0));
								toReport.setConfidenceXType(confType.get(confidenceMeasures.get(0)));

								toReport.setConfidenceY(confidenceMeasures.get(1));
								toReport.setConfidenceYType(confType.get(confidenceMeasures.get(1)));
							}

							report.accept(toReport);
						}

						// Grow graph.
						if (!pruned && rule.edgeSet().size() < maxRuleLength) {
							System.out.println(new Date() + " -- Worker: " + workerId + " growing rule " + next);

							// Add closing atoms.
							for (int u : rule.vertexSet())
								for (int v : rule.vertexSet())
									// No self loops!
									if (u != v) {
										Set<String> uvPredicates = rule.getAllEdges(u, v).stream().map(e -> e.predicate)
												.collect(Collectors.toSet());

										for (String p : db.getPredicates())
											if (!uvPredicates.contains(p))
												grow(lattice, rule, u, v, p);
									}

							// Add dangling atoms.
							int v = rule.vertexSet().size();

							for (int u : rule.vertexSet())
								for (String p : db.getPredicates())
									// Incoming and outgoing.
									for (Entry<Integer, Integer> entry : Map.of(u, v, v, u).entrySet())
										grow(lattice, rule, entry.getKey(), entry.getValue(), p);

							System.out.println(
									new Date() + " -- Worker: " + workerId + " growing is done for rule " + next);
						}

						// We are done!
						lattice.markAsCompleted(next);

						// Print pending from time to time.
						if (step % 50 == 0)
							System.out.println(new Date() + " -- Worker: " + workerId + " checks pending: "
									+ lattice.countPending());
					}
				}
			}.initialize(i));
			workers[i].start();
		}

		// Keep going until all are done.
		while (Arrays.stream(workers).anyMatch(w -> w.isAlive())) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException oops) {
				oops.printStackTrace(System.err);
			}
		}

		// We are done!
		dbService.shutdown();
		latticeService.shutdown();
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
