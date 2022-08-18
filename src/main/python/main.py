from sklearn.datasets import load_iris
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score

import argparse

import rf_train
import rw_json
import constants as cn


def main(args):

    # Load input configuration JSON file
    if args.verbose:
        print("Loading input configuration file: {0}\n".format(args.config))

    config = rw_json.read_json_file(args.config)

    # Load dataset
    # TODO Currently only iris dataset is supported. Add support for other
    # datasets in csv files with user options for data and target headers
    dataset = config.get("dataset")
    print("Loading dataset...\n")
    input_data = []
    target_data = []

    if dataset == "iris":
        iris = load_iris()
        input_data = iris.data
        target_data = iris.target
    else:
        print("Unsupported dataset: {0}\n".format(dataset))
        exit(1)

    # Split dataset into training and testing subsets
    train_split_size = config.pop("train_split_size", cn.DEFAULT_TRAIN_SPLIT_SIZE)

    input_train, input_test, target_train, target_test = train_test_split(
        input_data, target_data, train_size=train_split_size
    )

    if args.verbose:
        print("Number of samples for training = {0}".format(len(input_train)))
        print("Number of samples for testing = {0}\n".format(len(input_test)))

    # Train a random forest classifier based on input parameters
    rf_classifier = rf_train.train_rf_classifier(
        input_train_data=input_train,
        target_train_data=target_train,
        n_estimators=config.pop("n_estimators", cn.DEFAULT_N_ESTIMATORS),
        criterion=config.pop("criterion", cn.DEFAULT_CRITERION),
        max_leaf_nodes=config.pop("max_leaf_nodes", cn.DEFAULT_MAX_LEAF_NODES),
        max_depth=config.pop("max_depth", cn.DEFAULT_MAX_DEPTH),
        n_jobs=config.pop("n_jobs", cn.DEFAULT_N_JOBS),
        verbose=args.verbose,
    )

    # Predict expected test output with test data
    pred_test = rf_classifier.predict(input_test)

    # Calculate accuracy of trained classifier
    if args.verbose:
        print("Calculating accuracy of trained Random Forest Classifier...")

    accuracy = accuracy_score(target_test, pred_test)
    print("Accuracy of Random Forest Classifier: {0}\n".format(accuracy))

    # Extract necessary data and parameters from the trained random forest
    # classifier to be tranmitted to HW stage
    toHWStage_config = rf_train.extract_rf_classifier_params(rf_classifier)

    build_type = config.pop("build_type", cn.DEFAULT_BUILD_TYPE)
    if build_type == "test":
        toHWStage_config["test_candidates"] = input_test.tolist()
        toHWStage_config["expected_classifications"] = pred_test.tolist()
    else:
        print("Unsupported build type: {0}\n".format(build_type))
        exit(1)

    toHWStage_config["build_type"] = build_type
    toHWStage_config["build_target"] = config.pop(
        "build_target", cn.DEFAULT_BUILD_TARGET
    )

    # Append remaining config entries to hardware stage config
    toHWStage_config.update(config)

    # Write output JSoN file to be used by HW layer
    if args.verbose:
        print("Writing generated output file: {0}\n".format(args.out))

    rw_json.write_json_file(toHWStage_config, args.out)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Random Forest Classifier Training Script"
    )
    parser.add_argument(
        "-c", "--config", action="store", help="Path to config JSON file"
    )
    parser.add_argument(
        "-o", "--out", action="store", help="Path to generated output JSON file"
    )
    parser.add_argument("-v", "--verbose", action="store_true", help="Verbose output")
    args = parser.parse_args()
    main(args)
