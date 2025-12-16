package edu.rit.goal.canonical;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.iterators.PermutationIterator;
import org.eclipse.collections.impl.block.factory.Comparators;
import org.jgrapht.graph.DirectedMultigraph;

public class CanonicalTest {
	public static void main(String[] args) {
		int totalLevels = 5;
		Map<Integer, Map<String, DirectedMultigraph<Integer, EdgeWithPredicate>>> levels = new HashMap<>(),
				levelsSig = new HashMap<>();
		List<String> predicates = List.of("p1", "p2", "p3");

		{
			DirectedMultigraph<Integer, EdgeWithPredicate> g = new DirectedMultigraph<>(EdgeWithPredicate.class);
			g.addVertex(0);

			levels.put(0, Map.of("", g));
			levelsSig.put(0, Map.of("", g));
		}
		
		for (int level = 1; level <= totalLevels; level++) {
			long before, after;
			
			before = System.nanoTime();
			levels.put(level, new HashMap<>());

			// Get each in previous level.
			for (Entry<String, DirectedMultigraph<Integer, EdgeWithPredicate>> entry : levels.get(level - 1).entrySet()) {
				DirectedMultigraph<Integer, EdgeWithPredicate> prev = entry.getValue();
				
				// Add dangling edges.
				for (Integer u : prev.vertexSet()) {
					int v = prev.vertexSet().size();

					for (String p : predicates) {
						DirectedMultigraph<Integer, EdgeWithPredicate> cloned = null;

						// Add outgoing dangling edge.
						cloned = clone(prev);
						cloned.addVertex(v);

						cloned.addEdge(u, v, new EdgeWithPredicate(p));
						
						addGraphToLevel(levels, level, cloned);
						
						// Add incoming dangling edge.
						cloned = clone(prev);
						cloned.addVertex(v);

						cloned.addEdge(v, u, new EdgeWithPredicate(p));
						
						addGraphToLevel(levels, level, cloned);
					}
				}

				// Add closing edges.
				for (int u : prev.vertexSet())
					for (int v : prev.vertexSet()) {
						if (u == v)
							continue;

						for (String p : predicates)
							if (!prev.getAllEdges(u, v).stream().anyMatch(e -> e.predicate.equals(p))) {
								// Add closing edge. When u < v, outgoing, when u > v, incoming.
								DirectedMultigraph<Integer, EdgeWithPredicate> cloned = clone(prev);

								cloned.addEdge(u, v, new EdgeWithPredicate(p));
								
								addGraphToLevel(levels, level, cloned);
							}
					}
			}
			after = System.nanoTime();
			
			System.out.println("Time creating level " + level + " checking previous: " + (after-before)/1e9);
		
			before = System.nanoTime();
			levelsSig.put(level, new HashMap<>());

			// Get each in previous level.
			for (Entry<String, DirectedMultigraph<Integer, EdgeWithPredicate>> entry : levelsSig.get(level - 1).entrySet()) {
				DirectedMultigraph<Integer, EdgeWithPredicate> prev = entry.getValue();
				SignatureResult prevSig = getMinimumSignature(prev);
				
				// Add dangling edges.
				for (Integer u : prev.vertexSet()) {
					int v = prev.vertexSet().size();
					
					int root = prevSig.mapping.values().stream().min(Comparator.naturalOrder()).get(),
							rightmostVertex = prevSig.mapping.values().stream().max(Comparator.naturalOrder()).get();
					
					Set<Integer> rightmostPathMapping = new HashSet<>(Set.of(root));
					int sizeBefore = -1, sizeAfter = -1;
					do {
						sizeBefore = rightmostPathMapping.size();
						
						for (SignatureAtom a : prevSig.signature)
							if (rightmostPathMapping.contains(a.s))
								rightmostPathMapping.add(a.o);
						
						sizeAfter = rightmostPathMapping.size();
					} while (sizeBefore != sizeAfter);
					
					Set<Integer> rightmostPath = new HashSet<>();
					for (Entry<Integer, Integer> m : prevSig.mapping.entrySet())
						if (rightmostPathMapping.contains(m.getValue()))
							rightmostPath.add(m.getKey());
					
					boolean uIsRightmostVertex = 
							prevSig.mapping.get(u) == prevSig.mapping.values().stream().max(
									Comparator.naturalOrder()).get();
					
					for (String p : predicates) {
						List<SignatureAtom> x = Arrays.stream(prevSig.signature).filter(
								atom->p.compareTo(atom.p) < 0).collect(Collectors.toList());
						boolean pGtOrEqPrevPreds = x.size() == 0;
						
						SignatureResult clonedSig = null;
						DirectedMultigraph<Integer, EdgeWithPredicate> cloned = null;

						// Add outgoing dangling edge.
						cloned = clone(prev);
						cloned.addVertex(v);

						cloned.addEdge(u, v, new EdgeWithPredicate(p));
						
						clonedSig = getDanglingSignature(prevSig, cloned, v);
						
						// TODO Testing. Remove!
						SignatureResult minSig = getMinimumSignature(cloned);
						boolean canGrow = cmpSignature(prevSig.signature, clonedSig.signature) == 0;
						
						if ((prevSig.signature.length > 0 && rightmostPath.contains(u) && canGrow && pGtOrEqPrevPreds) && cmpSignature(clonedSig.signature, minSig.signature) != 0)
							minSig.toString();
						
						if (checkSignature(prevSig, cloned, clonedSig)) {
							DirectedMultigraph<Integer, EdgeWithPredicate> existing = 
									levelsSig.get(level).put(clonedSig.toString(), cloned);
							
							if (existing != null)
								System.out.println("Wrong!");
						} else {
							// TODO Remove!
							clonedSig.toString();
						}
						
						// Add incoming dangling edge.
						cloned = clone(prev);
						cloned.addVertex(v);

						cloned.addEdge(v, u, new EdgeWithPredicate(p));
						
						clonedSig = getDanglingSignature(prevSig, cloned, v);
						
						// TODO Testing. Remove!
						minSig = getMinimumSignature(cloned);
						canGrow = cmpSignature(prevSig.signature, clonedSig.signature) == 0;
						
						if ((prevSig.signature.length > 0 && rightmostPath.contains(u) && canGrow && pGtOrEqPrevPreds) && cmpSignature(clonedSig.signature, minSig.signature) != 0)
							minSig.toString();
						
						if (checkSignature(prevSig, cloned, clonedSig)) {
							DirectedMultigraph<Integer, EdgeWithPredicate> existing = 
									levelsSig.get(level).put(clonedSig.toString(), cloned);
							
							if (existing != null)
								System.out.println("Wrong!");
						} else {
							// TODO Remove!
							clonedSig.toString();
						}
					}
				}

				// Add closing edges.
				for (int u : prev.vertexSet())
					for (int v : prev.vertexSet()) {
						if (u == v)
							continue;

						for (String p : predicates)
							if (!prev.getAllEdges(u, v).stream().anyMatch(e -> e.predicate.equals(p))) {
								boolean pGtOrEqPrevPreds = Arrays.stream(prevSig.signature).map(
										atom->p.compareTo(atom.p) < 0).count() == 0;
								
								// Add closing edge. When u < v, outgoing, when u > v, incoming.
								DirectedMultigraph<Integer, EdgeWithPredicate> cloned = clone(prev);

								cloned.addEdge(u, v, new EdgeWithPredicate(p));
								
								SignatureResult clonedSig = getClosingSignature(prevSig, cloned);
								
								boolean canGrow = cmpSignature(prevSig.signature, clonedSig.signature) == 0;
								
								if (checkSignature(prevSig, cloned, clonedSig)) {
									DirectedMultigraph<Integer, EdgeWithPredicate> existing = 
											levelsSig.get(level).put(clonedSig.toString(), cloned);
									
									if (existing != null)
										System.out.println("Wrong!");
								}
							}
					}
			}
			
			after = System.nanoTime();
			
			System.out.println("Time creating level " + level + " without checking previous: " + (after-before)/1e9);
		}
		
		for (int level = 1; level <= totalLevels; level++) {
			if (levels.get(level).size() != levelsSig.get(level).size())
				System.out.println("Check!");
			
			System.out.println("Level: " + level + "; Size: " + levels.get(level).size());
//			System.out.println("Level (sig.): " + level + "; Size: " + levelsSig.get(level).size());
//
//			for (DirectedMultigraph<Integer, EdgeWithPredicate> g : levels.get(level))
//				System.out.println("\t" + g.toString());
			
			for (Entry<String, DirectedMultigraph<Integer, EdgeWithPredicate>> entry : levels.get(level).entrySet()) {
				SignatureResult gSig = getMinimumSignature(entry.getValue());
				
				if (!levelsSig.get(level).containsKey(gSig.toString()))
					System.out.println("Check!");
			}
		}
	}
	
