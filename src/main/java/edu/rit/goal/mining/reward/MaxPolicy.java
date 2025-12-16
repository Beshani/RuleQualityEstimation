package edu.rit.goal.mining.reward;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

public class MaxPolicy extends Policy {

	@Override
	public synchronized NextProfile nextProfile(int currentThread) {
		// We don't care about the thread.
		
		Profile prf = null;
		BigDecimal reward = null;

		if (ThreadLocalRandom.current().nextDouble() <= epsilon)
			// Just random selection.
			prf = randomSelection();
		else {
			// Let's find the max! If there are ties, random selection among them.
			BigDecimal max = BigDecimal.ZERO;
			List<Profile> ties = new ArrayList<>();

			for (Entry<Profile, Reward> e : profileRewards.entrySet()) {
				BigDecimal r = e.getValue().getReward();

				if (r.compareTo(max) >= 0) {
					ties.add(e.getKey());
					max = r;
				}
			}

			prf = ties.get(ThreadLocalRandom.current().nextInt(ties.size()));
			reward = max;
		}
		
		NextProfile ret = new NextProfile();
		
		ret.prf = prf;
		ret.reward = reward;

		return ret;
	}

}
