package edu.rit.goal.metric;

import java.util.HashMap;
import java.util.Map;

public abstract class RuleMetric {
	// These are for AnyBURL and ERM. They decide what to put here.
	// There are many options so a POJO is very cumbersome.
	private Map<String, Object> extraStuff = new HashMap<>();
	
	public void addExtraStuff(Map<String, Object> extra) {
		extraStuff.putAll(extra);
	}
	
	public Map<String, Object> getExtraStuff() {
		return extraStuff;
	}
	
	public void reset() {
		extraStuff.clear();
	}
	
}