	private static SignatureResult getDanglingSignature(SignatureResult prevSig, DirectedMultigraph<Integer, EdgeWithPredicate> cloned, int v) {
		// Get signature mapping.
		Map<Integer, Integer> mapping = new HashMap<>(prevSig.mapping);
		
		// Add to mapping.
		int maxId = prevSig.mapping.values().stream().max(Comparators.naturalOrder()).get();
		mapping.put(v, maxId+1);
		
		// Get signature.
		SignatureAtom[] newSig = getSignature(cloned, mapping);
		
		SignatureResult ret = new CanonicalTest().new SignatureResult();
		
		ret.mapping = mapping;
		ret.signature = newSig;
		
		return ret;
	}
	
	private static boolean checkSignature(SignatureResult prevSig, DirectedMultigraph<Integer, EdgeWithPredicate> cloned, SignatureResult newSig) {
		// Get minimum signature.
		SignatureResult minSig = getMinimumSignature(cloned);
		
		return minSig.mapping.equals(newSig.mapping) && 
				cmpSignature(prevSig.signature, newSig.signature) == 0 && cmpSignature(newSig.signature, minSig.signature) == 0;
	}
	
	private static SignatureResult getClosingSignature(SignatureResult prevSig, DirectedMultigraph<Integer, EdgeWithPredicate> cloned) {
		// Get signature mapping.
		Map<Integer, Integer> mapping = new HashMap<>(prevSig.mapping);
		
		// Get signature.
		SignatureAtom[] newSig = getSignature(cloned, mapping);
		
		SignatureResult ret = new CanonicalTest().new SignatureResult();
		
		ret.mapping = mapping;
		ret.signature = newSig;
		
		return ret;
	}

