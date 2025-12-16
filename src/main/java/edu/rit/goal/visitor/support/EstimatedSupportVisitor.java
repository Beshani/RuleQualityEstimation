package edu.rit.goal.visitor.support;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

import edu.rit.goal.estimator.EstimatorMemento;
import edu.rit.goal.estimator.Sampling;
import edu.rit.goal.visitor.EstimatedRuleMetricListener;
import edu.rit.goal.visitor.GraphVisitor;

public class EstimatedSupportVisitor extends SupportVisitor {
	protected EstimatorMemento memento;

	public EstimatedSupportVisitor(GraphVisitor visitor, EstimatorMemento memento, int x, int y,
			Sampling<Entry<Integer, Integer>> sampling) {
		super(visitor, x, y, sampling);
		this.memento = memento;
	}

	@Override
	public void initVisit() {
		memento.reset();

		listeners.stream().forEach(l -> {
			getTime(startingTime);
			((EstimatedRuleMetricListener) l).resetEstimator(BigInteger.valueOf(candidatePairs.size()));
			getTime(endingTime);

			l.addToTimeElapsed(startingTime, endingTime);
		});
	}

	@Override
	public void endVisit() {
		listeners.forEach(l -> {
			getTime(startingTime);
			((EstimatedRuleMetricListener) l).setMetric("");
			getTime(endingTime);

			l.addToTimeElapsed(startingTime, endingTime);

		});
	}

	@Override
	public void newSuccess(Map<Integer, Integer> matching) {
		getTime(startingTime);

		// New success.
		int s = matching.get(x), o = matching.get(y);

		BigDecimal probability = getProbability(matching, null, null);

		getTime(endingTime);

		AtomicLong probStarting = new AtomicLong(startingTime.get()), probEnding = new AtomicLong(endingTime.get());

		// New success!
		memento.newSuccess(s, o, probability);

		listeners.stream().forEach(l -> {
			if (((EstimatedRuleMetricListener) l).getEstimator().requiresProbability())
				l.addToTimeElapsed(probStarting, probEnding);

			getTime(startingTime);
			((EstimatedRuleMetricListener) l).newSuccess();
			getTime(endingTime);

			l.addToTimeElapsed(startingTime, endingTime);
		});
	}

	@Override
	public boolean stop() {
		// All estimators are within limit, then stop.
		return listeners.stream().filter(l -> {
			getTime(startingTime);
			boolean stop = ((EstimatedRuleMetricListener) l).stop();
			getTime(endingTime);

			l.addToTimeElapsed(startingTime, endingTime);

			return !stop;
		}).count() == 0;
	}

	@Override
	public void newFailure() {
		listeners.stream().forEach(l -> {
			getTime(startingTime);
			((EstimatedRuleMetricListener) l).newFailure();
			getTime(endingTime);

			l.addToTimeElapsed(startingTime, endingTime);
		});
	}
}
