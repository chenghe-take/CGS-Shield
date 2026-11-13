import java.util.*;

/** A concrete vertex set with its common trend labels; edges remain in the intersection index. */
public final class CGSFragment {
    final BitSet vertices;
    final int[] ids;
    final BitSet[] labels;
    final long weight;
    final int hash;
    int number;

    CGSFragment(BitSet vertices, BitSet[] graphLabels) {
        this.vertices = (BitSet) vertices.clone();
        ids = new int[vertices.cardinality()];
        labels = new BitSet[ids.length];
        long total = ids.length;
        int index = 0;
        for (int v = vertices.nextSetBit(0); v >= 0; v = vertices.nextSetBit(v + 1)) {
            ids[index] = v;
            labels[index] = (BitSet) graphLabels[v].clone();
            total += labels[index].cardinality();
            index++;
        }
        weight = total;
        hash = 31 * Arrays.hashCode(ids) + Arrays.hashCode(labels);
    }

    /** Attribute containment is tested on original identities, never on interchangeable labels. */
    boolean isSubsetOf(CGSFragment other) {
        if (ids.length > other.ids.length || weight > other.weight) return false;
        int j = 0;
        for (int i = 0; i < ids.length; i++) {
            while (j < other.ids.length && other.ids[j] < ids[i]) j++;
            if (j == other.ids.length || other.ids[j] != ids[i]) return false;
            BitSet difference = (BitSet) labels[i].clone();
            difference.andNot(other.labels[j]);
            if (!difference.isEmpty()) return false;
        }
        return true;
    }

    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object value) {
        if (!(value instanceof CGSFragment)) return false;
        CGSFragment other = (CGSFragment) value;
        return Arrays.equals(ids, other.ids) && Arrays.equals(labels, other.labels);
    }
}
