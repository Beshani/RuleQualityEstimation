package edu.rit.goal.visitor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.commons.collections4.SetUtils;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.jgrapht.graph.DirectedMultigraph;

import edu.rit.goal.graph.GraphDatabase;
import edu.rit.goal.graph.LabeledEdge;
import edu.rit.goal.graph.LanguageBias;

public class GraphVisitor {

	@SuppressWarnings("unchecked")
	public MutableList<Entry<Integer, Integer>> toIterate(LabeledEdge e, Map<Integer, Integer> pm) {
		MutableList<Entry<Integer, Integer>> ret = Lists.mutable.empty();

		int u = query.getEdgeSource(e), up = query.getEdgeTarget(e);
		String p = e.predicate;

		if (!pm.containsKey(u) && !pm.containsKey(up))
			ret = db.getCandidatesAsList(p);
		else if (pm.containsKey(u) && !pm.containsKey(up)) {
			MutableList<Entry<Integer, Integer>> list = db.getCandidatesBySubjectAsList(p, pm.get(u));

			if (list != null)
				ret = list;
		} else if (!pm.containsKey(u) && pm.containsKey(up)) {
			MutableList<Entry<Integer, Integer>> list = db.getCandidatesByObjectAsList(p, pm.get(up));

			if (list != null)
				ret = list;
		} else {
			Entry<Integer, Integer> pair = Map.entry(pm.get(u), pm.get(up));

			MutableSet<Entry<Integer, Integer>> subjectPairs = db.getCandidatesBySubjectAsSet(p, pm.get(u)),
					objectPairs = db.getCandidatesByObjectAsSet(p, pm.get(up));

			if (subjectPairs != null && subjectPairs.contains(pair) && objectPairs != null
					&& objectPairs.contains(pair))
				ret = Lists.mutable.of(pair);
		}

		return ret;
	}

	// This computes the best probability (greedily).
	public BigDecimal getProbability(Map<Integer, Integer> matching, Set<Integer> assuming) {
		// Choose next edge greedily.
		List<BigDecimal> probabilities = new ArrayList<>();
		Set<LabeledEdge> pending = new HashSet<>(query.edgeSet());
		List<LabeledEdge> selected = new ArrayList<>();

		// Precompute local edge sizes.
		Map<LabeledEdge, Integer> localEdgeSizes = new HashMap<>();
		for (LabeledEdge e : pending) {
			int src = query.getEdgeSource(e), tgt = query.getEdgeTarget(e);
			
			if (assuming.contains(src) && assuming.contains(tgt))
				localEdgeSizes.put(e, 1);
			else if (assuming.contains(src) && !assuming.contains(tgt))
				localEdgeSizes.put(e, db.getCandidatesBySubjectAsSet(e.predicate, matching.get(src)).size());
			else if (!assuming.contains(src) && assuming.contains(tgt))
				localEdgeSizes.put(e, db.getCandidatesByObjectAsSet(e.predicate, matching.get(tgt)).size());
			else {
				// First: take the edge size
				AtomicInteger x = new AtomicInteger(edgeSizes.get(e));

				// Second: get source.
				AtomicInteger y = new AtomicInteger(variableSizes.get(src));
				y.set(y.get() * db.getCandidatesBySubjectAsSet(e.predicate, matching.get(src)).size());

				// Third: get target.
				AtomicInteger z = new AtomicInteger(variableSizes.get(tgt));
				z.set(z.get() * db.getCandidatesByObjectAsSet(e.predicate, matching.get(tgt)).size());

				localEdgeSizes.put(e, Math.min(x.get(), Math.min(y.get(), z.get())));
			}			
		}

		Map<Integer, Integer> partialMatching = new HashMap<>();
		while (!pending.isEmpty()) {
			LabeledEdge next = null;
			int cost = Integer.MAX_VALUE;

			for (LabeledEdge e : pending) {
				int eCost = -1;

				int src = query.getEdgeSource(e), tgt = query.getEdgeTarget(e);

				if (!partialMatching.containsKey(src) && !partialMatching.containsKey(tgt))
					eCost = localEdgeSizes.get(e);
				else
					eCost = toIterate(e, partialMatching).size();

				if (eCost < cost) {
					next = e;
					cost = eCost;
				}
			}

			// We have a winner!
			probabilities.add(new BigDecimal(cost));
			pending.remove(next);
			selected.add(next);

			// Add to partial matching.
			for (Integer u : List.of(query.getEdgeSource(next), query.getEdgeTarget(next)))
				partialMatching.put(u, matching.get(u));
		}

		return probabilities.stream().reduce(BigDecimal.ONE, (a, b) -> a.multiply(b));
	}

