package edu.rit.goal.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jgrapht.graph.DirectedMultigraph;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import edu.rit.goal.graph.signature.GraphSignature;
import edu.rit.goal.graph.signature.GraphSignature.SignatureResult;
import edu.rit.goal.graph.signature.SignatureAtom;

public class LatticeGraph {
	GraphDatabaseService db;

	enum NodeLabel {
		Root, Node, Leaf, Metadata
	}

	public LatticeGraph(GraphDatabaseService db) {
		super();
		this.db = db;
	}

	public boolean isEmpty() {
		boolean empty = false;

		try (Transaction t = db.beginTx()) {
			Result r = t.execute("MATCH (n) RETURN COUNT(*) AS cnt");
			empty = ((Number) r.next().get("cnt")).intValue() == 0;
			r.close();
		}

		return empty;
	}

	public void init() {
		try (Transaction tx = db.beginTx()) {
			// We are going to create a fake node to store metadata that is not part of the
			// lattice.
			tx.execute("CREATE (u:" + NodeLabel.Metadata + ")");
			tx.commit();
		}
	}

	public Set<Integer> getRuleIds() {
		Set<Integer> ret = new HashSet<>();

		try (Transaction tx = db.beginTx()) {
			Result r = tx.execute("MATCH (u:" + NodeLabel.Leaf + ") RETURN u.id AS id");
			while (r.hasNext())
				ret.add((int) r.next().get("id"));
			r.close();
		}

		return ret;
	}

	// Find rule in lattice following the tree structure.
	public Integer findRule(DirectedMultigraph<Integer, LabeledEdge> graph, LabeledEdge head) {
		Integer leafId = null;

		// Get signature.
		SignatureResult signature = GraphSignature.getSignature(graph, head);
		int length = graph.edgeSet().size();

		try (Transaction tx = db.beginTx()) {
			// Traverse from root to leaf using a single Cypher query.
			StringBuffer query = new StringBuffer("MATCH ");
			Map<String, Object> queryParams = new HashMap<>();

			for (int i = 0; i < length; i++) {
				SignatureAtom sa = signature.signature[i];

				query.append("(u");
				query.append(i + ":");

				if (i == 0)
					query.append(NodeLabel.Root);
				else if (i == length - 1)
					query.append(NodeLabel.Leaf);
				else
					query.append(NodeLabel.Node);

				query.append("{atom_s:$atomsu" + i + ", atom_o:$atomou" + i + ", atom_p:$atompu" + i + "})");

				queryParams.put("atomsu" + i, sa.s);
				queryParams.put("atomou" + i, sa.o);
				queryParams.put("atompu" + i, sa.p);

				if (i < length - 1)
					query.append("-->");
			}

			// Return something!
			query.append(" RETURN u" + (length - 1) + ".id AS leaf");

			Result r = tx.execute(query.toString(), queryParams);
			if (r.hasNext())
				leafId = (int) r.next().get("leaf");
			r.close();
		}

		return leafId;
	}

	public DirectedMultigraph<Integer, LabeledEdge> getRule(int ruleid) {
		DirectedMultigraph<Integer, LabeledEdge> rule = new DirectedMultigraph<>(LabeledEdge.class);

		try (Transaction tx = db.beginTx()) {
			Result r = tx.execute("MATCH p = (r:" + NodeLabel.Root + ")-[:next *1..]->(l:" + NodeLabel.Leaf
					+ " {id:$id}) " + "RETURN nodes(p) AS nodes", Map.of("id", ruleid));
			if (r.hasNext()) {
				Map<String, Object> next = r.next();

				@SuppressWarnings("unchecked")
				List<Node> nodes = (List<Node>) next.get("nodes");

				for (Node n : nodes) {
					int s = (int) n.getProperty("atom_s"), o = (int) n.getProperty("atom_o");

					rule.addVertex(s);
					rule.addVertex(o);
				}

				for (int i = 0; i < nodes.size(); i++) {
					Node current = nodes.get(i);
					int s = (int) current.getProperty("atom_s"), o = (int) current.getProperty("atom_o");
					rule.addEdge(s, o, new LabeledEdge((String) current.getProperty("atom_p"), i));
				}
			}
			r.close();
		}

		return rule;
	}

