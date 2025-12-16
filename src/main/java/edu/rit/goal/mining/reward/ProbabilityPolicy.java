package edu.rit.goal.mining.reward;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ProbabilityPolicy extends Policy {

	@Override
	public synchronized NextProfile nextProfile(int currentThread) {
		// We don't care about the thread.
		
		Profile prf = null;
		BigDecimal reward = null;

		if (ThreadLocalRandom.current().nextDouble() <= epsilon)
			// Just random selection.
			prf = randomSelection();
		else {
			// Let's compute the probabilities of each profile.
			BigDecimal total = BigDecimal.ZERO;
			Map<Profile, BigDecimal> probabilities = new HashMap<>(), rewards = new HashMap<>();

			for (Profile p : profileRewards.keySet()) {
				BigDecimal r = profileRewards.get(p).getReward();
				
				// Adjusting zero reward values.
				if (r.compareTo(BigDecimal.ZERO) == 0)
					r = BigDecimal.valueOf(.0001);

				probabilities.put(p, r);
				rewards.put(p, r);

				total = total.add(r);
			}

			// Adjust probabilities.
			if (total.compareTo(BigDecimal.ZERO) > 0)
				for (Profile p : probabilities.keySet())
					probabilities.replace(p, probabilities.get(p).divide(total, MathContext.DECIMAL128));

			// Example: assume five profiles with the following probabilities:
			// prf1: .30, prf2: .25, prf3: .20, prf4: .15, prf5: .10.
			// We toss a coin between 0 and 1. If <= .30, prf1 is selected.
			// If <= .55, prf2 is selected. If <= .75, prf3 is selected. And so on.

			// Sort profiles by probability, largest first.
			List<Profile> sortedProfiles = new ArrayList<>(probabilities.keySet());

			Collections.sort(sortedProfiles,
					Comparator.comparingDouble(x -> probabilities.get(x).doubleValue()).reversed());

			// Select a profile for the given probability.
			double prob = ThreadLocalRandom.current().nextDouble(), accumulatedProb = 0;
			for (int i = 0; prf == null && i < sortedProfiles.size(); i++) {
				Profile p = sortedProfiles.get(i);
				accumulatedProb += probabilities.get(p).doubleValue();

				// Found!
				if (prob <= accumulatedProb) {
					prf = p;
					reward = rewards.get(prf);
				}
			}
		}

		NextProfile ret = new NextProfile();

		ret.prf = prf;
		ret.reward = reward;

		return ret;
	}

}
