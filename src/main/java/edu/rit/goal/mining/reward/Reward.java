package edu.rit.goal.mining.reward;

import java.math.BigDecimal;

import edu.rit.goal.metric.Rule;

// This defines the measurements and reward function.
public abstract class Reward {
	public abstract BigDecimal getReward();
	
	public abstract void reportFailure();
	
	public abstract void reportRepetetion(Rule r);
	
	public abstract void reportRule(Rule r);
}
