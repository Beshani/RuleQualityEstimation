package edu.rit.goal.mining.reward;

import java.math.BigDecimal;

import edu.rit.goal.metric.Rule;

public class SamplingEffortReward extends Reward {
	// TODO Is this thread-safe?
	Diversity diversity = new Diversity();
	double g = .95;
	
	public void setG(double g) {
		this.g = g;
	}

	@Override
	public synchronized BigDecimal getReward() {
		return diversity.getSamplingEffort(g);
	}

	@Override
	public synchronized void reportFailure() {
		diversity.reportFailure();
	}

	@Override
	public synchronized void reportRepetetion(Rule r) {
		diversity.reportRepeated(r.getRuleId());
	}

	@Override
	public synchronized void reportRule(Rule r) {
		diversity.reportSuccess(r.getRuleId());
	}

}
