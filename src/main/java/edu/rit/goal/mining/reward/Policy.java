package edu.rit.goal.mining.reward;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import edu.rit.goal.metric.Rule;

// This applies a policy: a profile is selected based on reward.
public abstract class Policy {

	// For each profile, a number of measurements to compute reward.
	protected Map<Profile, Reward> profileRewards = Collections.synchronizedMap(new HashMap<>());

	// For each profile, a number of measurements to compute diversity.
	protected Map<Profile, Diversity> profileDiversity = Collections.synchronizedMap(new HashMap<>());

	// For each profile, we store rules already found and random walks that failed.
	protected Map<Profile, AtomicInteger> profileRepetitions = Collections.synchronizedMap(new HashMap<>()),
			profileFailures = Collections.synchronizedMap(new HashMap<>());

	// Probability of making a random selection in the multi-arm bandit problem.
	double epsilon = .1;

	public void setEpsilon(double e) {
		epsilon = e;
	}

	public Collection<Profile> getProfiles() {
		return profileRewards.keySet();
	}

	public void initPolicy(int maxLength, Collection<String> heads, boolean useLength, boolean useUniqueVars,
			boolean useUniquePreds, Class<? extends Reward> clazz) {
		// We are going to create these streams dynamically.
		IntStream mlStream, uvStream, upStream;

		if (useLength)
			mlStream = IntStream.range(2, maxLength + 1);
		else
			mlStream = IntStream.of(-1);

		if (useUniqueVars)
			// A rule contains between two and maxLength+1 variables.
			uvStream = IntStream.range(2, maxLength + 2);
		else
			uvStream = IntStream.of(-1);

		if (useUniquePreds)
			// A rule contains between one and maxLength unique predicates.
			upStream = IntStream.range(1, maxLength + 1);
		else
			upStream = IntStream.of(-1);

		Collection<Integer> mlCol = mlStream.boxed().collect(Collectors.toList()),
				uvCol = uvStream.boxed().collect(Collectors.toList()),
				upCol = upStream.boxed().collect(Collectors.toList());

		if (heads == null)
			heads = List.of("");

		for (String h : heads)
			for (Integer l : mlCol)
				for (Integer uv : uvCol)
					for (Integer up : upCol) {
						Profile prf = new Profile();

						if (h.length() > 0)
							prf.addHead(h);

						if (l != -1)
							prf.addLength(l);

						if (uv != -1)
							prf.addNumberOfVariables(uv);

						// We cannot have more variables than l+1 edges.
						if (uv > l + 1)
							continue;

						if (up != -1)
							prf.addNumberOfPredicates(up);

						// We cannot have more unique predicates than number of edges.
						if (up > l)
							continue;

						try {
							profileRewards.put(prf, (Reward) clazz.getDeclaredConstructor().newInstance());
						} catch (Exception oops) {
							// I hope this never happens...
							oops.printStackTrace();
							System.exit(-1);
						}

						profileDiversity.put(prf, new Diversity());
					}
	}

	public void setWindowSizeInReward(int s) {
		for (Reward r : profileRewards.values()) {
			RuleMetricsReward rr = (RuleMetricsReward) r;
			rr.setWindowSize(s);
		}
	}

	public void setGInReward(double g) {
		for (Reward r : profileRewards.values()) {
			SamplingEffortReward ser = (SamplingEffortReward) r;
			ser.setG(g);
		}
	}

	public void addLengthToReward() {
		for (Reward r : profileRewards.values()) {
			RuleMetricsReward rr = (RuleMetricsReward) r;
			rr.addLength();
		}
	}

	public void addPredicateSizesToReward(Map<String, Integer> predicateSizes) {
		for (Reward r : profileRewards.values()) {
			RuleMetricsReward rr = (RuleMetricsReward) r;
			rr.addPredicateSize(predicateSizes);
		}
	}

	public void addSupportToReward() {
		for (Reward r : profileRewards.values()) {
			RuleMetricsReward rr = (RuleMetricsReward) r;
			rr.addSupport();
		}
	}

	public void addHeadCoverageToReward() {
		for (Reward r : profileRewards.values()) {
			RuleMetricsReward rr = (RuleMetricsReward) r;
			rr.addHeadCoverage();
		}
	}

	public void addConfidenceToReward() {
		for (Reward r : profileRewards.values()) {
			RuleMetricsReward rr = (RuleMetricsReward) r;
			rr.addConfidence();
		}
	}

	// Profile and reward.
	public class NextProfile {
		public Profile prf;
		public BigDecimal reward;
	}

	// This gets the next profile based on the current measurements.
	public abstract NextProfile nextProfile(int currentThread);

	public synchronized Reward getReward(Profile p) {
		return profileRewards.get(p);
	}

	Profile randomSelection() {
		return new ArrayList<>(profileRewards.keySet()).get(ThreadLocalRandom.current().nextInt(profileRewards.size()));
	}

	// The random walk failed.
	public synchronized void reportFailure(Profile p) {
		updateCount(profileFailures, p);

		profileRewards.get(p).reportFailure();

		profileDiversity.get(p).reportFailure();
	}

	// The rule found was already there.
	public synchronized void reportRepeated(Profile p, Rule r) {
		updateCount(profileRepetitions, p);

		profileRewards.get(p).reportRepetetion(r);

		profileDiversity.get(p).reportRepeated(r.getRuleId());
	}

	// The rule found was new.
	public synchronized void reportRule(Profile p, Rule r) {
		updateCount(profileRepetitions, p);

		profileRewards.get(p).reportRule(r);

		profileDiversity.get(p).reportSuccess(r.getRuleId());
	}

	private void updateCount(Map<Profile, AtomicInteger> m, Profile p) {
		if (!m.containsKey(p))
			m.put(p, new AtomicInteger(1));
		else
			m.get(p).incrementAndGet();
	}

	public List<String> printDiversity(double g) {
		List<String> profileList = new ArrayList<>();
		Map<String, Profile> strProfileToProfileMap = new HashMap<>();

		for (Profile prf : profileDiversity.keySet()) {
			profileList.add(prf.toString());
			strProfileToProfileMap.put(prf.toString(), prf);
		}

		Collections.sort(profileList);

		List<String> toRet = new ArrayList<>();

		// Get the pooled sample.
		Diversity pooled = new Diversity();

		for (String prfStr : profileList) {
			Diversity current = profileDiversity.get(strProfileToProfileMap.get(prfStr));

			toRet.add("Profile: " + prfStr + "; Diversity: " + current.getReport(g));

			pooled.combine(current);
		}

		toRet.add("Profile: Pooled; Diversity: " + pooled.getReport(g));

		return toRet;
	}

}
