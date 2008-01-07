package org.tigris.mbt.generators;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.tigris.mbt.ExtendedFiniteStateMachine;
import org.tigris.mbt.FiniteStateMachine;
import org.tigris.mbt.Keywords;
import org.tigris.mbt.Util;
import org.tigris.mbt.conditions.StopCondition;
import org.tigris.mbt.generators.PathGenerator;

import edu.uci.ics.jung.graph.impl.AbstractElement;
import edu.uci.ics.jung.graph.impl.DirectedSparseEdge;
import edu.uci.ics.jung.graph.impl.DirectedSparseVertex;
import edu.uci.ics.jung.utils.UserData;

public class ShortestPathGenerator extends PathGenerator {

	static Logger logger = Logger.getLogger(ShortestPathGenerator.class);

	private Stack preCalculatedPath = null;
	private DirectedSparseVertex lastState;
	private boolean extended;

	public ShortestPathGenerator(FiniteStateMachine machine, StopCondition stopCondition) {
		super(machine, stopCondition);
		extended = machine instanceof ExtendedFiniteStateMachine;
	}

	private void resetNode(AbstractElement abstractElement)
	{
		DijkstraPoint dp = (extended?new ExtendedDijkstraPoint((ExtendedFiniteStateMachine) machine):new DijkstraPoint()); 
		abstractElement.setUserDatum(Keywords.DIJKSTRA, dp, UserData.SHARED );
	}
	
	public String[] getNext() {
		Util.AbortIf(!hasNext(), "No more lines available");
		
		if(lastState == null || lastState != machine.getCurrentState() || preCalculatedPath.size() == 0)
		{
			for(Iterator i = machine.getAllStates().iterator();i.hasNext();)
			{
				resetNode((AbstractElement)i.next());
			}
			boolean oldBacktracking = machine.isBacktrack();
			machine.setBacktrack(true);
			calculateShortestPath();
			machine.setBacktrack(oldBacktracking);

			if(preCalculatedPath == null)
			{
				String unreachableStates = "";
				String reachableStates = "";
				for(Iterator i = machine.getAllStates().iterator();i.hasNext();)
				{
					DirectedSparseVertex dsv = (DirectedSparseVertex)i.next();
					DijkstraPoint dp = (DijkstraPoint) dsv.getUserDatum(Keywords.DIJKSTRA);
					if(dp == null || dp.getShortestPath() == null || dp.getShortestPath().size() == 0)
					{
						unreachableStates += "Unreachable vertex: " + Util.getCompleteVertexName( dsv ) + "\n";
					}
					else
					{
						reachableStates += "Reachable vertex: " + Util.getCompleteVertexName( dsv ) + " by means of " + dp + "\n";
					}
				}
				throw new RuntimeException( "No path found to the following vertices:\n" + unreachableStates +"\n\n"+
						"Paths found to the following:\n" +reachableStates);
			}

			// reverse path
			Stack temp = new Stack();
			while( preCalculatedPath.size() > 0 )
			{
				temp.push(preCalculatedPath.pop());
			}
			preCalculatedPath = temp;
		}

		DirectedSparseEdge edge = (DirectedSparseEdge) preCalculatedPath.pop();
		machine.walkEdge(edge);
		lastState = machine.getCurrentState();
		String[] retur = {machine.getEdgeName(edge), machine.getCurrentStateName()};
		return retur;
	}

	private void calculateShortestPath()
	{
		DijkstraPoint dp = ((DijkstraPoint)machine.getCurrentState().getUserDatum(Keywords.DIJKSTRA));
		Stack edgePath = (Stack) dp.getPath().clone();
		Set outEdges = machine.getCurrentOutEdges();
		for(Iterator i = outEdges.iterator();i.hasNext();)
		{
			DirectedSparseEdge e = (DirectedSparseEdge)i.next();
			edgePath.push(e);
			machine.walkEdge(e);
			if(hasNext())
			{
				DijkstraPoint nextDp = ((DijkstraPoint)machine.getCurrentState().getUserDatum(Keywords.DIJKSTRA));
				if(nextDp.compareTo(edgePath)>0)
				{
					nextDp.setPath(edgePath);
					calculateShortestPath();
				}
			}
			else
			{
				preCalculatedPath = (Stack) edgePath.clone();
			}
			machine.backtrack();
			edgePath.pop();
		}
	}

	protected class DijkstraPoint implements Comparable
	{
		protected Stack edgePath = null;
		
		public DijkstraPoint()
		{
		}
		
		public void setPath( Stack edgePath )
		{
			this.edgePath = (Stack) edgePath.clone();
		}

		public Stack getPath()
		{
			if(edgePath==null)edgePath = new Stack();
			return edgePath;
		}

		public Stack getShortestPath()
		{
			if(edgePath==null)edgePath = new Stack();
			return edgePath;
		}

		public int compareTo(Object o) {
			int a = getPath().size();
			if(a==0) return 1;
			int b = 0;
			if(o instanceof Stack)
			{
				b = ((Stack)o).size();
			} else {
				b = ((DijkstraPoint)o).getPath().size();
			}
		
			return a-b;
		}

		public String toString()
		{
			return edgePath.toString();
		}
	}

	protected class ExtendedDijkstraPoint extends DijkstraPoint implements Comparable
	{
		protected Hashtable edgePaths;
		protected ExtendedFiniteStateMachine parent;
		
		public ExtendedDijkstraPoint()
		{
			edgePaths = new Hashtable();
		}
		
		public ExtendedDijkstraPoint( ExtendedFiniteStateMachine parent) {
			this();
			this.parent = parent;
		}

		public void setPath( Stack edgePath )
		{
			if(edgePaths == null) edgePaths = new Hashtable();
			edgePaths.put(parent.getCurrentDataString(), edgePath.clone()); 
			if(edgePaths.size()>50)
				throw new RuntimeException( "Too many internal states in "+ Util.getCompleteVertexName(parent.getCurrentState()) + " please revise model.");
		}

		public Stack getPath()
		{
			String key = parent.getCurrentDataString();
			Stack localPath = (Stack) edgePaths.get(key);
			if(localPath == null)
			{
				localPath = new Stack();
				edgePaths.put(key, localPath);
			}
			return localPath;
		}

		public Stack getShortestPath()
		{
			Stack retur = null; 
			for(Iterator i = edgePaths.values().iterator();i.hasNext();)
			{
				Stack path = (Stack) i.next();
				if(retur == null || retur.size() > path.size()) retur = path;
			}
			return retur;
		}
		
		public String toString()
		{
			return edgePaths.toString();
		}
	}
}