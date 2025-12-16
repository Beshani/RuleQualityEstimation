package edu.rit.goal.graph;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.factory.primitive.IntObjectMaps;
import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

public class GraphDatabase {
	GraphDatabaseService db;

	Map<String, Integer> predicateSizes;

	MutableIntList allEntities;

	// These are our indexes.
	Map<String, MutableSet<Entry<Integer, Integer>>> predicatePairsAsSet;
	Map<String, MutableList<Entry<Integer, Integer>>> predicatePairsAsList;

	Map<String, MutableIntObjectMap<MutableList<Entry<Integer, Integer>>>> predicatePairsBySubjectAsList,
			predicatePairsByObjectAsList;
	Map<String, MutableIntObjectMap<MutableSet<Entry<Integer, Integer>>>> predicatePairsBySubjectAsSet,
			predicatePairsByObjectAsSet;

	Integer split;

	public GraphDatabase(GraphDatabaseService db, Integer split) {
		super();
		this.db = db;
		this.split = split;

		predicateSizes = new HashMap<>();

		predicatePairsAsSet = new HashMap<>();
		predicatePairsAsList = new HashMap<>();

		predicatePairsBySubjectAsList = new HashMap<>();
		predicatePairsByObjectAsList = new HashMap<>();

		predicatePairsBySubjectAsSet = new HashMap<>();
		predicatePairsByObjectAsSet = new HashMap<>();

		try (Transaction tx = db.beginTx()) {
			Result r = tx.execute(
					"MATCH (:Entity)-[r:Triple]->(:Entity) " + ((split != null) ? " WHERE " + getSplitFilter("r") : "")
							+ " RETURN r.predicate AS p, COUNT(r) AS cnt");
			while (r.hasNext()) {
				Map<String, Object> next = r.next();
				predicateSizes.put((String) next.get("p"), ((Number) next.get("cnt")).intValue());
			}
			r.close();
		}

		allEntities = IntLists.mutable.empty();
		try (Transaction tx = db.beginTx()) {
			Result r = tx.execute("MATCH (e:Entity) RETURN e.id AS eid");
			while (r.hasNext())
				allEntities.add((int) r.next().get("eid"));
			r.close();
		}

		Map<String, MutableIntSet> entitiesByPredicateAsSet = new HashMap<>();
		try (Transaction tx = db.beginTx()) {
			Result r = tx.execute("MATCH (e:Entity) RETURN e.id AS eid");
			while (r.hasNext())
				allEntities.add((int) r.next().get("eid"));
			r.close();
		}

		try (Transaction tx = db.beginTx()) {
			Result r = tx.execute("MATCH (s:Entity)-[r:Triple]->(o:Entity) "
					+ ((split != null) ? " WHERE " + getSplitFilter("r") : "")
					+ "RETURN r.predicate AS p, s.id AS sid, o.id AS oid");
			while (r.hasNext()) {
				Map<String, Object> next = r.next();

				String p = (String) next.get("p");
				int sid = (int) next.get("sid"), oid = (int) next.get("oid");

				Entry<Integer, Integer> pair = Map.entry(sid, oid);

				entitiesByPredicateAsSet.putIfAbsent(p, IntSets.mutable.empty());

				entitiesByPredicateAsSet.get(p).add(sid);
				entitiesByPredicateAsSet.get(p).add(oid);

				predicatePairsAsSet.putIfAbsent(p, Sets.mutable.empty());
				predicatePairsAsList.putIfAbsent(p, Lists.mutable.empty());

				predicatePairsBySubjectAsList.putIfAbsent(p, IntObjectMaps.mutable.empty());
				predicatePairsByObjectAsList.putIfAbsent(p, IntObjectMaps.mutable.empty());

				predicatePairsBySubjectAsSet.putIfAbsent(p, IntObjectMaps.mutable.empty());
				predicatePairsByObjectAsSet.putIfAbsent(p, IntObjectMaps.mutable.empty());

				predicatePairsAsSet.get(p).add(pair);
				predicatePairsAsList.get(p).add(pair);

				predicatePairsBySubjectAsList.get(p).getIfAbsentPut(sid, Lists.mutable.empty()).add(pair);
				predicatePairsByObjectAsList.get(p).getIfAbsentPut(oid, Lists.mutable.empty()).add(pair);

				predicatePairsBySubjectAsSet.get(p).getIfAbsentPut(sid, Sets.mutable.empty()).add(pair);
				predicatePairsByObjectAsSet.get(p).getIfAbsentPut(oid, Sets.mutable.empty()).add(pair);
			}
			r.close();
		}
	}

	public MutableIntList getAllEntities() {
		return allEntities;
	}

	public MutableList<Entry<Integer, Integer>> getCandidatesAsList(String p) {
		return predicatePairsAsList.get(p);
	}

	public MutableSet<Entry<Integer, Integer>> getCandidatesAsSet(String p) {
		return predicatePairsAsSet.get(p);
	}

	public MutableList<Entry<Integer, Integer>> getCandidatesBySubjectAsList(String p, int s) {
		return predicatePairsBySubjectAsList.get(p).get(s);
	}

	public MutableList<Entry<Integer, Integer>> getCandidatesByObjectAsList(String p, int o) {
		return predicatePairsByObjectAsList.get(p).get(o);
	}

	public MutableSet<Entry<Integer, Integer>> getCandidatesBySubjectAsSet(String p, int s) {
		return predicatePairsBySubjectAsSet.get(p).get(s);
	}

	public MutableSet<Entry<Integer, Integer>> getCandidatesByObjectAsSet(String p, int o) {
		return predicatePairsByObjectAsSet.get(p).get(o);
	}

	public MutableIntSet getCandidatesAllSubjects(String p) {
		return predicatePairsBySubjectAsSet.get(p).keySet();
	}

	public MutableIntSet getCandidatesAllObjects(String p) {
		return predicatePairsByObjectAsSet.get(p).keySet();
	}

	public Transaction getTransaction() {
		return db.beginTx();
	}

	public Map<String, Integer> getPredicateSizes() {
		return predicateSizes;
	}

	public Collection<String> getPredicates() {
		return predicateSizes.keySet();
	}

	public long getNumberOfEntities() {
		Long ret = null;
		try (Transaction tx = db.beginTx()) {
			Result r = tx.execute("MATCH (e:Entity) RETURN COUNT(e) AS cnt");
			if (r.hasNext()) {
				Map<String, Object> next = r.next();
				ret = ((Number) next.get("cnt")).longValue();
			}
			r.close();
		}
		return ret;
	}

	private String getSplitFilter(String triple) {
		if (split != null)
			return triple + ".split <= " + split + " ";
		else
			throw new RuntimeException("Variable cannot be null!");
	}

}
