package edu.rit.goal.visitor.support;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.eclipse.collections.api.list.MutableList;

import edu.rit.goal.estimator.Sampling;
import edu.rit.goal.visitor.GraphVisitor;
import edu.rit.goal.visitor.RuleMetricListener;
import edu.rit.goal.visitor.RuleMetricVisitor;

public abstract class SupportVisitor extends RuleMetricVisitor {
	Sampling<Entry<Integer, Integer>> sampling;

	public SupportVisitor(GraphVisitor visitor, int x, int y, Sampling<Entry<Integer, Integer>> sampling) {
		super(visitor, x, y);

		this.sampling = sampling;
	}

	MutableList<Entry<Integer, Integer>> candidatePairs;
	int totalVisited = 0;

	Collection<RuleMetricListener> listeners;

	// Sometimes, we know a pair that worked because of a random walk.
	Map<Integer, Integer> previousMatching;

	public void compute(Collection<RuleMetricListener> listeners, Map<Integer, Integer> matching) {
		this.listeners = listeners;
		this.previousMatching = matching;

		// We are starting!
		this.listeners.forEach(l -> l.reset());

		{
			// Pre-visit.
			getTime(startingTime);

			visitor.getOrder(Set.of(x, y));

			candidatePairs = visitor.getDb().getCandidatesAsList(visitor.head.predicate);

			getTime(endingTime);

			this.listeners.forEach(l -> l.addToTimeElapsed(startingTime, endingTime));
		}

		initVisit();

		visit();

		endVisit();
	}

	public void compute(Collection<RuleMetricListener> listeners) {
		compute(listeners, null);
	}

	private void visit() {
		getTime(startingTime);

		sampling.init(candidatePairs.collect(x -> x));

		if (previousMatching != null)
			newSuccess(previousMatching);

		getTime(endingTime);

		this.listeners.forEach(l -> l.addToTimeElapsed(startingTime, endingTime));

		while (true) {
			getTime(startingTime);

			totalVisited++;

			Entry<Integer, Integer> pair = sampling.getNext();

			int s = pair.getKey(), o = pair.getValue();

			if (!visitor.isInHeadCandidates(s, o))
				continue;

			// Add s and o to the partial matching.
			Map<Integer, Integer> partialMatching = new HashMap<>();

			partialMatching.put(x, s);
			partialMatching.put(y, o);

			AtomicInteger oneFound = new AtomicInteger();
			Map<Integer, Integer> matching = new HashMap<>();
			Consumer<Map<Integer, Integer>> matchingFound = m -> {
				oneFound.incrementAndGet();
				matching.putAll(m);
			};

			Predicate<Void> stopping = z -> {
				return oneFound.get() > 0;
			};

			visitor.matching(0, partialMatching, matchingFound, visitor::toIterate, stopping);

			getTime(endingTime);

			this.listeners.forEach(l -> l.addToTimeElapsed(startingTime, endingTime));

			listeners.forEach(l -> {
				l.updateCalls(visitor.matchingCalls.get());
			});

			if (oneFound.get() == 0)
				newFailure();
			else
				newSuccess(matching);

			boolean stop = stop();

			if (stop)
				// We are done!
				return;
		}
	}

	public abstract void initVisit();

	public abstract void endVisit();

	public abstract void newSuccess(Map<Integer, Integer> matching);

	public abstract boolean stop();

	public abstract void newFailure();

}
