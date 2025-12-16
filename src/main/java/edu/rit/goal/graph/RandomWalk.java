package edu.rit.goal.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DirectedMultigraph;

public class RandomWalk {
	GraphDatabase db;

	// This is the max number of restarts for random walks.
	int maxRestarts = 1000;

	// This is whether we force a closing triple in a random walk's last step.
	boolean forceClosing = false;

	// This is whether to consider the rule length.
	Integer length;

	// This is whether to consider the predicate.
	String head;

	// This is whether to consider the total number of nodes.
	Integer totalNumberOfNodes;

	// This is whether to consider the total number of predicates.
	Integer totalNumberOfPredicates;

	// This is the selected split (if any).
	Integer split;

	public RandomWalk(GraphDatabase db) {
		this.db = db;
	}

	public void setMaxRestarts(int maxRestarts) {
		this.maxRestarts = maxRestarts;
	}

	public void setForceClosing(boolean forceClosing) {
		this.forceClosing = forceClosing;
	}

	public void setLength(Integer length) {
		this.length = length;
	}

	public void setHead(String predicate) {
		this.head = predicate;
	}

	public void setTotalNumberOfNodes(Integer totalNumberOfNodes) {
		this.totalNumberOfNodes = totalNumberOfNodes;
	}

	public void setTotalNumberOfPredicates(Integer totalNumberOfPredicates) {
		this.totalNumberOfPredicates = totalNumberOfPredicates;
	}

	public void setSplit(Integer split) {
		this.split = split;
	}

	public class Walk {
		public DirectedMultigraph<Integer, LabeledEdge> walk;
		public LabeledEdge head;
	}

