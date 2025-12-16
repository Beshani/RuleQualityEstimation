package edu.rit.goal.canonical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DFSCode {
	private boolean minimum;
	private List<DFSCodeItem> code = new ArrayList<>();

	public boolean isMinimum() {
		return minimum;
	}

	public List<DFSCodeItem> getCode() {
		return code;
	}
	
	public void setMinimum(boolean minimum) {
		this.minimum = minimum;
	}

	public void setCode(List<DFSCodeItem> code) {
		this.code = code;
	}
	
	public static Map<Integer, Integer> getSubscript(DFSCode code) {
		return getSubscript(code.code);
	}
	
	public static Map<Integer, Integer> getSubscript(List<DFSCodeItem> code) {
		Map<Integer, Integer> map = new HashMap<>();
		for (DFSCodeItem i : code) {
			map.put(i.getI(), i.getSrc());
			map.put(i.getJ(), i.getTgt());
		}
		return map;
	}
	
}
