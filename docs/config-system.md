# Configuration system

This document describes the configuration system of the Random forest classifier project. Configurations are supplied as a JSON file to the build pipeline. Parameters will be used by the different stages of the pipeline and may be tweaked by the user. A default configuration file may be found [config/](../config/).

## Input configuration parameters

- `dataset`:
  Dataset to be used for training and testing the random forest classifier and generate decision trees.
  - Optional: No
  - Default value: NA
  - Value type: String
  - Expected values: "iris" or path to a .csv file
  - Consumed by: Software stage

- `input_headers`:
  Input headers are attribute names (csv headers) present in the dataset.
  - Optional: Yes, only expected if the dataset is a csv file.
  - Default value: NA
  - Value type: List of strings
  - Consumed by: Software stage

- `target_header`:
  Target header is the attribute name (csv headers) capturing the target classifications of the candidates in the dataset.
  - Optional: Yes, only expected if the dataset is a csv file.
  - Default value: NA
  - Value type: String
  - Consumed by: Software stage

- `train_split_size`:
  Proportion of candidates in the dataset to be used for training the classifier. The remaining candidates will be used for testing. Please refer to the [sklearn reference page](https://scikit-learn.org/stable/modules/generated/sklearn.model_selection.train_test_split.html) for more information (this parameter is same as the train_size parameter).
  - Optional: No
  - Default value: 0.7
  - Value type: Floating point
  - Expected values: Number between 0 and 1
  - Consumed by: Software stage

- `n_estimators`:
  Number of decision trees to be generated while training the classifier. Please refer to the [sklearn reference page](https://scikit-learn.org/stable/modules/generated/sklearn.ensemble.RandomForestClassifier.html) for more information.
  - Optional: No
  - Default value: 100
  - Value type: Integer
  - Consumed by: Software stage

- `max_leaf_nodes`:
  Maximum number of leaf nodes to be generated in each tree. Please refer to the [sklearn reference page](https://scikit-learn.org/stable/modules/generated/sklearn.ensemble.RandomForestClassifier.html) for more information.
  - Optional: Yes
  - Default value: None
  - Value type: Integer
  - Consumed by: Software stage

- `criterion`:
  The function to be used to measure the quality of a split while training the classifier. Please refer to the [sklearn reference page](https://scikit-learn.org/stable/modules/generated/sklearn.ensemble.RandomForestClassifier.html) for more information.
  - Optional: Yes
  - Default value: "gini"
  - Expected values: "gini", “entropy”, “log_loss”
  - Consumed by: Software stage

- `max_depth`:
  Maximum depth of each tree. Please refer to the [sklearn reference page](https://scikit-learn.org/stable/modules/generated/sklearn.ensemble.RandomForestClassifier.html) for more information.
  - Optional: Yes
  - Default value: None
  - Value type: Integer
  - Consumed by: Software stage

- `n_jobs`:
  The number of jobs to run in parallel during software training and testing. Please refer to the [sklearn reference page](https://scikit-learn.org/stable/modules/generated/sklearn.ensemble.RandomForestClassifier.html) for more information.
  - Optional: Yes
  - Default value: None
  - Value type: Integer
  - Consumed by: Software stage

- `build_type`:
  The type of build to be executed by the build pipeline. Please refer to the [build system documentation](build-system.md) for more information.
  - Optional: No
  - Default value: "test"
  - Value type: String
  - Expected values: "test" or "production"
  - Consumed by: All stages

- `build_target`:
  Decides what is to be done by the run stage in build pipeline. Please refer to the [build system documentation](build-system.md) for more information. "sim" corrosponds to the simulation build target and "synth" corrosponds to the synthesis build target.
  - Optional: No
  - Default value: "sim"
  - Value type: String
  - Expected values: "sim" or "synth"
  - Consumed by: Run stage

- `fixed_point_width`:
  Width (number of bits) of fixed point numbers in the hardware. An adequate width must be provided to properly accomodate all generated values from the software. Otherwise a java.lang.IllegalArgumentException will be thrown when compiling chisel to verilog.
  - Optional: No
  - Default value: NA
  - Value type: Integer
  - Consumed by: Hardware stage

- `fixed_point_bp`:
  Position of the binary point of the fixed point numbers in hardware. An adequate binary point must be provided to properly accomodate all generated values from the software. Otherwise a java.lang.IllegalArgumentException will be thrown when compiling chisel to verilog.
  - Optional: No
  - Default value: NA
  - Value type: Integer
  - Consumed by: Hardware stage

- `opt_majority_voter`:
  The optimization to be applied to the majority voter. It can be optimized for area or time.
***NOTE: Currently only area-friendly majority voter supported. Time-friendly majority voter will be added in the future.***
  - Optional: Yes
  - Default value: "area"
  - Value type: String
  - Expected values: "area" or "time"
  - Consumed by: Hardware stage
