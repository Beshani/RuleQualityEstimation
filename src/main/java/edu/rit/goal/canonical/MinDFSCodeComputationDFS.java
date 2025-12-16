package edu.rit.goal.canonical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.Graphs;
import org.jgrapht.graph.DirectedMultigraph;

public class MinDFSCodeComputationDFS {
	public static DFSCode getMinDFSCode(DirectedMultigraph<Integer, EdgeWithPredicate> graph) {
		return getMinDFSCode(graph, graph.vertexSet());
	}
	
	public static DFSCode getMinDFSCode(DirectedMultigraph<Integer, EdgeWithPredicate> graph, Set<Integer> startingNodes) {
		MinDFSCodeComputationDFS aux = new MinDFSCodeComputationDFS();
		
		DFSCode minCode = new DFSCode();
		startingNodes.stream().forEach(v -> {
			DFSCodeHelper helper = aux.new DFSCodeHelper();
			helper.init(graph, v);
			findMinDFSCode(helper, minCode);
		});
		
		minCode.setMinimum(true);
		
		// Sanity checks.
		if (minCode.getCode().isEmpty())
			throw new Error("Empty minimum code.");
		if (minCode.getCode().size() != graph.edgeSet().size())
			throw new Error("Missing edges in code.");
		
		return minCode;
	}
	
	private static void findMinDFSCode(DFSCodeHelper current, DFSCode code) {
		if (current.code.size() == current.graph.edgeSet().size()) {
			if (code.getCode().isEmpty())
				code.setCode(current.code);
			else {
				boolean eq = DFSCodeComparator.codeEqOtherCode(current.code, code.getCode()), 
						lt = DFSCodeComparator.codeLessOtherCode(current.code, code.getCode());
				
				// Cannot be eq and lt.
				if (eq && lt)
					throw new Error("Both equal and less than.");
				
				// Check existing helpers.
				// Current code is less than existing code: Keep current only.
				if (lt)
					code.setCode(current.code);
			}
		} else {
			// All forward extensions first.
			List<DFSCodeItem> extensions = current.getForwardExtensions();
			// Sort: Smaller first.
			Collections.sort(extensions, new DFSCodeComparator(true));
			
			for (DFSCodeItem ex : extensions) {
				DFSCodeHelper copy = current.copy();
				copy.update(ex);
				
				// Add all missing edges.
				List<DFSCodeItem> missingExtensions = copy.getRemainingEdges();
				for (DFSCodeItem otherEx : missingExtensions)
					copy.update(otherEx);
				
				List<DFSCodeItem> minCode = null;
				boolean eq = false, lt = false;
				if (!code.getCode().isEmpty()) {
					minCode = pruneCode(code.getCode(), copy.dfsSubscript.get(copy.rightmostPath.get(copy.rightmostPath.size() - 1)));
					
					eq = DFSCodeComparator.codeEqOtherCode(copy.code, minCode);
					lt = DFSCodeComparator.codeLessOtherCode(copy.code, minCode);
					
					// Cannot be eq and lt.
					if (eq && lt)
						throw new Error("Both equal and less than.");
				}
				
				if (minCode == null || eq || lt)
					findMinDFSCode(copy, code);
			}
		}
	}
	
	private static List<DFSCodeItem> pruneCode(List<DFSCodeItem> code, int maxSubscript) {
		List<DFSCodeItem> pruned = new ArrayList<>();
		for (DFSCodeItem i : code)
			if (i.getI() <= maxSubscript && i.getJ() <= maxSubscript)
				pruned.add(i);
		return pruned;
	}
	
	private class DFSCodeHelper {
		DirectedMultigraph<Integer, EdgeWithPredicate> graph;
		DFSCodeComparator cmp = new DFSCodeComparator(false);
		
		Set<EdgeWithPredicate> edges = new HashSet<>();
		
		List<Integer> rightmostPath = new ArrayList<>();
		Map<Integer, Integer> dfsSubscript = new HashMap<>();
		
		List<DFSCodeItem> code = new ArrayList<>();
		
		DFSCodeHelper copy() {
			DFSCodeHelper copy = new DFSCodeHelper();
			copy(copy);
			return copy;
		}
		
		void copy(DFSCodeHelper copy) {
			copy.code = new ArrayList<>(code);
			copy.dfsSubscript = new HashMap<>(dfsSubscript);
			copy.edges = new HashSet<>(edges);
			copy.graph = graph;
			copy.cmp = cmp;
			copy.rightmostPath = new ArrayList<>(rightmostPath);
		}
		
		void init(DirectedMultigraph<Integer, EdgeWithPredicate> graph, Integer root) {
			this.graph = graph;
			rightmostPath.add(root);
			dfsSubscript.put(root, dfsSubscript.size());
		}
		
		void update(DFSCodeItem it) {
			Integer src = it.getSrc(), tgt = it.getTgt();
			EdgeWithPredicate e = it.getE();
			
			if (!dfsSubscript.containsKey(tgt)) {
				dfsSubscript.put(tgt, it.getJ());
				edges.add(e);
				addItem(it);
				
				// Sanity check.
				if (dfsSubscript.get(src) >= dfsSubscript.get(tgt))
					throw new Error("Incorrect forward edge.");
				
				while (rightmostPath.get(rightmostPath.size() - 1) != tgt)
					if (rightmostPath.get(rightmostPath.size() - 1) == src)
						rightmostPath.add(tgt);
					else
						rightmostPath.remove(rightmostPath.size() - 1);
			} else {
				edges.add(e);
				addItem(it);
			}
		}
		
		void addItem(DFSCodeItem it) {
			int pos = Collections.binarySearch(code, it, cmp);
			// Sanity check.
			if (pos >= 0)
				throw new Error("Item was already in the code.");
			code.add(-pos - 1, it);
		}
		
		List<DFSCodeItem> getRemainingEdges() {
			List<DFSCodeItem> extensions = Collections.synchronizedList(new ArrayList<>());
			graph.edgesOf(rightmostPath.get(rightmostPath.size() - 1)).parallelStream().forEach(e -> {
				if (edges.contains(e))
					return;
				
				Integer src = graph.getEdgeSource(e),
						tgt = graph.getEdgeTarget(e);
				
				if (!dfsSubscript.containsKey(src) || !dfsSubscript.containsKey(tgt))
					return;
				
				if (graph.getType().isUndirected() && 
						dfsSubscript.get(src) < dfsSubscript.get(tgt)) {
					src = graph.getEdgeTarget(e);
					tgt = graph.getEdgeSource(e);
				}
				
				extensions.add(new DFSCodeItem(src, tgt, e, dfsSubscript.get(src), dfsSubscript.get(tgt)));
			});
			return extensions;
		}
		
		List<DFSCodeItem> getForwardExtensions() {
			List<DFSCodeItem> extensions = new ArrayList<>();
			// From vertices in the rightmost path and introduces a new vertex (forward extension).
			for (int i = rightmostPath.size() - 1; i >= 0; i--) {
				Integer v = rightmostPath.get(i);
				
				for (EdgeWithPredicate e : graph.edgesOf(v)) {
					Integer u = Graphs.getOppositeVertex(graph, e, v);
					if (!dfsSubscript.containsKey(u))
						extensions.add(new DFSCodeItem(v, u, e, dfsSubscript.get(v), dfsSubscript.size()));
				}
				
				// If there is a vertex with extensions, we do not need to check the rest.
				if (!extensions.isEmpty())
					break;
			}
			return extensions;
		}
	}
	
}