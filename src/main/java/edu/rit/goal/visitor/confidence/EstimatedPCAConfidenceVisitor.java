package edu.rit.goal.visitor.confidence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import edu.rit.goal.estimator.Estimator;
import edu.rit.goal.estimator.EstimatorMemento;
import edu.rit.goal.estimator.ProbabilityEstimator;
import edu.rit.goal.estimator.Sampling;
import edu.rit.goal.graph.LabeledEdge;
import edu.rit.goal.metric.RulePCAConfidence;
import edu.rit.goal.visitor.EstimatedRuleMetricListener;
import edu.rit.goal.visitor.GraphVisitor;

public class EstimatedPCAConfidenceVisitor extends PCAConfidenceVisitor {
	private static final int MAX_MATCHING_CALLS = 100000;

	public enum SampleSelection {
		Minimum, Maximum, Random, Corrupt, NonCorrupt
	}

	// These are the sampling methods to use.
	Sampling<Integer> samplingX, samplingY;

	// This indicates whether we are doing beam search.
	boolean beamSearch;

	// This indicates the sampling method.
	SampleSelection choice;

	EstimatorMemento memento;

	public EstimatedPCAConfidenceVisitor(GraphVisitor visitor, EstimatorMemento memento, int x, int y, int corrupt,
			SampleSelection choice, Sampling<Integer> samplingX, Sampling<Integer> samplingY, boolean beamSearch) {
		super(visitor, x, y, corrupt);

		this.memento = memento;
		this.choice = choice;
		this.samplingX = samplingX;
		this.samplingY = samplingY;
		this.beamSearch = beamSearch;
	}

	@SuppressWarnings("unchecked")
	BiFunction<LabeledEdge, Map<Integer, Integer>, MutableList<Entry<Integer, Integer>>> toIterate() {
		return (e, pm) -> {
			MutableList<Entry<Integer, Integer>> it = visitor.toIterate(e, pm);

			// Get random element.
			if (beamSearch && !it.isEmpty()) {
				MutableIntSet visited = IntSets.mutable.empty();
				Set<Integer> values = new HashSet<>(pm.values());

				// A value that is already in the mapping can show up. Let's avoid that.
				int u = visitor.query.getEdgeSource(e), up = visitor.query.getEdgeTarget(e);

				boolean checkU = !pm.containsKey(u), checkUp = !pm.containsKey(up);

				// Keep visiting until finding the one.
				while (visited.size() < it.size()) {
					int idx = ThreadLocalRandom.current().nextInt(it.size());
					Entry<Integer, Integer> selected = it.get(idx);

					if (!visited.contains(idx)) {
						visited.add(idx);

						if (!checkU && !checkUp) {
							return Lists.mutable.of(selected);
						} else if ((checkU && !checkUp) || (!checkU && checkUp)) {
							if (checkU && !values.contains(selected.getKey()))
								return Lists.mutable.of(selected);

							if (checkUp && !values.contains(selected.getValue()))
								return Lists.mutable.of(selected);
						}
					}
				}

				return Lists.mutable.empty();
			}

			return it;
		};
	}

	List<LabeledEdge> xOrder, yOrder, order;

	// Whether x is selected based on the choice; otherwise, select y.
	boolean selectX() {
		int xSize = xCandidates.size(), ySize = yCandidates.size();

		return switch (choice) {
		case Minimum -> xSize <= ySize;
		case Maximum -> xSize > ySize;
		case Random -> ThreadLocalRandom.current().nextDouble() <= .5;
		case Corrupt -> x == corrupt;
		case NonCorrupt -> x != corrupt;
		default -> throw new IllegalArgumentException("Unexpected value: " + choice);
		};
	}

	// Other variable in partial matching.
	AtomicInteger selected = new AtomicInteger(-1), other = new AtomicInteger(-1);

