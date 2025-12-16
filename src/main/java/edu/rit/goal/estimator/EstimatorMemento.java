package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

public class EstimatorMemento {
	private MutableList<Entry<Integer, Integer>> pairs = Lists.mutable.empty();
	private MutableList<BigDecimal> probabilities = Lists.mutable.empty();
	
	public void reset() {
		pairs.clear();
		probabilities.clear();
		lastPair = null;
	}
	
	private Entry<Integer, Integer> lastPair = null;
	public void newSuccess(int s, int o, BigDecimal probability) {
		Entry<Integer, Integer> pair = Map.entry(s, o);
		
		pairs.add(pair);
		lastPair = pair;
		
		probabilities.add(probability);
	}
	
	public Map<Entry<Integer, Integer>, AtomicInteger> getPairs(BigInteger limit) {
		Map<Entry<Integer, Integer>, AtomicInteger> pairsAsMap = new HashMap<>();
		
		for (int i = 0; i < pairs.size() && i < limit.intValue(); i++) {
			Entry<Integer, Integer> pair = pairs.get(i);
			
			if (!pairsAsMap.containsKey(pair))
				pairsAsMap.put(pair, new AtomicInteger(1));
			else
				pairsAsMap.get(pair).incrementAndGet();
		}
		
		return pairsAsMap;
	}
	
	public boolean isEmpty() {
		return pairs.isEmpty();
	}
	
	public Entry<Integer, Integer> getLastPair() {
		return lastPair;
	}
	
	public ImmutableList<BigDecimal> getProbabilities(BigInteger limit) {
		return Lists.immutable.ofAll(probabilities.subList(0, limit.intValue()));
	}
	
}
