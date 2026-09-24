package com.example.ngac.support;

import gov.nist.ngac.pm.core.pap.PAP;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

/**
 * Scalability harness for the paper's experiments: loads each policy.json given on the command
 * line, builds G_Com (Algorithms 1-2) and runs Algorithms 3, 4 and 5 on it, and prints one CSV
 * line per policy with the wall-clock time of each step and the number of findings.
 *
 * <pre>
 * mvn -q compile exec:java \
 *     -Dexec.mainClass=com.example.ngac.support.Benchmark \
 *     -Dexec.args="bench/synth200/policy.json bench/synth1000/policy.json"
 * </pre>
 *
 * Each measurement is preceded by {@value #WARMUP} untimed runs, so that the JIT has compiled the
 * code before the {@value #RUNS} timed runs; the reported value is the median, which is less
 * sensitive to garbage collection than the mean.
 */
public final class Benchmark {

    private static final int WARMUP = 3;
    private static final int RUNS = 7;

    private Benchmark() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: Benchmark <policy.json> [<policy.json> ...]");
            System.exit(1);
        }
        System.out.println("rules,associations,prohibitions,fp,build_ms,alg3_ms,alg4_ms,alg5_ms,"
                + "alg3_findings,alg4_findings,alg5_findings");
        for (String arg : args) {
            run(Path.of(arg));
        }
    }

    private static void run(Path policyFile) throws Exception {
        if (!Files.exists(policyFile)) {
            System.err.println("missing: " + policyFile);
            return;
        }
        JsonPolicyLoader.LoadedPolicy loaded = JsonPolicyLoader.load(policyFile);
        PAP pap = loaded.pap();

        List<Double> build = new ArrayList<>();
        List<Double> t3 = new ArrayList<>();
        List<Double> t4 = new ArrayList<>();
        List<Double> t5 = new ArrayList<>();

        List<Algorithm1_2_ComplementedGraphBuilder.Edge> g = null;
        List<Algorithm1_2_ComplementedGraphBuilder.Edge> assoc = null;
        List<Algorithm1_2_ComplementedGraphBuilder.Edge> fp = null;
        List<Algorithm1_2_ComplementedGraphBuilder.Edge> assign = null;
        Algorithm3_RedundancyDetector.Result r3 = null;
        Algorithm4_HierarchicalRedundancyDetector.Result r4 = null;
        Algorithm5_ConflictDetector.Result r5 = null;

        for (int i = 0; i < WARMUP + RUNS; i++) {
            long t0 = System.nanoTime();
            var builder = new Algorithm1_2_ComplementedGraphBuilder(pap);
            g = builder.computeG();                                   // ASSIGNMENT + ASSOCIATION
            fp = builder.computeFP();                                 // Algorithms 1-2
            long t1 = System.nanoTime();

            assoc = g.stream().filter(e -> e.kind()
                    == Algorithm1_2_ComplementedGraphBuilder.EdgeKind.ASSOCIATION).toList();
            assign = g.stream().filter(e -> e.kind()
                    == Algorithm1_2_ComplementedGraphBuilder.EdgeKind.ASSIGNMENT).toList();

            long t2 = System.nanoTime();
            r3 = Algorithm3_RedundancyDetector.detect(pap, assoc, fp);
            long t3end = System.nanoTime();
            r4 = new Algorithm4_HierarchicalRedundancyDetector(pap).detect(assign, assoc, fp);
            long t4end = System.nanoTime();
            r5 = new Algorithm5_ConflictDetector(pap).detect(assoc, fp);
            long t5end = System.nanoTime();

            if (i >= WARMUP) {
                build.add(ms(t0, t1));
                t3.add(ms(t2, t3end));
                t4.add(ms(t3end, t4end));
                t5.add(ms(t4end, t5end));
            }
        }

        System.out.printf(Locale.ROOT, "%s,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%d,%d,%d%n",
                policyFile.getParent() == null ? policyFile : policyFile.getParent().getFileName(),
                assoc.size(), fp.size(), fp.size(),
                median(build), median(t3), median(t4), median(t5),
                r3.totalCount(), r4.totalCount(), r5.conflicts().size());
    }

    private static double ms(long from, long to) {
        return (to - from) / 1_000_000.0;
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }
}