	public Integer resumeStep(int workerId) {
		Integer currentStep = null;

		// If the worker exists, resume. We store everything in the Metadata node.
		try (Transaction tx = db.beginTx()) {
			Node metadata = (Node) tx.execute("MATCH (u:" + NodeLabel.Metadata + ") RETURN u").next().get("u");

			String prop = workerId + "_Step";

			if (metadata.hasProperty(prop))
				currentStep = (int) metadata.getProperty(prop);
		}

		return currentStep;
	}

	public Integer updateStep(int workerId, int step) {
		Integer currentStep = null;

		// If the worker exists, resume. We store everything in the Metadata node.
		try (Transaction tx = db.beginTx()) {
			Node metadata = (Node) tx.execute("MATCH (u:" + NodeLabel.Metadata + ") RETURN u").next().get("u");

			String prop = workerId + "_Step";
			metadata.setProperty(prop, step);

			tx.commit();
		}

		return currentStep;
	}

	/** <These are for AMIE.> */

	public void addPending(int ruleid) {
		// Find rule and mark as pending.
		try (Transaction tx = db.beginTx()) {
			Node metadata = (Node) tx.execute("MATCH (u {id:$id}) RETURN u", Map.of("id", ruleid)).next().get("u");

			String prop = "pending";
			metadata.setProperty(prop, true);

			prop = "processing";
			metadata.setProperty(prop, false);

			tx.commit();
		}
	}

	public synchronized Integer nextPending() {
		Integer id = null;

		// Find minimum rule marked as pending but not yet in processing.
		try (Transaction tx = db.beginTx()) {
			id = (Integer) tx.execute("MATCH (u {pending:true, processing:false}) RETURN MIN(u.id) AS id").next()
					.get("id");

			if (id != null) {
				Node metadata = (Node) tx.execute("MATCH (u {id:$id}) RETURN u", Map.of("id", id)).next().get("u");

				String prop = "processing";
				metadata.setProperty(prop, true);

				tx.commit();
			}
		}

		return id;
	}

	public synchronized Integer countPending() {
		Integer count = null;

		// Find minimum rule marked as pending but not yet in processing.
		try (Transaction tx = db.beginTx()) {
			count = ((Number) tx.execute("MATCH (u {pending:true, processing:false}) RETURN COUNT(u.id) AS cnt").next()
					.get("cnt")).intValue();
		}

		return count;
	}

	public synchronized void markAsCompleted(int id) {
		// Find minimum rule marked as pending.
		try (Transaction tx = db.beginTx()) {
			Node metadata = (Node) tx.execute("MATCH (u {id:$id}) RETURN u", Map.of("id", id)).next().get("u");

			String prop = "pending";
			metadata.setProperty(prop, false);

			prop = "processing";
			metadata.setProperty(prop, false);

			tx.commit();
		}
	}

	public synchronized void resumeProcessing() {
		// Find all pending=true and processing=true and set processing=false.
		try (Transaction tx = db.beginTx()) {
			Result r = tx.execute("MATCH (u {pending:true, processing:true}) RETURN u");

			while (r.hasNext()) {
				Node metadata = (Node) r.next().get("u");

				String prop = "processing";
				metadata.setProperty(prop, false);
			}

			r.close();

			tx.commit();
		}
	}

	/** </These are for AMIE.> */

	public void addPending(int workerId, int ruleid) {
		try (Transaction tx = db.beginTx()) {
			Node metadata = (Node) tx.execute("MATCH (u:" + NodeLabel.Metadata + ") RETURN u").next().get("u");

			String prop = workerId + "_Processing";
			metadata.setProperty(prop, ruleid);

			tx.commit();
		}
	}