	public MutableIntSet getVariableCandidates(Integer x) {
		return getVariableCandidates(x, Map.of());
	}

	public MutableIntSet getVariableCandidates(Integer x, Map<Integer, Integer> partialMatching) {
		MutableIntSet allCandidates = null;

		for (LabeledEdge e : query.edgesOf(x)) {
			MutableIntSet candidates = null;

			int src = query.getEdgeSource(e), tgt = query.getEdgeTarget(e);

			if (src == x) {
				candidates = db.getCandidatesAllSubjects(e.predicate);

				if (partialMatching.containsKey(tgt)) {
					MutableIntSet validSubjects = IntSets.mutable
							.ofAll(db.getCandidatesByObjectAsSet(e.predicate, partialMatching.get(tgt))
									.collect(entry -> entry.getKey()));
					
					validSubjects.retainAll(candidates);
					
					candidates = validSubjects;
				}
			}

			if (tgt == x) {
				candidates = db.getCandidatesAllObjects(e.predicate);
				
				if (partialMatching.containsKey(src)) {
					MutableIntSet validObjects = IntSets.mutable
							.ofAll(db.getCandidatesBySubjectAsSet(e.predicate, partialMatching.get(src))
									.collect(entry -> entry.getValue()));
					
					validObjects.retainAll(candidates);
					
					candidates = validObjects;
				}
			}

			if (allCandidates == null)
				allCandidates = IntSets.mutable.ofAll(candidates);
			else
				allCandidates.retainAll(candidates);

			if (allCandidates.isEmpty())
				continue;
		}

		return allCandidates;
	}

	public List<LabeledEdge> order = new ArrayList<>();
	Map<LabeledEdge, Integer> edgeSizes = new HashMap<>();
	Map<Integer, Integer> variableSizes = new HashMap<>();

	public LabeledEdge head;
	public DirectedMultigraph<Integer, LabeledEdge> query;

	long totalEntities;

	public Integer split;

	int headSize;

	GraphDatabase db;

	public GraphVisitor(GraphDatabase db, DirectedMultigraph<Integer, LabeledEdge> query, LabeledEdge head,
			Integer split) {
		this.query = query;
		this.head = head;
		this.db = db;

		if (head != null)
			this.headSize = db.getPredicateSizes().get(head.predicate);

		for (LabeledEdge e : query.edgeSet())
			edgeSizes.put(e, db.getPredicateSizes().get(e.predicate));

		for (int v : query.vertexSet())
			variableSizes.put(v, getVariableCandidates(v).size());

		this.totalEntities = db.getNumberOfEntities();
		this.split = split;
	}

	public static GraphVisitor getPCAConfVisitor(GraphDatabase db, DirectedMultigraph<Integer, LabeledEdge> query,
			LabeledEdge head, Integer varToCorrupt, Integer split) {
		DirectedMultigraph<Integer, LabeledEdge> ret = LanguageBias.cloneSubgraph(query, head);

		int v = query.getEdgeSource(head), vp = query.getEdgeTarget(head);

		// Add this same head but with a free variable (-1).
		ret.addVertex(-1);

		LabeledEdge newHead = new LabeledEdge(head.predicate, -1);
		if (vp == varToCorrupt) {
			ret.addVertex(v);
			ret.addEdge(v, -1, newHead);
		} else if (v == varToCorrupt) {
			ret.addVertex(vp);
			ret.addEdge(-1, vp, newHead);
		} else
			throw new RuntimeException("Variable to corrupt: " + varToCorrupt + " not found in head!");

		GraphVisitor toRet = new GraphVisitor(db, ret, newHead, split);

		toRet.refineCandidates();

		for (int var : query.vertexSet())
			toRet.variableSizes.put(var, toRet.getVariableCandidates(var).size());

		return toRet;
	}

