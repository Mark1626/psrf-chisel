from sklearn.datasets import load_iris
import argparse

import rf_train
import rw_json


def main(args):

    if args.verbose:
        print("Loading input configuration file: {0}\n".format(args.config))

    config = rw_json.read_json_file(args.config)
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

    rf_classifier = rf_train.train_rf_classifier(
        input_data=input_data,
        target_data=target_data,
        train_split_size=config.get("train_split_size"),
        n_estimators=config.get("n_estimators"),
        criterion=config.get("criterion"),
        max_depth=config.get("max_depth"),
        max_leaf_nodes=config.get("max_leaf_nodes"),
        n_jobs=config.get("n_jobs"),
        verbose=args.verbose,
    )

    rf_classifier_params = rf_train.extract_rf_classifier_params(rf_classifier)

    if args.verbose:
        print("Writing generated output file: {0}\n".format(args.out))

    rw_json.write_json_file(rf_classifier_params, args.out)


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