	@Override
	public void visit() {
		while (true) {
			getTime(startingTime);

			// Add to the partial matching.
			Map<Integer, Integer> partialMatching = new HashMap<>();

			other.set(-1);

			Map<Integer, Integer> found = new HashMap<>();

			Predicate<Void> earlyStopping = a -> {
				return !found.isEmpty();
			};

			if (beamSearch) {
				// Choose one variable to sample based on the choice.
				if (selectX()) {
					partialMatching.put(x, samplingX.getNext());

					selected.set(x);
					other.set(y);

					visitor.order = xOrder;
				} else {
					partialMatching.put(y, samplingY.getNext());

					selected.set(y);
					other.set(x);

					visitor.order = yOrder;
				}
			} else {
				partialMatching.put(x, samplingX.getNext());
				partialMatching.put(y, samplingY.getNext());
				visitor.order = order;

				earlyStopping = earlyStopping.or(a -> {
					return visitor.matchingCalls.intValue() >= MAX_MATCHING_CALLS;
				});
			}

			Consumer<Map<Integer, Integer>> matchingFound = matching -> {
				// Make sure it does not exist.
				if (!visitor.isInHeadCandidates(matching.get(x), matching.get(y)))
					found.putAll(matching);
				else
					listeners.stream().forEach(l -> {
						if (!((EstimatedRuleMetricListener) l).stop()) {
							RulePCAConfidence conf = (RulePCAConfidence) ((EstimatedRuleMetricListener) l).getMetric();
							conf.gatherPositive(matching.get(x), matching.get(y));
						}
					});
			};

			if (beamSearch || !visitor.isInHeadCandidates(partialMatching.get(x), partialMatching.get(y)))
				visitor.matching(0, partialMatching, matchingFound, toIterate(), earlyStopping);

			getTime(endingTime);

			this.listeners.forEach(l -> l.addToTimeElapsed(startingTime, endingTime));

			if (!found.isEmpty())
				matchingFound(found);
			else {
				listeners.stream().forEach(l -> {
					getTime(startingTime);
					((EstimatedRuleMetricListener) l).newFailure();
					getTime(endingTime);

					l.addToTimeElapsed(startingTime, endingTime);
				});
			}

			updateCalls();

			boolean stop = stopVisiting();

			if (stop)
				// We are done!
				return;
		}
	}

	@Override
	public void initVisit() {
		getTime(startingTime);

		Set<Integer> groundedVariables = new HashSet<>();

		if (beamSearch) {
			// We will ground either x or y.
			groundedVariables.add(x);
			visitor.getOrder(groundedVariables);
			xOrder = new ArrayList<>(visitor.order);

			groundedVariables.clear();
			groundedVariables.add(y);
			visitor.getOrder(groundedVariables);
			yOrder = new ArrayList<>(visitor.order);
		} else {
			// We will ground both x and y.
			groundedVariables.add(x);
			groundedVariables.add(y);

			visitor.getOrder(groundedVariables);
			order = new ArrayList<>(visitor.order);
		}

		samplingX.init(xCandidates.collect(x -> x));

		samplingY.init(yCandidates.collect(x -> x));

		getTime(endingTime);

		AtomicLong houseKeepingStarting = new AtomicLong(startingTime.get()),
				houseKeepingEnding = new AtomicLong(endingTime.get());

		memento.reset();

		listeners.stream().forEach(l -> {
			l.addToTimeElapsed(houseKeepingStarting, houseKeepingEnding);

			getTime(startingTime);
			((EstimatedRuleMetricListener) l).resetEstimator(total);
			getTime(endingTime);

			l.addToTimeElapsed(startingTime, endingTime);
		});
	}

	void matchingFound(Map<Integer, Integer> matching) {
		getTime(startingTime);

		int s = matching.get(x), o = matching.get(y);

		BigDecimal probability = getProbability(matching, selected.get(), other.get());

		getTime(endingTime);

		memento.newSuccess(s, o, probability);

		AtomicLong probStarting = new AtomicLong(startingTime.get()), probEnding = new AtomicLong(endingTime.get());

		listeners.stream().forEach(l -> {
			if (((EstimatedRuleMetricListener) l).getEstimator().requiresProbability())
				l.addToTimeElapsed(probStarting, probEnding);

			getTime(startingTime);
			((EstimatedRuleMetricListener) l).newSuccess();
			getTime(endingTime);

			l.addToTimeElapsed(startingTime, endingTime);
		});
	}

	public boolean stopVisiting() {
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
	public void endVisit() {
		listeners.stream().forEach(l -> {
			getTime(startingTime);

			String suffix = new String();

			if (corrupt == x)
				suffix = "xp";
			if (corrupt == y)
				suffix = "yp";

			((EstimatedRuleMetricListener) l).setMetric(suffix);

			Estimator est = ((EstimatedRuleMetricListener) l).getEstimator();

			// This adjusts the probability of the probability-based estimators using the
			// selected variable. If we change between x and y, this will be a mess because
			// different adjustments would be necessary, which we don't contemplate. Even
			// though we implement random, it should be always the same (either x or y) to
			// start the beam search and, then, adjust.
			if (est instanceof ProbabilityEstimator) {
				// TODO This is the code we would need to modify if we want to adjust
				// probabilities at the end.
//				Map<Entry<Integer, Integer>, AtomicInteger> pairs = ((ProbabilityEstimator) est).getPairs();
//
//				RulePCAConfidence pcaConf = (RulePCAConfidence) ((EstimatedRuleMetricListener) l).getMetric();
//
//				BigDecimal estimatedTotal = Chao2Estimator.getEstimationPairs(pairs, est.n);
			}

			getTime(endingTime);

			l.addToTimeElapsed(startingTime, endingTime);
		});
	}

}
