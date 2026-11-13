import java.math.BigInteger;
import java.util.*;

/** Injective role-assignment counts: exact reductions first, reproducible SIS only when enabled. */
public final class CGSShieldMatching {
    static final class Count {
        final double logCount, logLower, logUpper, relativeSE;
        final boolean exact;
        Count(double value, double lower, double upper, double relativeSE, boolean exact) {
            this.logCount = value; this.logLower = lower; this.logUpper = upper;
            this.relativeSE = relativeSE; this.exact = exact;
        }
        static Count exact(BigInteger value) {
            double log = log(value);
            return new Count(log, log, log, 0, true);
        }
        static Count one() { return new Count(0,0,0,0,true); }
        Count times(Count other) {
            return new Count(logCount+other.logCount, logLower+other.logLower, logUpper+other.logUpper,
                    Math.hypot(relativeSE, other.relativeSE), exact && other.exact);
        }
    }

    static final class Bounds {
        final double lower, upper;
        Bounds(double lower, double upper) { this.lower = lower; this.upper = upper; }
    }

    private static final class Group {
        final BitSet candidates;
        final int roles;
        Group(BitSet candidates, int roles) { this.candidates = candidates; this.roles = roles; }
    }

    final CGSShieldParameters parameters;
    long exactComponents, estimatedComponents;
    double maxRelativeSE;
    int zeroSampleFallbacks;

    CGSShieldMatching(CGSShieldParameters parameters) { this.parameters = parameters; }

    Count count(BitSet[] candidates, long salt) {
        List<Group> groups = group(candidates);
        if (groups.isEmpty()) return Count.one();
        return reduce(groups, salt);
    }

    private Count reduce(List<Group> input, long salt) {
        List<Group> groups = new ArrayList<>(input);
        Count factor = Count.one();
        boolean changed;
        do {
            changed = false;
            BitSet forced = new BitSet();
            List<Group> remaining = new ArrayList<>();
            for (Group group : groups) {
                int size = group.candidates.cardinality();
                if (size < group.roles) throw new IllegalStateException("No injective full-data assignment; check provenance");
                if (size == group.roles) {
                    if (forced.intersects(group.candidates)) throw new IllegalStateException("Conflicting forced assignments");
                    forced.or(group.candidates);
                    factor = factor.times(Count.exact(factorial(group.roles)));
                    changed = true;
                } else remaining.add(group);
            }
            if (changed) {
                Map<BitSet,Integer> merged = new HashMap<>();
                for (Group group : remaining) {
                    BitSet available = (BitSet) group.candidates.clone();
                    available.andNot(forced);
                    merged.merge(available, group.roles, Integer::sum);
                }
                groups = sortedGroups(merged);
            }
        } while (changed && !groups.isEmpty());
        if (groups.isEmpty()) return factor;

        List<List<Group>> components = components(groups);
        if (components.size() > 1) {
            for (List<Group> component : components) factor = factor.times(reduce(component, salt));
            return factor;
        }
        BigInteger laminar = laminar(groups);
        if (laminar != null) { exactComponents++; return factor.times(Count.exact(laminar)); }
        if (groups.size() == 2) {
            exactComponents++;
            return factor.times(Count.exact(twoGroups(groups.get(0), groups.get(1))));
        }
        int roles = 0;
        for (Group group : groups) roles += group.roles;
        if (parameters.exactOnly || roles <= parameters.exactRoleLimit) {
            List<BitSet> expanded = new ArrayList<>();
            for (Group group : groups) for (int j = 0; j < group.roles; j++) expanded.add(group.candidates);
            expanded.sort(Comparator.comparingInt(BitSet::cardinality));
            BigInteger value = roles <= 16 ? subsetDP(expanded) : exact(expanded, 0, new BitSet(), new HashMap<>());
            if (value.signum() == 0) throw new IllegalStateException("No feasible role assignment");
            exactComponents++;
            return factor.times(Count.exact(value));
        }
        Count estimate = estimate(groups, salt);
        estimatedComponents++;
        maxRelativeSE = Math.max(maxRelativeSE, estimate.relativeSE);
        return factor.times(estimate);
    }

