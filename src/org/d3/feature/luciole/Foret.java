package org.d3.feature.luciole;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.graphstream.algorithm.generator.BarabasiAlbertGenerator;
import org.graphstream.algorithm.generator.DorogovtsevMendesGenerator;
import org.graphstream.algorithm.generator.Generator;
import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.AdjacencyListGraph;
import org.graphstream.stream.SinkAdapter;

public class Foret {
	public static final String LUCIOLE_ATTRIBUTE = "luciole";

	Graph g;
	ReentrantLock lock = new ReentrantLock();
	Condition starter = lock.newCondition();
	PrintStream outputSync;
	double syncMean, syncDeviation;

	public Foret(int size) {
		try {
			outputSync = new PrintStream(String.format("sync-%d.dat",
					System.currentTimeMillis()));
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}

		g = new AdjacencyListGraph("foret");
		g.addSink(new LucioleFactory());
		g.addAttribute("ui.quality");
		g.addAttribute("ui.antialias");
		g.addAttribute(
				"ui.stylesheet",
				"node { size: 20px; fill-mode: dyn-plain; fill-color: black,orange; } graph { fill-color: black; }");

		Generator gen = new DorogovtsevMendesGenerator(); // new
															// BarabasiAlbertGenerator();
		gen.addSink(g);

		gen.begin();
		while (g.getNodeCount() < size)
			gen.nextEvents();
		gen.end();
		gen.removeSink(g);

		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
		}

		try {
			lock.lock();
			starter.signalAll();
		} finally {
			lock.unlock();
		}
	}

	public void loop() {
		g.display();

		long startDate = System.currentTimeMillis();
		double sync;

		while (true) {
			try {
				lock.lock();

				for (int i = 0; i < g.getNodeCount(); i++) {
					double c = g.getNode(i).getNumber("ui.color");

					if (Double.isNaN(c))
						c = 0;

					c *= 0.75;
					g.getNode(i).setAttribute("ui.color", c);
				}
			} finally {
				lock.unlock();
			}

			syncMeasure();
			outputSync.printf(Locale.ROOT, "%d %f %f\n",
					System.currentTimeMillis() - startDate, syncMean,
					syncDeviation);
			outputSync.flush();

			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {

			}
		}
	}

	public void syncMeasure() {
		double mean = 0, deviation = 0;

		try {
			lock.lock();

			for (int idx = 0; idx < g.getNodeCount(); idx++)
				mean += syncMeasure(g.getNode(idx));

			mean /= g.getNodeCount();

			for (int idx = 0; idx < g.getNodeCount(); idx++) {
				double s = g.getNode(idx).getNumber("sync-measure");
				deviation += (s - mean) * (s - mean);
			}

			deviation /= g.getNodeCount();
			deviation = Math.sqrt(deviation);
		} finally {
			lock.unlock();
		}

		syncMean = mean;
		syncDeviation = deviation;
	}

	public double syncMeasure(Node n) {
		double s = 0;
		Luciole l = n.getAttribute(LUCIOLE_ATTRIBUTE);
		Iterator<Node> it = n.getNeighborNodeIterator();

		while (it.hasNext()) {
			Node o = it.next();
			Luciole ol = o.getAttribute(LUCIOLE_ATTRIBUTE);

			s += Math.abs(l.lastFlashAt - ol.lastFlashAt);
		}

		s /= n.getDegree();
		n.setAttribute("sync-measure", s);
		return s;
	}

	public void flash(String luciole) {
		try {
			lock.lock();
			Node n = g.getNode(luciole);
			n.setAttribute("ui.color", 1);
		} finally {
			lock.unlock();
		}
	}

	public void flash(String luciole, long delay) {
		try {
			lock.lock();
			Node n = g.getNode(luciole);
			n.setAttribute("ui.color", 1);

			Iterator<Node> it = n.getNeighborNodeIterator();
			while (it.hasNext()) {
				Node o = it.next();
				Luciole ol = o.getAttribute(LUCIOLE_ATTRIBUTE);
				ol.signal(luciole, delay);
			}
		} finally {
			lock.unlock();
		}
	}

	class LucioleFactory extends SinkAdapter {
		@Override
		public void graphCleared(String sourceId, long timeId) {
			// TODO Auto-generated method stub

		}

		@Override
		public void nodeAdded(String sourceId, long timeId, String nodeId) {
			Node n = g.getNode(nodeId);
			Luciole l = new Luciole(Foret.this, nodeId);
			n.setAttribute(LUCIOLE_ATTRIBUTE, l);
		}

		@Override
		public void nodeRemoved(String sourceId, long timeId, String nodeId) {
			Node n = g.getNode(nodeId);
			Luciole l = n.getAttribute(LUCIOLE_ATTRIBUTE);
			l.swat();
		}

		@Override
		public void edgeAdded(String sourceId, long timeId, String edgeId,
				String fromId, String toId, boolean directed) {
			Edge e = g.getEdge(edgeId);
			Luciole l = e.getNode0().getAttribute(LUCIOLE_ATTRIBUTE);
			l.neigh++;

			l = e.getNode1().getAttribute(LUCIOLE_ATTRIBUTE);
			l.neigh++;
		}

		@Override
		public void edgeRemoved(String sourceId, long timeId, String edgeId) {
			Edge e = g.getEdge(edgeId);
			Luciole l = e.getNode0().getAttribute(LUCIOLE_ATTRIBUTE);
			l.neigh--;

			l = e.getNode1().getAttribute(LUCIOLE_ATTRIBUTE);
			l.neigh--;
		}
	}

	public static void main(String... args) {
		Foret f = new Foret(1000);
		f.loop();
	}
}
