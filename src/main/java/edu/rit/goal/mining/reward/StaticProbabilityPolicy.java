package edu.rit.goal.mining.reward;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class StaticProbabilityPolicy extends ProbabilityPolicy {
	
	// This stores how many times each thread has visited its current profile.
	Map<Integer, AtomicInteger> threadCount = new HashMap<>();
	
	// This stores the current profile of each thread.
	Map<Integer, NextProfile> threadProfile = new HashMap<>();
	
	// This stores how many times until switch profiles.
	int switchThreshold;
	
	public StaticProbabilityPolicy(int switchThreshold) {
		super();
		this.switchThreshold = switchThreshold;
	}

	@Override
	public synchronized NextProfile nextProfile(int currentThread) {
		threadCount.putIfAbsent(currentThread, new AtomicInteger());
		
		int currentCount = threadCount.get(currentThread).get();
		if (currentCount == 0 || currentCount == switchThreshold) {
			// Reset.
			threadCount.get(currentThread).set(0);
			
			// Get the next profile.
			threadProfile.put(currentThread, super.nextProfile(currentThread));
		} else
			threadCount.get(currentThread).incrementAndGet();
		
		return threadProfile.get(currentThread);
	}

}
