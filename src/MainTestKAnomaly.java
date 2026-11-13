public class MainTestKAnomaly {
    public static void main(String[] args) throws Exception {
        String inputPath = "output.txt";
        String inputDirectory = "Data/USFlight/";
        String outputPath = "outputKAnomaly.txt";

        int K_min = 2;
        int attackSimulations = 10000;
        long attackSeed = 20261025L;
        // Use the same half-attribute knowledge model and seed as MainTestCGSShield.java.
        boolean exactOnly = false;
        int exactRoleLimit = 12;
        int countingSamples = 128;
        long countingSeed = 73421L;

        CGSShieldParameters parameters = new CGSShieldParameters();
        parameters.inputPath = inputPath;
        parameters.inputDirectory = inputDirectory;
        parameters.outputPath = outputPath;
        parameters.kMin = K_min;
        parameters.attackSimulations = attackSimulations;
        parameters.attackSeed = attackSeed;
        parameters.exactOnly = exactOnly;
        parameters.exactRoleLimit = exactRoleLimit;
        parameters.countingSamples = countingSamples;
        parameters.countingSeed = countingSeed;
        // Attribute count and discretization threshold are inherited from the source output header.
        // This is Candidate-k Suppression from the document, not a formal k-anonymity guarantee.
        // Heap memory is sampled through publication only; attack simulation is excluded.
        new CGSShieldExperiment().run(parameters, false);
    }
}