	public static void addGraphToLevel(Map<Integer, Map<String, DirectedMultigraph<Integer, EdgeWithPredicate>>> levels,
			int level, DirectedMultigraph<Integer, EdgeWithPredicate> graph) {
		// Check signatures.
		SignatureResult graphSig = getMinimumSignature(graph);

		if (!levels.get(level).containsKey(graphSig.toString()))
			levels.get(level).put(graphSig.toString(), graph);
	}

	// TODO Remove?
	public static List<DirectedMultigraph<Integer, EdgeWithPredicate>> getParents(
			DirectedMultigraph<Integer, EdgeWithPredicate> child) {
		List<DirectedMultigraph<Integer, EdgeWithPredicate>> parents = new ArrayList<>();

		SignatureResult childSig = getMinimumSignature(child);
		DFSCode childDFSCode = MinDFSCodeComputationDFS.getMinDFSCode(child);

		for (EdgeWithPredicate e : child.edgeSet()) {
			DirectedMultigraph<Integer, EdgeWithPredicate> parent = clone(child);
			parent.removeEdge(e);

			Integer u = child.getEdgeSource(e), v = child.getEdgeTarget(e);

			if (parent.edgesOf(u).isEmpty())
				parent.removeVertex(u);

			if (parent.edgesOf(v).isEmpty())
				parent.removeVertex(v);

			SignatureResult parentSig = getMinimumSignature(parent);
			DFSCode parentDFSCode = MinDFSCodeComputationDFS.getMinDFSCode(parent);

			parents.add(parent);
		}

		return parents;
	}

