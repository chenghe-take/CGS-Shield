import java.util.*;

/** Cache intersections of time combinations and their maximal connected components. */
final class CGSIntersectionIndex {
    private final int minvol;
    private final List<CGSGraph> base;
    private final Map<BitSet, CGSGraph> graphs = new HashMap<>();
    private final Map<BitSet, List<CGSFragment>> fragments = new HashMap<>();
    private final Map<CGSFragment, CGSFragment> interned = new HashMap<>();

    CGSIntersectionIndex(CGSData data, int minvol) {
        this.minvol = minvol;
        base = new ArrayList<>();
        // Algorithm 1, line 1: retain only sufficiently large connected components.
        for (CGSGraph graph : data.graphs) base.add(graph.pruneSmallComponents(minvol));
    }

    List<CGSFragment> fragments(BitSet times) {
        List<CGSFragment> known = fragments.get(times);
        if (known != null) return known;
        CGSGraph graph = graph(times);
        List<CGSFragment> result = new ArrayList<>();
        for (BitSet component : graph.components(minvol)) {
            CGSFragment fragment = new CGSFragment(component, graph.labels);
            CGSFragment canonical = interned.get(fragment);
            if (canonical == null) {
                fragment.number = interned.size();
                interned.put(fragment, fragment);
                canonical = fragment;
            }
            result.add(canonical);
        }
        BitSet key = (BitSet) times.clone();
        fragments.put(key, Collections.unmodifiableList(result));
        return result;
    }

    CGSGraph graph(BitSet times) {
        if (times.isEmpty()) throw new IllegalArgumentException("An intersection needs at least one time");
        CGSGraph known = graphs.get(times);
        if (known != null) return known;
        int last = times.length() - 1;
        CGSGraph result;
        if (times.cardinality() == 1) result = base.get(last);
        else {
            BitSet previous = (BitSet) times.clone();
            previous.clear(last);
            result = graph(previous).intersect(base.get(last)).pruneSmallComponents(minvol);
        }
        graphs.put((BitSet) times.clone(), result);
        return result;
    }

    int intersectionCount() { return graphs.size(); }
    int fragmentCount() { return interned.size(); }
}
