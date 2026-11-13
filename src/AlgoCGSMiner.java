import java.io.IOException;
import java.util.*;

/** CGSMiner intersections and synchronized extensions with explicit merge and closure handling. */
public class AlgoCGSMiner {
    private CGSParameters parameters;
    private CGSData data;
    private CGSIntersectionIndex index;
    private Map<CGSSequence, Set<BitSet>> candidates;
    private final Map<Long, Boolean> containment = new HashMap<>();
    private long totalTime, extensionCount;
    private double maxMemory;
    private int patternCount, intersectionCount, fragmentCount;

    public void runAlgorithm(CGSParameters parameters) throws IOException {
        parameters.validate();
        long start = System.nanoTime();
        data = CGSData.load(parameters.inputPath(), parameters.discretizationThreshold, parameters.attributeCount);
        List<CGSResult> results = mine(data, parameters);
        CGSOutput.write(parameters.outputPath(), data, parameters, results);
        sampleMemory();
        totalTime = (System.nanoTime() - start) / 1000000;
    }

    /** Keep this method independent of file IO for small exhaustive regression checks. */
    public List<CGSResult> mine(CGSData data, CGSParameters parameters) {
        parameters.validate();
        this.data = data;
        this.parameters = parameters;
        maxMemory = 0;
        extensionCount = 0;
        containment.clear();
        candidates = new HashMap<>();
        index = new CGSIntersectionIndex(data, parameters.minvol);
        if (parameters.minsup <= data.graphs.size()
                && Math.max(parameters.minvol, parameters.mincom) <= data.vertexIds.length)
            enumerateTimes(new BitSet(), 0);
        sampleMemory();
        List<CGSResult> merged = mergeUpdate();
        // Edge witnesses are implicit in the time-intersection index, never merged blindly.
        List<CGSResult> results = removeRedundant(merged);
        results.sort(Comparator.comparingInt(CGSResult::support).reversed()
                .thenComparing(Comparator.comparingInt((CGSResult r) -> r.sequence.length()).reversed())
                .thenComparing(r -> r.sequence.key()).thenComparing(r -> r.startTimes().toString()));
        patternCount = results.size();
        intersectionCount = index.intersectionCount();
        fragmentCount = index.fragmentCount();
        sampleMemory();
        // A returned solution does not retain the potentially large private working index.
        candidates = null;
        index = null;
        containment.clear();
        return results;
    }

    private void enumerateTimes(BitSet starts, int next) {
        if (starts.cardinality() >= parameters.minsup) extendFrom(starts);
        for (int t = next; t < data.graphs.size(); t++) {
            if (starts.cardinality() + data.graphs.size() - t < parameters.minsup) break;
            starts.set(t);
            enumerateTimes(starts, t + 1);
            starts.clear(t);
        }
    }

    private void extendFrom(BitSet starts) {
        int length = data.graphs.size() - starts.length() + 1;
        List<List<CGSFragment>> layers = new ArrayList<>();
        for (int offset = 0; offset < length; offset++) {
            BitSet times = shifted(starts, offset);
            List<CGSFragment> layer = index.fragments(times);
            if (layer.isEmpty()) break;
            layers.add(layer);
        }
        if (layers.isEmpty()) return;
        // Every time combination can start a sequence, even after another extension succeeds.
        for (CGSFragment fragment : layers.get(0)) {
            if (fragment.ids.length < parameters.mincom) continue;
            List<CGSFragment> prefix = new ArrayList<>();
            prefix.add(fragment);
            extend(prefix, fragment.vertices, starts, layers);
        }
    }

    private void extend(List<CGSFragment> prefix, BitSet core, BitSet starts,
            List<List<CGSFragment>> layers) {
        CGSSequence pattern = new CGSSequence(prefix);
        Set<BitSet> times = candidates.computeIfAbsent(pattern, p -> new HashSet<>());
        times.add((BitSet) starts.clone());
        extensionCount++;
        if ((extensionCount & 1023) == 0) sampleMemory();
        // Saving every valid prefix also flushes terminal candidates at the end of the timeline.
        if (prefix.size() == layers.size()) return;
        for (CGSFragment fragment : layers.get(prefix.size())) {
            BitSet common = (BitSet) core.clone();
            common.and(fragment.vertices);
            if (common.cardinality() < parameters.mincom) continue;
            prefix.add(fragment);
            // All starts advance by the prefix length; the original starts stay unchanged.
            extend(prefix, common, starts, layers);
            prefix.remove(prefix.size() - 1);
        }
    }