	public void refineCandidates() {
		for (LabeledEdge e : edgeSizes.keySet()) {
			int u = query.getEdgeSource(e), up = query.getEdgeTarget(e);

			// Let's get its candidates.
			MutableSet<Entry<Integer, Integer>> eEntries = Sets.mutable.ofAll(db.getCandidatesAsSet(e.predicate));

			// Let's get the touching edges.
			Set<LabeledEdge> touchingEdges = new HashSet<>();
			for (LabeledEdge touching : query.edgesOf(u))
				touchingEdges.add(touching);

			for (LabeledEdge touching : query.edgesOf(up))
				touchingEdges.add(touching);

			// Remove the current edge.
			touchingEdges.remove(e);

			// Get the edges touching u; skip e.
			for (LabeledEdge touching : touchingEdges) {
				// For the touching edge, we can have tu = u or tu = up, or tup = u or tup = up.
				int tu = query.getEdgeSource(touching), tup = query.getEdgeTarget(touching);

				// This is just to make sure there are no other choices.
				boolean checked = false;

				if (tu == u && tup != up) {
					checked = true;

					// All eEntries must have touching subjects.
					MutableIntSet validSubjects = db.getCandidatesAllSubjects(touching.predicate);
					eEntries.removeIf(entry -> !validSubjects.contains(entry.getKey()));
				} else if (tu != u && tup == up) {
					checked = true;

					// All eEntries must have touching objects.
					MutableIntSet validObjects = db.getCandidatesAllObjects(touching.predicate);
					eEntries.removeIf(entry -> !validObjects.contains(entry.getValue()));
				} else if (tu == u && tup == up) {
					checked = true;

					// All eEntries must have the same entries.
					eEntries.retainAll(db.getCandidatesAsSet(touching.predicate));
				}

				if (tup == u && tu != up) {
					checked = true;

					// All eEntries must have touching objects.
					MutableIntSet validSubjects = db.getCandidatesAllObjects(touching.predicate);
					eEntries.removeIf(entry -> !validSubjects.contains(entry.getKey()));
				} else if (tup != u && tu == up) {
					checked = true;

					// All eEntries must have touching subjects.
					MutableIntSet validObjects = db.getCandidatesAllSubjects(touching.predicate);
					eEntries.removeIf(entry -> !validObjects.contains(entry.getValue()));
				} else if (tup == u && tu == up) {
					checked = true;

					// All eEntries must have the same entries, but backwards.
					MutableSet<Entry<Integer, Integer>> reversedEntries = Sets.mutable.empty();
					for (Entry<Integer, Integer> entry : db.getCandidatesAsSet(touching.predicate)) {
						Entry<Integer, Integer> newEntry = Map.entry(entry.getValue(), entry.getKey());

						reversedEntries.add(newEntry);
					}

					eEntries.retainAll(reversedEntries);
				}

				if (!checked)
					throw new RuntimeException("This was never checked! Edge: " + e + "; touching: " + touching);
			}

			edgeSizes.put(e, eEntries.size());
		}
	}

	public static GraphVisitor getCWAConfVisitor(GraphDatabase db, DirectedMultigraph<Integer, LabeledEdge> query,
			LabeledEdge head, Integer split) {
		DirectedMultigraph<Integer, LabeledEdge> ret = LanguageBias.cloneSubgraph(query, head);

		// New head is null.
		return new GraphVisitor(db, ret, null, split);
	}

	public GraphDatabase getDb() {
		return db;
	}