    /** Hall-safe lower bound: in any partial assignment at most i vertices have been used. */
    static Bounds quickBounds(BitSet[] candidates) {
        int[] sizes = new int[candidates.length];
        double upper = 0, lower = 0;
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = candidates[i].cardinality();
            if (sizes[i] == 0) throw new IllegalStateException("Empty compatible identity set");
            upper += Math.log(sizes[i]);
        }
        Arrays.sort(sizes);
        boolean positive = true;
        for (int i = 0; i < sizes.length; i++) {
            if (sizes[i] <= i) { positive = false; break; }
            lower += Math.log(sizes[i]-i);
        }
        // The provenance supplies at least one feasible assignment if this coarse bound vanishes.
        return new Bounds(positive ? lower : 0, upper);
    }

    private static BigInteger exact(List<BitSet> roles, int offset, BitSet used, Map<String,BigInteger> memo) {
        if (offset == roles.size()) return BigInteger.ONE;
        String key = offset + ":" + used;
        BigInteger known = memo.get(key);
        if (known != null) return known;
        BigInteger sum = BigInteger.ZERO;
        BitSet choices = (BitSet) roles.get(offset).clone();
        choices.andNot(used);
        if (offset == roles.size()-1) return BigInteger.valueOf(choices.cardinality());
        for (int v = choices.nextSetBit(0); v >= 0; v = choices.nextSetBit(v+1)) {
            used.set(v);
            sum = sum.add(exact(roles, offset+1, used, memo));
            used.clear(v);
        }
        memo.put(key, sum);
        return sum;
    }

    /** Rectangular permanent DP: process each candidate vertex once, without enumerating tuples. */
    private static BigInteger subsetDP(List<BitSet> roles) {
        BitSet union=new BitSet();
        for(BitSet candidates:roles) union.or(candidates);
        int full=(1<<roles.size())-1;
        BigInteger[] dp=new BigInteger[full+1];
        dp[0]=BigInteger.ONE;
        for(int vertex=union.nextSetBit(0);vertex>=0;vertex=union.nextSetBit(vertex+1)) {
            int membership=0;
            for(int r=0;r<roles.size();r++) if(roles.get(r).get(vertex)) membership|=1<<r;
            // Descending masks keep the smaller source states at their previous-vertex values.
            for(int mask=full;mask>0;mask--) {
                BigInteger value=dp[mask];
                for(int available=mask&membership;available!=0;available&=available-1) {
                    int bit=available&-available;
                    BigInteger previous=dp[mask^bit];
                    if(previous!=null) value=value==null?previous:value.add(previous);
                }
                dp[mask]=value;
            }
        }
        return dp[full]==null?BigInteger.ZERO:dp[full];
    }

    private Count estimate(List<Group> groups, long salt) {
        double upper = 0;
        for (Group group : groups) upper += logFalling(group.candidates.cardinality(), group.roles);
        int[][] assignment = matching(groups);
        // One complete matching certifies independent factorial permutations within equal groups.
        int maximumVertex = 0;
        for (Group group : groups) maximumVertex = Math.max(maximumVertex, group.candidates.length());
        int[] owner = new int[maximumVertex], pool = new int[groups.size()];
        Arrays.fill(owner, -1);
        for (int g = 0; g < groups.size(); g++) for (int v : assignment[g]) { owner[v] = g; pool[g]++; }
        // Allocate unused vertices to disjoint candidate pools, yielding a stronger certified lower bound.
        for (int v = 0; v < owner.length; v++) if (owner[v] < 0) {
            int best = -1;
            double gain = -1;
            for (int g = 0; g < groups.size(); g++) if (groups.get(g).candidates.get(v)) {
                double next = Math.log((double) (pool[g]+1)/(pool[g]+1-groups.get(g).roles));
                if (next > gain) { gain = next; best = g; }
            }
            if (best >= 0) { owner[v] = best; pool[best]++; }
        }
        double lower = 0;
        for (int g = 0; g < groups.size(); g++) lower += logFalling(pool[g], groups.get(g).roles);

        Random random = new Random(mix(parameters.countingSeed, salt ^ groupsHash(groups)));
        double logSum = Double.NEGATIVE_INFINITY, logSquares = Double.NEGATIVE_INFINITY;
        int samples = parameters.countingSamples;
        for (int sample = 0; sample < samples; sample++) {
            BitSet used = new BitSet();
            double weight = 0;
            for (Group group : groups) {
                BitSet available = (BitSet) group.candidates.clone();
                available.andNot(used);
                int size = available.cardinality();
                if (size < group.roles) { weight = Double.NEGATIVE_INFINITY; break; }
                weight += logFalling(size, group.roles);
                int[] values = available.stream().toArray();
                for (int i = 0; i < group.roles; i++) {
                    int selected = i + random.nextInt(size-i);
                    int v = values[selected]; values[selected] = values[i]; values[i] = v;
                    used.set(v);
                }
            }
            logSum = logAdd(logSum, weight);
            logSquares = logAdd(logSquares, 2*weight);
        }
        if (!Double.isFinite(logSum)) {
            zeroSampleFallbacks++;
            // This is explicitly reported as an interval-midpoint fallback, not an exact count.
            return new Count((lower+upper)/2, lower, upper, Double.POSITIVE_INFINITY, false);
        }
        double value = Math.max(lower, Math.min(upper, logSum-Math.log(samples)));
        double relativeSE = Math.sqrt(Math.max(0, Math.exp(Math.log(samples)+logSquares-2*logSum)-1)/(samples-1));
        return new Count(value, lower, upper, relativeSE, false);
    }

    private static List<Group> group(BitSet[] candidates) {
        Map<BitSet,Integer> grouped = new HashMap<>();
        for (BitSet set : candidates) grouped.merge((BitSet) set.clone(), 1, Integer::sum);
        return sortedGroups(grouped);
    }
    private static List<Group> sortedGroups(Map<BitSet,Integer> grouped) {
        List<Group> result = new ArrayList<>();
        for (Map.Entry<BitSet,Integer> entry : grouped.entrySet()) result.add(new Group(entry.getKey(), entry.getValue()));
        result.sort(Comparator.comparingInt((Group g) -> g.candidates.cardinality()-g.roles)
                .thenComparingInt(g -> g.candidates.cardinality()).thenComparing(g -> g.candidates.toString()));
        return result;
    }
    private static List<List<Group>> components(List<Group> groups) {
        int n = groups.size();
        int[] parent = new int[n];
        Map<Integer,Integer> first = new HashMap<>();
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            for (int v = groups.get(i).candidates.nextSetBit(0); v >= 0; v = groups.get(i).candidates.nextSetBit(v+1)) {
                Integer other = first.putIfAbsent(v, i);
                if (other != null) parent[find(parent,i)] = find(parent,other);
            }
        }
        Map<Integer,List<Group>> split = new TreeMap<>();
        for (int i = 0; i < n; i++) split.computeIfAbsent(find(parent,i), k -> new ArrayList<>()).add(groups.get(i));
        return new ArrayList<>(split.values());
    }
    private static int find(int[] parent, int value) {
        while (parent[value] != value) { parent[value] = parent[parent[value]]; value = parent[value]; }
        return value;
    }
    private static BigInteger laminar(List<Group> groups) {
        BigInteger count = BigInteger.ONE;
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            int consumed = 0;
            for (int j = 0; j < groups.size(); j++) if (i != j) {
                BitSet overlap = (BitSet) group.candidates.clone();
                overlap.and(groups.get(j).candidates);
                if (overlap.isEmpty()) continue;
                if (overlap.equals(groups.get(j).candidates)) consumed += groups.get(j).roles;
                else if (!overlap.equals(group.candidates)) return null;
            }
            int available = group.candidates.cardinality()-consumed;
            if (available < group.roles) return BigInteger.ZERO;
            count = count.multiply(falling(available, group.roles));
        }
        return count;
    }
    private static BigInteger twoGroups(Group first, Group second) {
        BitSet overlap = (BitSet) first.candidates.clone(); overlap.and(second.candidates);
        int common = overlap.cardinality(), only = first.candidates.cardinality()-common;
        BigInteger sum = BigInteger.ZERO;
        for (int chosen = Math.max(0, first.roles-only); chosen <= Math.min(common,first.roles); chosen++) {
            int remaining = second.candidates.cardinality()-chosen;
            if (remaining < second.roles) continue;
            sum = sum.add(choose(common,chosen).multiply(choose(only,first.roles-chosen))
                    .multiply(factorial(first.roles)).multiply(falling(remaining,second.roles)));
        }
        return sum;
    }

    /** Find one covering matching; its identity choices are never supplied to the attack evaluator. */
    private static int[][] matching(List<Group> groups) {
        List<Integer> roleGroups = new ArrayList<>();
        int size = 0;
        for (int g = 0; g < groups.size(); g++) {
            size = Math.max(size, groups.get(g).candidates.length());
            for (int r = 0; r < groups.get(g).roles; r++) roleGroups.add(g);
        }
        int[] owner = new int[size]; Arrays.fill(owner,-1);
        for (int r = 0; r < roleGroups.size(); r++)
            if (!augment(r, groups, roleGroups, owner, new BitSet())) throw new IllegalStateException("No covering matching");
        List<List<Integer>> assigned = new ArrayList<>();
        for (int g = 0; g < groups.size(); g++) assigned.add(new ArrayList<>());
        for (int v = 0; v < owner.length; v++) if (owner[v] >= 0) assigned.get(roleGroups.get(owner[v])).add(v);
        int[][] result = new int[groups.size()][];
        for (int g = 0; g < result.length; g++) result[g] = assigned.get(g).stream().mapToInt(Integer::intValue).toArray();
        return result;
    }
    private static boolean augment(int role, List<Group> groups, List<Integer> roleGroups, int[] owner, BitSet seen) {
        BitSet candidates = groups.get(roleGroups.get(role)).candidates;
        for (int v = candidates.nextSetBit(0); v >= 0; v = candidates.nextSetBit(v+1)) if (!seen.get(v)) {
            seen.set(v);
            if (owner[v] < 0 || augment(owner[v], groups, roleGroups, owner, seen)) { owner[v] = role; return true; }
        }
        return false;
    }

    /** A covering matching is unique iff it has no edge to a free vertex and no alternating cycle. */
    static boolean isUnique(BitSet[] candidates) {
        int size=0;
        for (BitSet set:candidates) size=Math.max(size,set.length());
        int[] owner=new int[size], assigned=new int[candidates.length];
        Arrays.fill(owner,-1);
        for(int r=0;r<candidates.length;r++)
            if(!augmentMatrix(r,candidates,owner,new BitSet())) throw new IllegalStateException("No attacker-compatible matching");
        for(int v=0;v<owner.length;v++) if(owner[v]>=0) assigned[owner[v]]=v;
        for(int r=0;r<candidates.length;r++)
            for(int v=candidates[r].nextSetBit(0);v>=0;v=candidates[r].nextSetBit(v+1))
                if(owner[v]<0) return false;
        int[] state=new int[candidates.length];
        for(int r=0;r<candidates.length;r++) if(state[r]==0 && cycle(r,candidates,owner,assigned,state)) return false;
        return true;
    }
    private static boolean augmentMatrix(int role,BitSet[] candidates,int[] owner,BitSet seen) {
        for(int v=candidates[role].nextSetBit(0);v>=0;v=candidates[role].nextSetBit(v+1)) if(!seen.get(v)) {
            seen.set(v);
            if(owner[v]<0 || augmentMatrix(owner[v],candidates,owner,seen)) {owner[v]=role;return true;}
        }
        return false;
    }
    private static boolean cycle(int role,BitSet[] candidates,int[] owner,int[] assigned,int[] state) {
        state[role]=1;
        for(int v=candidates[role].nextSetBit(0);v>=0;v=candidates[role].nextSetBit(v+1)) if(v!=assigned[role]) {
            int next=owner[v];
            if(state[next]==1 || (state[next]==0 && cycle(next,candidates,owner,assigned,state))) return true;
        }
        state[role]=2;
        return false;
    }

    static BigInteger factorial(int n) { return falling(n,n); }
    static BigInteger falling(int n, int k) {
        if (n < k) return BigInteger.ZERO;
        BigInteger result = BigInteger.ONE;
        for (int i = 0; i < k; i++) result = result.multiply(BigInteger.valueOf(n-i));
        return result;
    }
    private static BigInteger choose(int n, int k) {
        if (k < 0 || k > n) return BigInteger.ZERO;
        k = Math.min(k,n-k);
        BigInteger result = BigInteger.ONE;
        for (int i = 1; i <= k; i++) result = result.multiply(BigInteger.valueOf(n-i+1)).divide(BigInteger.valueOf(i));
        return result;
    }
    static double logFactorial(int n) { return logFalling(n,n); }
    static double logFalling(int n, int k) {
        if (n < k) return Double.NEGATIVE_INFINITY;
        double result = 0;
        for (int i = 0; i < k; i++) result += Math.log(n-i);
        return result;
    }
    static double log(BigInteger value) {
        if (value.signum() <= 0) throw new IllegalStateException("A valid pattern needs a positive assignment count");
        int shift = Math.max(0,value.bitLength()-53);
        return Math.log(value.shiftRight(shift).doubleValue()) + shift*Math.log(2);
    }
    static double logAdd(double a, double b) {
        if (a == Double.NEGATIVE_INFINITY) return b;
        if (b == Double.NEGATIVE_INFINITY) return a;
        double maximum = Math.max(a,b);
        return maximum + Math.log1p(Math.exp(Math.min(a,b)-maximum));
    }
    static long hash(String text) {
        long value = 0xcbf29ce484222325L;
        for (int i = 0; i < text.length(); i++) { value ^= text.charAt(i); value *= 0x100000001b3L; }
        return value;
    }
    static long mix(long first, long second) {
        long z = first ^ (second + 0x9e3779b97f4a7c15L);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
    private static long groupsHash(List<Group> groups) {
        long result = 0;
        for (Group group : groups) result = mix(result, hash(group.candidates.toString()) ^ group.roles);
        return result;
    }
}