	public void releasePending(int workerId) {
		try (Transaction tx = db.beginTx()) {
			Node metadata = (Node) tx.execute("MATCH (u:" + NodeLabel.Metadata + ") RETURN u").next().get("u");

			String prop = workerId + "_Processing";
			Object ret = metadata.removeProperty(prop);

			if (ret == null)
				throw new RuntimeException("Worker: " + workerId + " was not processing any rule!");

			tx.commit();
		}
	}

	public Integer retrievePending(int workerId) {
		Integer ret = null;

		try (Transaction tx = db.beginTx()) {
			Node metadata = (Node) tx.execute("MATCH (u:" + NodeLabel.Metadata + ") RETURN u").next().get("u");

			String prop = workerId + "_Processing";

			if (metadata.hasProperty(prop))
				ret = (Integer) metadata.getProperty(prop);
		}

		return ret;
	}

	public synchronized int addToLattice(DirectedMultigraph<Integer, LabeledEdge> graph, LabeledEdge head) {
		int id = -1;

		// Get signature.
		SignatureResult signature = GraphSignature.getSignature(graph, head);
		int length = signature.signature.length;

		try (Transaction tx = db.beginTx()) {
			// TODO Find number of parents only if the current graph has more than two
			// edges.
			// TODO Find the parents in the lattice. Remove one edge at a time, find the
			// minimum signature, and search for the rule.
//			int totalParents = 0;

			if (graph.edgeSet().size() > 2) {
				// TODO Do something!
			}

			Integer max = (Integer) tx.execute("MATCH (u) RETURN MAX(u.id) AS max").next().get("max");
			if (max != null)
				id = max.intValue() + 1;

			// One by one. If one is not found, create and connect to previous.
			int previousId = -1;
			for (int i = 0; i < length; i++) {
				SignatureAtom atom = signature.signature[i];
				Map<String, Object> params = new HashMap<>(
						Map.of("atom_s", atom.s, "atom_o", atom.o, "atom_p", atom.p));

				if (previousId != -1)
					params.put("pid", previousId);

				if (i == 0) {
					// It is root!
					Result res = tx.execute("MATCH (u:" + NodeLabel.Root
							+ " {atom_s:$atom_s, atom_o:$atom_o, atom_p:$atom_p}) RETURN u.id AS id", params);

					boolean rootFound = false;

					if (res.hasNext()) {
						previousId = (int) res.next().get("id");
						rootFound = true;
					}

					res.close();

					if (!rootFound) {
						params.put("id", id);

						tx.execute("CREATE (u:" + NodeLabel.Root
								+ " {atom_s:$atom_s, atom_o:$atom_o, atom_p:$atom_p, id:$id})", params).close();

						previousId = id;
						id++;
					}
				} else if (i == length - 1) {
					// It is leaf!
					params.put("pid", previousId);

					Result res = tx.execute(
							"MATCH (p {id:$pid})-[:next]->(u {atom_s:$atom_s, atom_o:$atom_o, atom_p:$atom_p}) RETURN u.id AS id",
							params);

					boolean leafFound = false;

					int leafId = -1;

					if (res.hasNext()) {
						leafId = (int) res.next().get("id");
						leafFound = true;
					}

					res.close();

					// Add to the leaf any extra parameters we may use.
					if (leafFound) {
						// Add leaf label.
						params.put("id", leafId);
						id = leafId;

						tx.execute("MATCH (p {id:$pid})-[:next]->(u {id:$id}) SET u:" + NodeLabel.Leaf, params).close();
					} else {
						// Create leaf.
						params.put("id", id);

						tx.execute("MATCH (p {id:$pid}) CREATE (p)-[:next]->(u:" + NodeLabel.Leaf
								+ " {atom_s:$atom_s, atom_o:$atom_o, atom_p:$atom_p, id:$id})", params).close();
					}
				} else {
					// It is intermediate node!
					params.put("pid", previousId);

					Result res = tx.execute(
							"MATCH (p {id:$pid})-[:next]->(u {atom_s:$atom_s, atom_o:$atom_o, atom_p:$atom_p}) RETURN u.id AS id",
							params);

					boolean nodeFound = false;

					int nodeId = -1;

					if (res.hasNext()) {
						nodeId = (int) res.next().get("id");
						nodeFound = true;
					}

					res.close();

					if (nodeFound) {
						// Add node label.
						params.put("id", nodeId);
						previousId = nodeId;

						tx.execute("MATCH (p {id:$pid})-[:next]->(u {id:$id}) SET u:" + NodeLabel.Node, params).close();
					} else {
						// Create node.
						params.put("id", id);
						previousId = id;

						tx.execute("MATCH (p {id:$pid}) CREATE (p)-[:next]->(u:" + NodeLabel.Node
								+ " {atom_s:$atom_s, atom_o:$atom_o, atom_p:$atom_p, id:$id})", params).close();
						id++;
					}
				}
			}

			tx.commit();
		}

		return id;
	}