    private List<CGSResult> mergeUpdate() {
        List<CGSResult> results = new ArrayList<>();
        for (Map.Entry<CGSSequence, Set<BitSet>> entry : candidates.entrySet()) {
            List<BitSet> occurrences = new ArrayList<>(entry.getValue());
            occurrences.sort(Comparator.comparingInt(BitSet::cardinality).reversed()
                    .thenComparing(BitSet::toString));
            List<BitSet> maximal = new ArrayList<>();
            for (BitSet starts : occurrences) {
                boolean covered = false;
                for (BitSet other : maximal) if (subset(starts, other)) { covered = true; break; }
                if (!covered) {
                    maximal.add(starts);
                    results.add(new CGSResult(entry.getKey(), starts));
                }
            }
        }
        return results;
    }

    private List<CGSResult> removeRedundant(List<CGSResult> merged) {
        Map<BitSet, List<CGSResult>> grouped = new HashMap<>();
        for (CGSResult result : merged)
            grouped.computeIfAbsent(result.startTimes(), t -> new ArrayList<>()).add(result);
        List<CGSResult> results = new ArrayList<>();
        for (List<CGSResult> group : grouped.values()) {
            // A strict specialization has greater total vertex/attribute weight.
            group.sort(Comparator.comparingLong((CGSResult r) -> r.sequence.weight).reversed()
                    .thenComparing(r -> r.sequence.key()));
            List<CGSResult> retained = new ArrayList<>();
            for (CGSResult candidate : group) {
                boolean redundant = false;
                for (CGSResult specific : retained) {
                    if (generalizes(candidate.sequence, specific.sequence)) { redundant = true; break; }
                }
                if (!redundant) retained.add(candidate);
            }
            results.addAll(retained);
            sampleMemory();
        }
        return results;
    }

    private boolean generalizes(CGSSequence general, CGSSequence specific) {
        for (int offset = 0; offset + general.length() <= specific.length(); offset++) {
            boolean included = true;
            for (int i = 0; i < general.length(); i++) {
                CGSFragment a = general.fragments[i], b = specific.fragments[offset + i];
                long pair = ((long) a.number << 32) | (b.number & 0xffffffffL);
                Boolean known = containment.get(pair);
                if (known == null) { known = a.isSubsetOf(b); containment.put(pair, known); }
                if (!known) { included = false; break; }
            }
            if (included) return true;
        }
        return false;
    }

    static BitSet shifted(BitSet times, int offset) {
        BitSet shifted = new BitSet();
        for (int t = times.nextSetBit(0); t >= 0; t = times.nextSetBit(t + 1)) shifted.set(t + offset);
        return shifted;
    }

    static boolean subset(BitSet first, BitSet second) {
        BitSet difference = (BitSet) first.clone();
        difference.andNot(second);
        return difference.isEmpty();
    }

    private void sampleMemory() {
        Runtime runtime = Runtime.getRuntime();
        maxMemory = Math.max(maxMemory, (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024));
    }

    public void printStats() {
        System.out.println("============= CGSMiner - STATS =============");
        System.out.println(" Raw timestamps: " + data.rawTimeCount);
        System.out.println(" Trend timestamps: " + data.graphs.size());
        System.out.println(" Attributes used: " + data.attributeNames.length + " (first mapped columns)");
        if (data.mappingAliases > 0)
            System.out.println(" Duplicate vertex mappings: " + data.mappingAliases + " (first name retained)");
        if (data.skippedTopologyRows > 0 || data.skippedTopologyNeighbors > 0)
            System.out.println(" Topology restricted to attributed vertices; skipped rows=" + data.skippedTopologyRows
                    + ", neighbor references=" + data.skippedTopologyNeighbors);
        if (data.ignoredTopologySnapshots > 0)
            System.out.println(" Extra topology snapshots beyond the attribute timeline: " + data.ignoredTopologySnapshots + " (ignored)");
        System.out.println(" Cached time intersections: " + intersectionCount);
        System.out.println(" Distinct component fragments: " + fragmentCount);
        System.out.println(" Total time: " + totalTime + " ms");
        System.out.printf(Locale.ROOT, " Maximum sampled heap usage: %.3f MB%n", maxMemory);
        System.out.println(" Pattern count: " + patternCount);
        System.out.println(" Output: " + parameters.outputPath());
        System.out.println("==========================================");
    }
}