	public static DirectedMultigraph<Integer, EdgeWithPredicate> clone(
			DirectedMultigraph<Integer, EdgeWithPredicate> g) {
		DirectedMultigraph<Integer, EdgeWithPredicate> cloned = new DirectedMultigraph<>(EdgeWithPredicate.class);

		for (Integer v : g.vertexSet())
			cloned.addVertex(v);

		for (EdgeWithPredicate e : g.edgeSet()) {
			int u = g.getEdgeSource(e), v = g.getEdgeTarget(e);
			cloned.addEdge(u, v, e);
		}

		return cloned;
	}

	public static SignatureResult getMinimumSignature(DirectedMultigraph<Integer, EdgeWithPredicate> graph) {
		SignatureResult ret = new CanonicalTest().new SignatureResult();

		// We aim to find the mapping with the minimum signature.

		Set<Integer> variables = new HashSet<>(graph.vertexSet());

		// There are no more variables!
		if (variables.isEmpty()) {
			Map<Integer, Integer> mapping = new HashMap<>();
			
			SignatureAtom[] signature = getSignature(graph, mapping);
			ret.signature = signature;
			ret.mapping = mapping;
		} else {
			// Get all the permutations of the variables.
			PermutationIterator<Integer> permutIt = new PermutationIterator<>(variables);
			while (permutIt.hasNext()) {
				List<Integer> permut = permutIt.next();

				Map<Integer, Integer> mapping = new HashMap<>();
				
				// Add the rest (2, 3, 4, ...).
				for (int i = 0; i < permut.size(); i++)
					mapping.put(permut.get(i), i + 2);

				SignatureAtom[] signature = getSignature(graph, mapping);

				// If this signature is less than the current one, update.
				if (ret.signature == null || cmpSignature(signature, ret.signature) < 0) {
					ret.signature = signature;
					ret.mapping = mapping;
				}
			}
		}

		return ret;
	}

	private static SignatureAtom[] getSignature(DirectedMultigraph<Integer, EdgeWithPredicate> graph,
			Map<Integer, Integer> mapping) {
		SignatureAtom[] signature = new SignatureAtom[graph.edgeSet().size()];

		int i = 0;
		for (EdgeWithPredicate e : graph.edgeSet()) {
			int s = graph.getEdgeSource(e), o = graph.getEdgeTarget(e);

			SignatureAtom sa = new CanonicalTest().new SignatureAtom();
			sa.s = mapping.get(s);
			sa.o = mapping.get(o);
			sa.p = e.predicate;

			signature[i++] = sa;
		}

		Arrays.sort(signature);

		return signature;
	}

	private static int cmpSignature(SignatureAtom[] a, SignatureAtom[] b) {
		int ret = 0;

		for (int i = 0; ret == 0 && i < a.length; i++)
			ret = a[i].compareTo(b[i]);

		return ret;
	}

	class SignatureResult {
		// This is the actual signature.
		SignatureAtom[] signature;
		// This is the mapping between the ids of the input graph and the signature.
		Map<Integer, Integer> mapping;

		@Override
		public String toString() {
			return Arrays.toString(signature);
		}
		
		public SignatureResult clone() {
			SignatureResult ret = new SignatureResult();
			
			ret.signature = new SignatureAtom[signature.length];
			
			for (int i = 0; i < signature.length; i++)
				ret.signature[i] = signature[i].clone();
			
			ret.mapping = new HashMap<>(mapping);
			
			return ret;
		}
	}

	class SignatureAtom implements Comparable<SignatureAtom> {
		int s, o;
		String p;

		@Override
		public int compareTo(SignatureAtom o) {
			int ret = Integer.compare(this.s, o.s);

			if (ret == 0)
				ret = Integer.compare(this.o, o.o);

			if (ret == 0)
				ret = this.p.compareTo(o.p);

			return ret;
		}

		@Override
		public String toString() {
			return "(" + s + ", " + o + ", " + p + ")";
		}
		
		public SignatureAtom clone() {
			SignatureAtom ret = new SignatureAtom();
			
			ret.s = s;
			ret.o = o;
			ret.p = p;
			
			return ret;
		}

	}

}