	public void getOrder(Collection<Integer> groundedVariables) {
		order.clear();

		// Grounded variables are the variables that will be grounded (one specific
		// value) when solving the query. This collection can be empty if none are
		// grounded. Choose next edge greedily.
		Set<Integer> variablesWithValues = new HashSet<>(groundedVariables), variablesInOrder = new HashSet<>();

		Set<LabeledEdge> pending = new HashSet<>(query.edgeSet());

		// All edges with grounded variables will go first.
		List<LabeledEdge> edgesWithTwoGroundedVars = new ArrayList<>();

		for (LabeledEdge e : pending) {
			int x = query.getEdgeSource(e), y = query.getEdgeTarget(e);

			List<LabeledEdge> listToAdd = null;

			if (groundedVariables.contains(x) && groundedVariables.contains(y))
				listToAdd = edgesWithTwoGroundedVars;

			if (listToAdd != null) {
				listToAdd.add(e);

				variablesInOrder.addAll(Set.of(x, y));
				variablesWithValues.addAll(Set.of(x, y));
			}
		}

		pending.removeAll(edgesWithTwoGroundedVars);

		// These regardless of the size.
		order.addAll(edgesWithTwoGroundedVars);

		while (!pending.isEmpty()) {
			LabeledEdge next = null;
			double estimatedCost = Double.MAX_VALUE;

			for (LabeledEdge e : pending) {
				int x = query.getEdgeSource(e), y = query.getEdgeTarget(e);
				Set<Integer> currentVars = Set.of(x, y);

				double eCost = edgeSizes.get(e).doubleValue();

				// Compute connections to previous edges in the order and those grounded.
				int connections = SetUtils.intersection(currentVars, variablesInOrder).size(),
						grounded = SetUtils.intersection(currentVars, variablesWithValues).size();

				// Skipping those with no connections!
				if (!order.isEmpty() && connections == 0 && grounded == 0)
					continue;

				if (connections == 2 || grounded == 2) {
					// Add this directly!
					next = e;
					estimatedCost = 0;

					continue;
				}

				// For each predicate, there can be, at most, n*(n-1) directed edges, where n is
				// the number of entities. If we divide the predicate sizes by these values, we
				// have the "expectation" of an edge existing.
				double gamma = (edgeSizes.get(e) * 1.0) / (totalEntities * (totalEntities - 1));

				// Adjust cost based on connections and grounded. Giving priority to connections
				// to avoid Cartesian products in the final order.
				eCost = eCost * Math.pow(gamma, 2 * connections + grounded);

				if (eCost < estimatedCost) {
					next = e;
					estimatedCost = eCost;
				}
			}

			// We have a winner!
			order.add(next);
			pending.remove(next);

			int x = query.getEdgeSource(next), y = query.getEdgeTarget(next);

			variablesWithValues.addAll(Set.of(x, y));
			variablesInOrder.addAll(Set.of(x, y));
		}
	}

	public AtomicInteger matchingCalls = new AtomicInteger(), failures = new AtomicInteger();

	public void matching(int i, Map<Integer, Integer> partialMatching, Consumer<Map<Integer, Integer>> matchingFound,
			BiFunction<LabeledEdge, Map<Integer, Integer>, MutableList<Entry<Integer, Integer>>> iterate,
			Predicate<Void> earlyStopping) {
		matchingCalls.incrementAndGet();

		if (i == order.size())
			matchingFound.accept(partialMatching);
		else {
			// First time! Reset matching calls!
			if (i == 0) {
				matchingCalls.set(1);
				failures.set(0);
			}

			LabeledEdge e = order.get(i);
			int u = query.getEdgeSource(e), up = query.getEdgeTarget(e);
			boolean replaceU = !partialMatching.containsKey(u), replaceUp = !partialMatching.containsKey(up);

			Set<Integer> valuesAsSet = new HashSet<>(partialMatching.values());

			MutableList<Entry<Integer, Integer>> pairs = iterate.apply(e, partialMatching);

			int matches = 0;

			Iterator<Entry<Integer, Integer>> it = pairs.iterator();
			while (it.hasNext()) {
				Entry<Integer, Integer> pair = it.next();

				int v = pair.getKey(), vp = pair.getValue();

				// Injective function!
				if (replaceU && valuesAsSet.contains(v))
					continue;

				if (replaceUp && valuesAsSet.contains(vp))
					continue;

				matches++;

				if (replaceU)
					partialMatching.put(u, v);
				if (replaceUp)
					partialMatching.put(up, vp);

				matching(i + 1, partialMatching, matchingFound, iterate, earlyStopping);

				if (replaceU)
					partialMatching.remove(u);
				if (replaceUp)
					partialMatching.remove(up);

				// Check whether we are done early.
				if (earlyStopping.test(null))
					return;
			}

			if (matches == 0)
				failures.incrementAndGet();
		}
	}

	public boolean isInHeadCandidates(int s, int o) {
		// TODO Optimize! We could check sizes and run contains over the smaller.
		MutableSet<Entry<Integer, Integer>> subjectPairs = db.getCandidatesBySubjectAsSet(head.predicate, s);
		return subjectPairs != null && subjectPairs.contains(Map.entry(s, o));
	}

	public int getHeadSize() {
		return headSize;
	}

}