	public Walk randomWalk() {
		DirectedMultigraph<Integer, LabeledEdge> walk = new DirectedMultigraph<>(LabeledEdge.class);
		LabeledEdge currentHead = null;

		boolean found = false;
		int restarts = 0;

		AtomicInteger tid = new AtomicInteger();

		restart: while (!found && restarts < maxRestarts) {
			restarts++;

			Integer next = null;

			currentHead = null;

			clearWalk(walk);

			tid.set(0);

			if (head == null) {
				// Get the first random entity.
				next = db.getAllEntities().get(ThreadLocalRandom.current().nextInt(db.getAllEntities().size()));

				walk.addVertex(next);
			} else {
				// Choose a random entity and its edges for the given predicate.
				Map<String, Object> row = getRandomNeighbor(next, head);

				if (row != null) {
					int s = (Integer) row.get("s"), o = (Integer) row.get("o");
					String p = (String) row.get("p");

					LabeledEdge added = addEdgeToWalk(walk, s, p, o, tid);

					// If the edge did not exist already and s is equals to o (it can happen!).
					if (added != null) {
						// Update next and head.
						next = (Integer) row.get("neighbor");

						if (currentHead == null)
							currentHead = added;
					} else
						// Stop if we see the same edge.
						continue restart;
				} else
					// Dead end. Stop! (This may never happen since we are traversing the undirected
					// version of the graph. It can only happen if we select an isolated entity.)
					continue restart;
			}

			while (walk.edgeSet().size() < length) {
				// We are in the last step and we want to force closing.
				if (walk.edgeSet().size() == length - 1 && forceClosing) {
					// Get vertices that are not closed.
					Set<Integer> nonClosedVertices = LanguageBias.getNonClosedVertices(walk);

					if (nonClosedVertices.size() > 2)
						// Impossible we will be able to close this thing. Restart!
						continue restart;

					Iterator<Integer> it = nonClosedVertices.iterator();

					Map<String, Set<Entry<Integer, Integer>>> edgesInWalk = new HashMap<>();
					for (LabeledEdge e : walk.edgeSet()) {
						int u = walk.getEdgeSource(e), up = walk.getEdgeTarget(e);

						edgesInWalk.putIfAbsent(e.predicate, new HashSet<>());
						edgesInWalk.get(e.predicate).add(Map.entry(u, up));
					}

					Set<Integer> verticesInWalk = walk.vertexSet();

					Map<String, MutableList<Entry<Integer, Integer>>> newTriples = new HashMap<>();

					// There are two of them: find all edges between them.
					if (nonClosedVertices.size() == 2) {
						int u = it.next(), v = it.next();
						Entry<Integer, Integer> pair = Map.entry(u, v);

						for (String currentP : db.getPredicates()) {
							newTriples.putIfAbsent(currentP, Lists.mutable.empty());

							MutableSet<Entry<Integer, Integer>> set = db.getCandidatesBySubjectAsSet(currentP, u);

							// It is there but the edge is not yet in the walk.
							if (set != null && set.contains(pair) && (!edgesInWalk.containsKey(currentP)
									|| !edgesInWalk.get(currentP).contains(pair)))
								newTriples.get(currentP).add(pair);
						}
					}

					// There is one of them: take its edges connecting vertices in the walk.
					if (nonClosedVertices.size() == 1) {
						int u = it.next();

						for (String currentP : db.getPredicates()) {
							newTriples.putIfAbsent(currentP, Lists.mutable.empty());

							MutableSet<Entry<Integer, Integer>> set = db.getCandidatesBySubjectAsSet(currentP, u);

							// It is there but the edge is not yet in the walk.
							if (set != null)
								for (Entry<Integer, Integer> pair : set)
									if (verticesInWalk.contains(pair.getValue()) && (!edgesInWalk.containsKey(currentP)
											|| !edgesInWalk.get(currentP).contains(pair)))
										newTriples.get(currentP).add(pair);

							set = db.getCandidatesByObjectAsSet(currentP, u);

							// It is there but the edge is not yet in the walk.
							if (set != null)
								for (Entry<Integer, Integer> pair : set)
									if (verticesInWalk.contains(pair.getKey()) && (!edgesInWalk.containsKey(currentP)
											|| !edgesInWalk.get(currentP).contains(pair)))
										newTriples.get(currentP).add(pair);
						}
					}

					// There is zero of them: take all edges connecting entities not in the walk.
					if (nonClosedVertices.size() == 0)
						for (int u : verticesInWalk)
							for (int v : verticesInWalk) {
								Entry<Integer, Integer> pair = Map.entry(u, v);

								for (String currentP : db.getPredicates()) {
									newTriples.putIfAbsent(currentP, Lists.mutable.empty());

									MutableSet<Entry<Integer, Integer>> set = db.getCandidatesBySubjectAsSet(currentP,
											u);

									// It is there but the edge is not yet in the walk.
									if (set != null && set.contains(pair) && (!edgesInWalk.containsKey(currentP)
											|| !edgesInWalk.get(currentP).contains(pair)))
										newTriples.get(currentP).add(pair);
								}
							}

					// Remove all empty predicates.
					for (String key : new HashSet<>(newTriples.keySet()))
						if (newTriples.get(key).isEmpty())
							newTriples.remove(key);

					// We did not make it!
					if (newTriples.isEmpty())
						continue restart;

					// Choose one of these randomly and we are done! If there are multiple and we
					// fail, we were really close. It is worthy to try them all.
					List<String> predicates = new ArrayList<>(newTriples.keySet());

					String p = predicates.get(ThreadLocalRandom.current().nextInt(predicates.size()));

					MutableList<Entry<Integer, Integer>> list = newTriples.get(p);

					Entry<Integer, Integer> pair = list.get(ThreadLocalRandom.current().nextInt(list.size()));

					boolean anotherFound = false;

					int s = pair.getKey(), o = pair.getValue();

					LabeledEdge added = addEdgeToWalk(walk, s, p, o, tid);

					if (added != null) {
						// Update next and head.
						next = o;
						anotherFound = true;

						if (currentHead == null)
							currentHead = added;
					}

					// Should not happen, but I've seen things you people wouldn't believe.
					if (!anotherFound)
						continue restart;
				} else {
					Map<String, Object> row = getRandomNeighbor(next, null);

					if (row != null) {
						int s = (Integer) row.get("s"), o = (Integer) row.get("o");
						String p = (String) row.get("p");

						LabeledEdge added = addEdgeToWalk(walk, s, p, o, tid);

						// If the edge did not exist already and s is equals to o (it can happen!).
						if (added != null) {
							// Update next and head.
							next = (Integer) row.get("neighbor");

							if (currentHead == null)
								currentHead = added;
						} else
							// Stop if we see the same edge.
							continue restart;
					} else
						// Dead end. Stop! (This may never happen since we are traversing the undirected
						// version of the graph. It can only happen if we select an isolated entity.)
						continue restart;
				}
			}

			// We did it?
			found = walk.edgeSet().size() == length;

			found = found
					&& (totalNumberOfNodes == null || getTotalNumberOfNodes(walk, Set.of()) == totalNumberOfNodes);

			found = found && (totalNumberOfPredicates == null
					|| getTotalNumberOfPredicates(walk, null) == totalNumberOfPredicates);

			// Let's clear the walk.
			if (!found)
				clearWalk(walk);
		}

		Walk ret = new Walk();
		ret.walk = walk;
		ret.head = currentHead;

		return ret;
	}

	private void clearWalk(DirectedMultigraph<Integer, LabeledEdge> walk) {
		for (int v : new HashSet<>(walk.vertexSet()))
			walk.removeVertex(v);
	}

	private int getTotalNumberOfNodes(DirectedMultigraph<Integer, LabeledEdge> walk, Set<Integer> extra) {
		Set<Integer> nodes = new HashSet<>(walk.vertexSet());

		nodes.addAll(extra);

		return nodes.size();
	}

