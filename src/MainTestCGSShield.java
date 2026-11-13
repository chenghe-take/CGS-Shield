public class MainTestCGSShield {
    public static void main(String[] args) throws Exception {
        String inputPath = "output.txt";
        String inputDirectory = "Data/USFlight/";
        String outputPath = "outputShield.txt";

        double R_1 = 0.5; // Per-role candidate-count risk.
        double R_2 = 0.5; // Joint distinct-identity assignment risk.
        int attackSimulations = 10000;
        long attackSeed = 20261025L;
        // Each vertex/time reveals ceil(d/2) different trends, where d is the selected schema size.
        boolean exactOnly = false;
        int exactRoleLimit = 12;
        int countingSamples = 128;
        long countingSeed = 73421L;

        CGSShieldParameters parameters = new CGSShieldParameters();
        parameters.inputPath = inputPath;
        parameters.inputDirectory = inputDirectory;
        parameters.outputPath = outputPath;
        parameters.r1Threshold = R_1;
        parameters.r2Threshold = R_2;
        parameters.attackSimulations = attackSimulations;
        parameters.attackSeed = attackSeed;
        parameters.exactOnly = exactOnly;
        parameters.exactRoleLimit = exactRoleLimit;
        parameters.countingSamples = countingSamples;
        parameters.countingSeed = countingSeed;
        // Read the attribute count and discretization threshold from the source output header.
        // exactOnly=true disables estimated matching counts and can be expensive on large fragments.
        // Heap memory is sampled through publication only; attack simulation is excluded.
        new CGSShieldExperiment().run(parameters, true);
    }
}