	// TODO Deal with this!
//	public void applyApriori(DirectedMultigraph<Integer, LabeledEdge> graph, List<Predicate<DirectedMultigraph<Integer, LabeledEdge>>> bias) {
//		// The graph is in the lattice and there is one worker that will process it. First, let's check whether there are parents
//		//	of the graph present in the lattice and they have been pruned.
//		
//		try (Transaction tx = db.beginTx()) {
//			if (graph.edgeSet().size() > 2)
//				// Remove one edge and check whether the graph is present. The new graph must fulfill the language bias.
//				for (LabeledEdge e : graph.edgeSet()) {
//					DirectedMultigraph<Integer, LabeledEdge> parent = cloneSubgraph(graph, e);
//					
//					if (checkLanguageBias(parent, bias)) {
//						GraphInLattice gil = findGraph(parent);
//						
//						// Parent found!
//						if (gil.dbId != null)
//							// Each matching is a mapping from the parent graph in the lattice to the parent here.
//							for (Map<Integer, Integer> matching : gil.matchings)
//								for (LabeledEdge parentEdge : parent.edgeSet()) {
//									int v = parent.getEdgeSource(parentEdge), vp = parent.getEdgeTarget(parentEdge);
//									int u = -1, up = -1;
//									
//									for (int key : matching.keySet()) {
//										int value = matching.get(key);
//										
//										if (value == v)
//											u = key;
//										if (value == vp)
//											up = key;
//									}
//									
//									// Find the edges in the parent graph in the lattice.
//									for (LabeledEdge other : gil.graph.getAllEdges(u, up))
//										if (other.predicate.equals(parentEdge.predicate))
//											parentEdge.pruned = parentEdge.pruned || other.pruned;
//									
//									LabeledEdge parentEdgeInGraph = graph.edgeSet().stream().filter(x->x.pid == parentEdge.pid).findAny().get();
//									parentEdgeInGraph.pruned = parentEdgeInGraph.pruned || parentEdgeInGraph.pruned;
//								}
//					}
//				}
//		}
//	}

	public static String getStrRule(DirectedMultigraph<Integer, LabeledEdge> graph, int pid) {
		LabeledEdge head = graph.edgeSet().stream().filter(e -> e.pid == pid).findAny().get();

		Function<LabeledEdge, String> printEdge = (e) -> {
			return e.predicate + "(" + graph.getEdgeSource(e) + ", " + graph.getEdgeTarget(e) + ")";
		};

		StringBuffer output = new StringBuffer();

		output.append(printEdge.apply(head));
		output.append(" <= ");
		for (LabeledEdge e : graph.edgeSet())
			if (e != head) {
				output.append(printEdge.apply(e));
				output.append(" ");
			}

		return output.toString();
	}

}
