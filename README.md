# CGS-Shiled

This repository implements algorithms for mining and protecting frequent connected graph sequences.

## Requirements

1. **Java 8 or higher** is required to run the program.
2. Ensure that all necessary libraries and dependencies are set up in your project.

## Running the Program

### Step 1: Open the Project in IntelliJ IDEA

If you're using IntelliJ IDEA, simply open the project directory in the IDE. IntelliJ will automatically detect the `.java` files and compile them when you run the program. No need for a manual compilation step.

### Step 2: Run the `MainTestOriginal.java`

To run the program, execute the MainTestOriginal.java` file. This file is the entry point for mining frequent connected graph sequences.

In IntelliJ IDEA:
1. Right-click on the `MainTestOriginal.java` file.
2. Select **Run 'MainTestOriginal'** from the context menu.

Alternatively, you can use the **Run** button in the IDE toolbar after opening `MainTestOriginal.java`.

### Step 3: Configure the Parameters for the `MainTestOriginal.java`

Before running the program, you can configure the following parameters to customize the mining process:

- **discretizationThreshold**: The discretization threshold is an anti-interference coefficient to set trends. For example, if the value is 0.1, it means when (next_value - cur_value) >= 0.1, the trend is '+'. When (next_value - cur_value) <= -0.1, the trend is '-'. Otherwise, the trend is '='.
  
- **minsup**: The threshold for the support of connected graph sequences.

- **minvol**: The threshold for the segment size.

- **mincom**: The threshold for the core size.

- **attributeCount**: By default, attributeCount = -1 which means DBLP first 43 attributes, USFlight first 8, all mapped attributes otherwise.

### Example Command for the `MainTestOriginal.java`

To run the program with minimum support, the command would look like:

```bash
String inputDirectory = "Data/USFlight/";
String outputPath = "output.txt";
int minsup = 2;
int minvol = 1;
int mincom = 1;
float discretizationThreshold = 0.1f;
int attributeCount = -1;
```

### Step 4: Run the `MainTestCGSShield.java`

To run the program, execute the `MainTestCGSShield.java` file. This file is the entry point for anonymizing and evaluating risk functions.

### Step 5: Configure the Parameters for the `MainTestCGSShield.java`

Before running the program, you can configure the following parameters to customize the privacy protecting process:

- **R_1**: Individual identification risk
  
- **R_2**: Joint Matching risk

- **attackSimulations**: The number of attacks.

### Example Command for the `MainTestCGSShield.java`

To run the program with two risk functions, the command would look like:

```bash
String inputPath = "output.txt";
String inputDirectory = "Data/USFlight/";
String outputPath = "outputShield.txt";
double R_1 = 0.5; 
double R_2 = 0.5; 
int attackSimulations = 10000;

### Details
After running the `MainTestOriginal.java`, users can choose whether to run the `MainTestKAnomaly.java` to obtain CGSs under the privacy protection of the minimum candidate set size $K_{\min}$, the process is simialr to `MainTestCGSShield.java`.



