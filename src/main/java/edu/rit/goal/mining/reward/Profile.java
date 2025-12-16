package edu.rit.goal.mining.reward;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import edu.rit.goal.graph.RandomWalk;

// This defines the way of grouping measurements for rules.
public class Profile {
	public enum Feature {
		Length, Head, NumberOfVariables, NumberOfPredicates
	};

	// These are the features the profile uses.
	private Map<Feature, Object> features = new HashMap<>();

	public void addLength(int l) {
		features.put(Feature.Length, l);
	}

	public void addHead(String head) {
		features.put(Feature.Head, head);
	}

	public void addNumberOfVariables(int number) {
		features.put(Feature.NumberOfVariables, number);
	}

	public void addNumberOfPredicates(int number) {
		features.put(Feature.NumberOfPredicates, number);
	}
	
	public Map<Feature, Object> getFeatures() {
		return features;
	}
	
	public boolean match(Profile other) {
		boolean match = 
			this.features.keySet().containsAll(other.features.keySet()) &&
			other.features.keySet().containsAll(this.features.keySet());
		
		for (Feature f : this.features.keySet())
			match = match && this.features.get(f).equals(other.features.get(f));
		
		return match;
	}

	public void configureRandomWalk(RandomWalk rw, int maxRuleLength) {
		if (features.containsKey(Feature.Length))
			rw.setLength((int) features.get(Feature.Length));
		else
			// At least size 2.
			rw.setLength(2 + ThreadLocalRandom.current().nextInt(maxRuleLength - 2 + 1));

		if (features.containsKey(Feature.Head))
			rw.setHead((String) features.get(Feature.Head));

		if (features.containsKey(Feature.NumberOfVariables))
			rw.setTotalNumberOfNodes((int) features.get(Feature.NumberOfVariables));

		if (features.containsKey(Feature.NumberOfPredicates))
			rw.setTotalNumberOfPredicates((int) features.get(Feature.NumberOfPredicates));
	}

	@Override
	public String toString() {
		return "(" + String.join(", ",
				features.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.toList()))
				+ ")";
	}
}
