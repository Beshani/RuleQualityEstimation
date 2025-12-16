package edu.rit.goal.mining;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.commons.lang3.function.TriConsumer;
import org.jgrapht.graph.DirectedMultigraph;

import edu.rit.goal.graph.GraphDatabase;
import edu.rit.goal.graph.LabeledEdge;
import edu.rit.goal.graph.LanguageBias;
import edu.rit.goal.graph.LatticeGraph;
import edu.rit.goal.graph.RandomWalk;
import edu.rit.goal.graph.RandomWalk.Walk;
import edu.rit.goal.metric.Rule;
import edu.rit.goal.mining.reward.Policy;
import edu.rit.goal.mining.reward.Policy.NextProfile;
import edu.rit.goal.mining.reward.Profile;

public class RWMining {

	public class RWMiningHyperparameters {
		// The number of random walks each thread would inspect.
		public int walkBudget;

		// This will be the function to process a rule.
		public TriConsumer<Rule, Integer, Integer> processRule;

		// This is the policy, including profiles and reward, to apply.
		public Policy policy;

		// This is whether we force closing during random walks.
		public boolean forceClosing;

		// This is the file to print diversity information.
		public Consumer<List<String>> processDiversity;

		// This is the desired completeness.
		public double g;

		// This is the split.
		public Integer split;

		// This is the max rule length. This is regardless of the profile.
		public int maxRuleLength;

		// This indicates whether we just use the first predicate as head or we use all.
		public boolean onlyHead;
	}

	private static RWMining instance;

	private List<Predicate<DirectedMultigraph<Integer, LabeledEdge>>> languageBias = new ArrayList<>();

	private RWMining() {
		languageBias.add(LanguageBias.getIsConnected());
		languageBias.add(LanguageBias.getIsClosed());
	}

	public static RWMining getInstance() {
		if (instance == null)
			synchronized (RWMining.class) {
				if (instance == null)
					instance = new RWMining();
			}

		return instance;
	}

	public void mine(LatticeGraph lattice, GraphDatabase db, List<Integer> workerIds, RWMiningHyperparameters params) {
		if (lattice.isEmpty())
			lattice.init();

		// We will not resume policy.

		// Create the workers.
		Thread[] workers = new Thread[workerIds.size()];
		for (int i = 0; i < workerIds.size(); i++) {
			workers[i] = new Thread(new Runnable() {
				Integer workerId = null;

				// https://stackoverflow.com/questions/362424/accessing-constructor-of-an-anonymous-class
				Runnable initialize(int workerId) {
					this.workerId = workerId;
					return this;
				}

				@Override
				public void run() {
					System.out.println(new Date() + " -- Worker: " + workerId + " has started!");

					// There will be no resume!
					int step = 0;

					RandomWalk rw = new RandomWalk(db);
					rw.setForceClosing(params.forceClosing);
					rw.setSplit(params.split);

					while (step < params.walkBudget) {
						step++;

						// Get next profile based on reward.
						Profile prf = null;
						BigDecimal rewardValue = null;

						synchronized (RWMining.class) {
							NextProfile nextPrf = params.policy.nextProfile(this.workerId);

							prf = nextPrf.prf;
							rewardValue = nextPrf.reward;
						}

						System.out.println(new Date() + " -- Worker: " + workerId + ", step: " + step
								+ ", profile selected: " + prf + " with reward " + rewardValue);

						// Configure the walk based on the selected profile.
						prf.configureRandomWalk(rw, params.maxRuleLength);

						// And let's walk the walk.
						Walk rwRet = rw.randomWalk();

						DirectedMultigraph<Integer, LabeledEdge> walk = rwRet.walk;
						Set<LabeledEdge> heads = new HashSet<>();
						
						if (rwRet.head != null)
							heads.add(rwRet.head);
						
						if (!params.onlyHead)
							heads.addAll(walk.edgeSet());

						for (LabeledEdge head : heads) {
							// This is the rule to found (if any).
							Rule r = null;

							// These are the conditions to update the policy.
							boolean reportRule = false, reportFailure = false, reportRepeated = false;

							// We keep the walk only if it fulfills the language bias. We will only compute
							// scores if the rule was not discovered before. Avoid those that the PCA
							// confidence query is disconnected.
							if (!walk.edgeSet().isEmpty() && LanguageBias.check(walk, languageBias)
									&& LanguageBias.getNoDanglingPCAConf().test(walk, head)) {
								Integer ruleid = null;
								boolean process = false;

								synchronized (RWMining.class) {
									ruleid = lattice.findRule(walk, head);

									// If it does not exist, add to the lattice.
									if (ruleid == null) {
										ruleid = lattice.addToLattice(walk, head);
										process = true;
									}
								}

								r = new Rule(ruleid, head, walk);

								// This thread will process this rule.
								if (process) {
									processRule(workerId, r, step, lattice, params.processRule);

//									if (!r.isPruned())
//										reportRule = true;
//									else
//										reportFailure = true;

									// AnyBURL never prunes the rule, so the comparison is difficult. If the rule is
									// pruned, it will be still reported. Essentially, this is counting the number
									// of unique subgraph found.
									reportRule = true;
								} else
									reportRepeated = true;
							} else
								reportFailure = true;

							if (reportRule || reportFailure || reportRepeated) {
								synchronized (RWMining.class) {
									if (reportRule)
										// Report success.
										params.policy.reportRule(prf, r);

									if (reportFailure)
										// Report failure.
										params.policy.reportFailure(prf);

									if (reportRepeated)
										// Report repeated.
										params.policy.reportRepeated(prf, r);
								}

								System.out.println(new Date() + " -- Worker: " + workerId + ", step: " + step
										+ ", profile selected: " + prf + " reported rule: " + reportRule + ", failure: "
										+ reportFailure + ", repeated: " + reportRepeated);
							} else
								System.out.println("\t\t\tWorker: " + workerId + " had nothing to report. Weird!");
						}

						// Do this only for one worker.
						if (workerId == 0 && (step % 50 == 0 || step == params.walkBudget))
							params.processDiversity.accept(params.policy.printDiversity(params.g));
					}
				}
			}.initialize(workerIds.get(i)));
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

		// Final diversity info.
		params.processDiversity.accept(params.policy.printDiversity(params.g));
	}

	private void processRule(int workerId, Rule r, int step, LatticeGraph lattice,
			TriConsumer<Rule, Integer, Integer> processRule) {
		System.out.println(new Date() + " -- Worker: " + workerId + " is processing rule: " + r.getRuleId());

		// Update pending.
		lattice.addPending(workerId, r.getRuleId());

		processRule.accept(r, step, workerId);

		// Update pending.
		lattice.releasePending(workerId);

		System.out.println(new Date() + " -- Worker: " + workerId + " is done processing rule: " + r.getRuleId());
	}
}
