import java.util.*;

/** Immutable Connected Graph Sequence of concrete attributed vertices. */
public final class CGSSequence {
    final CGSFragment[] fragments;
    final long weight;
    private final int hash;

    CGSSequence(List<CGSFragment> fragments) {
        this.fragments = fragments.toArray(new CGSFragment[0]);
        long total = 0;
        for (CGSFragment fragment : fragments) total += fragment.weight;
        weight = total;
        hash = Arrays.hashCode(this.fragments);
    }

    public int length() { return fragments.length; }
    public int volume() {
        int volume = Integer.MAX_VALUE;
        for (CGSFragment fragment : fragments) volume = Math.min(volume, fragment.ids.length);
        return volume;
    }
    public int commonVolume() {
        BitSet core = (BitSet) fragments[0].vertices.clone();
        for (int i = 1; i < fragments.length; i++) core.and(fragments[i].vertices);
        return core.cardinality();
    }

    public String display(CGSData data) {
        StringBuilder text = new StringBuilder("{");
        for (CGSFragment fragment : fragments) {
            text.append('(');
            for (int i = 0; i < fragment.ids.length; i++) {
                if (i > 0) text.append("| ");
                text.append(data.vertexNames[fragment.ids[i]]).append(": ");
                BitSet items = fragment.labels[i];
                boolean first = true;
                for (int item = items.nextSetBit(0); item >= 0; item = items.nextSetBit(item + 1)) {
                    if (!first) text.append(", ");
                    text.append(data.attributeNames[item / 2]).append(item % 2 == 0 ? '-' : '+');
                    first = false;
                }
            }
            text.append(')');
        }
        return text.append('}').toString();
    }

    String key() {
        StringBuilder text = new StringBuilder();
        for (CGSFragment fragment : fragments) {
            text.append('(');
            for (int i = 0; i < fragment.ids.length; i++)
                text.append(fragment.ids[i]).append(':').append(fragment.labels[i]).append(';');
            text.append(')');
        }
        return text.toString();
    }

    @Override public int hashCode() { return hash; }
    @Override public boolean equals(Object value) {
        return value instanceof CGSSequence && Arrays.equals(fragments, ((CGSSequence) value).fragments);
    }
}
