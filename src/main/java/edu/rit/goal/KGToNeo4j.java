package edu.rit.goal;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.io.fs.FileUtils;

import edu.rit.goal.Experiments.Dataset;

public class KGToNeo4j {
	private static final AtomicInteger currentSplit = new AtomicInteger();
	
	public static void main(String[] args) throws Exception {
//		final String neo4jFolder = args[0], datasetsFolder = args[1];
//		final int number = Integer.valueOf(args[2]);
		
		final String neo4jFolder = "db/", datasetsFolder = "C:\\Users\\crrvcs\\PycharmProjects\\AugmentedKGE\\Datasets\\";
		final int number = 8;

		String commitRateRows = "100000 ROWS";
		int commitRate = 100000;

		Dataset dataset = Experiments.resolveDataset(number);

		System.out.println(new Date() + " -- Started: " + dataset);

		FileUtils.delete(Path.of(neo4jFolder + File.separator + dataset));

		DatabaseManagementService service = new DatabaseManagementServiceBuilder(
				Path.of(neo4jFolder + File.separator + dataset))
				.setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
				.setConfig(GraphDatabaseSettings.preallocate_logical_logs, false)
				.setConfig(GraphDatabaseSettings.memory_transaction_database_max_size, 0l)
				// This cleans the transaction files every second.
				.setConfig(GraphDatabaseSettings.check_point_interval_time, Duration.ofSeconds(1l)).build();
		GraphDatabaseService db = service.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME);
		registerShutdownHook(service);

		{
			Transaction tx = db.beginTx();
			tx.execute("CREATE INDEX EntityIdIdx FOR (e:Entity) ON (e.id)");
			tx.execute("CREATE INDEX TripleIdIdx FOR ()-[r:Triple]-() ON (r.id)");
			tx.execute("CREATE INDEX TriplePredicateIdx FOR ()-[r:Triple]-() ON (r.predicate)");
			tx.commit();
		}

		Set<String> predicates = new HashSet<>();
		{
			AtomicInteger tid = new AtomicInteger();

			Set<Integer> entities = new HashSet<>();
			List<Map<String, Object>> triples = new ArrayList<>();

			TriFunction<Integer, String, Integer, Void> processNodes = (s, p, o) -> {
				if (s != null)
					entities.add(s);
				
				if (o != null)
					entities.add(o);

				if (entities.size() >= commitRate || (s == null && p == null && o == null))
					commitEntities(db, entities, commitRateRows);

				return null;
			};

			TriFunction<Integer, String, Integer, Void> processTriples = (s, p, o) -> {
				if (s != null && p != null && o != null) {
					triples.add(Map.of("s", s, "o", o, "p", p, "tid", tid.get()));
	
					tid.incrementAndGet();
					predicates.add(p);
				}

				if (triples.size() >= commitRate || (s == null && p == null && o == null))
					commitTriples(db, triples, commitRateRows);

				return null;
			};

			String[] filesOfInterest = new String[] { "train2id.txt", "valid2id.txt", "test2id.txt" };

			// We will read twice: once for entities and another for triples.
			System.out.println("\t" + new Date() + " -- Collecting entities...");

			ZipFile zip = new ZipFile(new File(datasetsFolder + File.separator + dataset + ".zip"));
			Iterator<? extends ZipEntry> it = zip.entries().asIterator();
			while (it.hasNext()) {
				ZipEntry entry = it.next();

				if (Arrays.stream(filesOfInterest).anyMatch(f -> entry.getName().endsWith(f)))
					processGZipFile(zip, entry, dataset, processNodes);
			}

			System.out.println("\t" + new Date() + " -- Collecting triples...");
			zip = new ZipFile(new File(datasetsFolder + File.separator + dataset + ".zip"));
			it = zip.entries().asIterator();
			while (it.hasNext()) {
				ZipEntry entry = it.next();

				if (Arrays.stream(filesOfInterest).anyMatch(f -> entry.getName().endsWith(f))) {
					// We need to differentiate among splits.
					if (entry.getName().contains("train"))
						currentSplit.set(0);
					
					if (entry.getName().contains("valid"))
						currentSplit.set(1);
					
					if (entry.getName().contains("test"))
						currentSplit.set(2);
					
					processGZipFile(zip, entry, dataset, processTriples);
				}
			}

			System.out.println("\t" + new Date() + " -- Removing repeated triples...");

			Transaction tx = db.beginTx();
			System.out.println("\t\tBefore: " + tx.execute("MATCH ()-[r]->() RETURN COUNT(r) AS cnt").next());
			tx.close();

			db.executeTransactionally("MATCH (s:Entity)-[t1:Triple]->(o:Entity), (s)-[t2:Triple]->(o) "
					+ "WHERE t1.tid < t2.tid AND t1.predicate = t2.predicate CALL {" + "WITH t2 DELETE t2"
					+ "} IN TRANSACTIONS OF " + commitRateRows);

			tx = db.beginTx();
			System.out.println("\t\tAfter: " + tx.execute("MATCH ()-[r]->() RETURN COUNT(r) AS cnt").next());
			tx.close();
		}

		System.out.println(new Date() + " -- Done");

		service.shutdown();
	}

	private static void processGZipFile(ZipFile zip, ZipEntry entry, Dataset dataset,
			TriFunction<Integer, String, Integer, Void> process) throws Exception {
		Scanner sc = new Scanner(zip.getInputStream(entry));

		while (sc.hasNextLine()) {
			String[] sop = sc.nextLine().split(Experiments.resolveSeparator(dataset));

			if (sop.length == 3) {
				int s = -1, o = -1;
				String p = null;

				switch (Experiments.resolveFormat(dataset)) {
				case OPENKE:
					s = Integer.valueOf(sop[0]);
					o = Integer.valueOf(sop[1]);
					p = sop[2];

					break;
				case WIKI:
					s = Integer.valueOf(sop[0].replace("Q", ""));
					o = Integer.valueOf(sop[2].replace("Q", ""));
					p = sop[1];

					break;
				default:
					break;
				}

				process.apply(s, p, o);
			}
		}

		sc.close();
		
		// Last call! Flush the commits!
		process.apply(null, null, null);
	}

	private static void commitEntities(GraphDatabaseService db, Set<Integer> entities, String commitRateRows) {
		System.out.println("\t" + new Date() + " -- Creating entities...");
		
		db.executeTransactionally(
				"UNWIND $entities AS e CALL {WITH e MERGE (:Entity {id:e})} IN TRANSACTIONS OF " + commitRateRows,
				Map.of("entities", new ArrayList<>(entities)));
		
		entities.clear();
	}

	private static void commitTriples(GraphDatabaseService db, List<Map<String, Object>> triples,
			String commitRateRows) {
		System.out.println("\t" + new Date() + " -- Creating triples...");
		
		db.executeTransactionally("UNWIND $triples AS t CALL {WITH t "
				+ "MATCH (s:Entity {id:t.s}), (o:Entity {id:t.o}) "
				+ "CREATE (s)-[:Triple {predicate:t.p, id:t.tid, split:$split}]->(o)" + "} IN TRANSACTIONS OF " + commitRateRows,
				Map.of("triples", triples, "split", currentSplit.intValue()));
		
		triples.clear();
	}

	private static void registerShutdownHook(final DatabaseManagementService service) {
		// Registers a shutdown hook for the Neo4j instance so that it
		// shuts down nicely when the VM exits (even if you "Ctrl-C" the
		// running application).
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				service.shutdown();
			}
		});
	}

}