	private int getTotalNumberOfPredicates(DirectedMultigraph<Integer, LabeledEdge> walk, String extra) {
		Set<String> predicates = walk.edgeSet().stream().map(edge -> edge.predicate).collect(Collectors.toSet());

		if (extra != null)
			predicates.add(extra);

		return predicates.size();
	}

	private LabeledEdge addEdgeToWalk(DirectedMultigraph<Integer, LabeledEdge> walk, int s, String p, int o,
			AtomicInteger tid) {
		// If the edge did not exist already and s is equals to o (it can happen!).
		if (!edgeExists(s, p, o, walk) && s != o) {
			// We are going to add this edge. Let's check the other constraints!
			LabeledEdge e = new LabeledEdge(p, tid.incrementAndGet());

			// Let's count the total number of nodes.
			if (totalNumberOfNodes != null && getTotalNumberOfNodes(walk, Set.of(s, o)) > totalNumberOfNodes)
				return null;

			// Let's count the total number of predicates.
			if (totalNumberOfPredicates != null && getTotalNumberOfPredicates(walk, p) > totalNumberOfPredicates)
				return null;

			// Update walk.
			walk.addVertex(s);
			walk.addVertex(o);

			walk.addEdge(s, o, e);

			// TODO We are getting disconnected random walks!
			ConnectivityInspector<Integer, LabeledEdge> inspector = new ConnectivityInspector<>(walk);
			if (!inspector.isConnected())
				System.err.println("Walk not connected!!!!!!!");

			return e;
		} else
			return null;
	}

	// We were happily doing this with the tid before. Now, because things are
	// cached, we cannot have easy access to tid anymore, so we need to do it the
	// old-fashioned way... checking one by one! It shouldn't be that bad, but less
	// elegant for sure.
	private boolean edgeExists(int s, String p, int o, DirectedMultigraph<Integer, LabeledEdge> walk) {
		boolean found = false;

		for (LabeledEdge e : walk.edgeSet()) {
			int x = walk.getEdgeSource(e), y = walk.getEdgeTarget(e);

			if (x == s && y == o && p.equals(e.predicate)) {
				found = true;
				break;
			}
		}

		return found;
	}

	private class RandomNeighborChoice {
		boolean subject, object;
		String predicate;

		public RandomNeighborChoice(boolean subject, boolean object, String predicate) {
			super();
			this.subject = subject;
			this.object = object;
			this.predicate = predicate;
		}
	}

	private Map<String, Object> getRandomNeighbor(Integer u, String p) {
		// Getting all choices of u (subject/object and p).
		List<RandomNeighborChoice> choices = new ArrayList<>();

		Set<String> predicatesToCheck = new HashSet<>();
		if (p == null)
			predicatesToCheck.addAll(db.getPredicates());
		else
			predicatesToCheck.add(p);

		if (u == null) {
			for (String currentP : predicatesToCheck) {
				choices.add(new RandomNeighborChoice(true, false, currentP));
				choices.add(new RandomNeighborChoice(false, true, currentP));
			}

			// No choices!
			if (choices.isEmpty())
				return null;

			// Get random choice.
			RandomNeighborChoice choice = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));

			MutableIntSet set = null;
			ImmutableIntList list = null;

			if (choice.subject)
				// Creating the list for direct access. Not great, but what to do.
				set = db.getCandidatesAllSubjects(choice.predicate);

			if (choice.object)
				// Creating the list for direct access. Not great, but what to do.
				set = db.getCandidatesAllObjects(choice.predicate);

			list = IntLists.immutable.ofAll(set);

			u = list.get(ThreadLocalRandom.current().nextInt(list.size()));
		}

		// Getting all choices of u (subject/object and p).
		choices.clear();
		for (String currentP : predicatesToCheck) {
			if (db.getCandidatesBySubjectAsList(currentP, u) != null)
				choices.add(new RandomNeighborChoice(true, false, currentP));

			if (db.getCandidatesByObjectAsList(currentP, u) != null)
				choices.add(new RandomNeighborChoice(false, true, currentP));
		}

		// No choices!
		if (choices.isEmpty())
			return null;

		// Get random choice.
		RandomNeighborChoice choice = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));

		MutableList<Entry<Integer, Integer>> list = null;

		if (choice.subject)
			list = db.getCandidatesBySubjectAsList(choice.predicate, u);

		if (choice.object)
			list = db.getCandidatesByObjectAsList(choice.predicate, u);

		Entry<Integer, Integer> pair = list.get(ThreadLocalRandom.current().nextInt(list.size()));

		Map<String, Object> row = new HashMap<>();

		row.put("s", pair.getKey());
		row.put("o", pair.getValue());
		row.put("p", choice.predicate);
		row.put("neighbor", (u == pair.getKey()) ? pair.getValue() : pair.getKey());

		return row;
	}
}
