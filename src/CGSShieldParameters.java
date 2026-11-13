/** Shared post-processing and attack-evaluation configuration. */
public class CGSShieldParameters {
    public String inputPath = "output.txt";
    public String inputDirectory = "Data/DBLP/";
    public String outputPath = "outputShield.txt";
    public String internalAuditDirectory = "tmp/cgs-shield";
    public float discretizationThreshold = Float.NaN;
    public double r1Threshold = 0.8;
    public double r2Threshold = 0.8;
    public int kMin = 2;
    public int attackSimulations = 100;
    public long attackSeed = 20261025L;
    public long countingSeed = 73421L;
    public int exactRoleLimit = 12;
    public int countingSamples = 128;
    public boolean exactOnly = false;

    public void validate() {
        if (inputPath == null || inputDirectory == null || outputPath == null || internalAuditDirectory == null)
            throw new IllegalArgumentException("Input, dataset and output paths are required");
        for (double threshold : new double[] {r1Threshold, r2Threshold})
            if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1)
                throw new IllegalArgumentException("Risk thresholds must lie in [0,1]");
        if (kMin < 1 || attackSimulations < 0 || exactRoleLimit < 1 || countingSamples < 2)
            throw new IllegalArgumentException("Invalid k, simulation count, or counting configuration");
        if (!Float.isNaN(discretizationThreshold)
                && (!Float.isFinite(discretizationThreshold) || discretizationThreshold <= 0))
            throw new IllegalArgumentException("Use a positive discretization threshold or NaN to read the input header");
    }
}
