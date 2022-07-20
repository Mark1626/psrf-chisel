import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn import tree


def train_rf_classifier(
    input_data,
    target_data,
    train_split_size=None,
    n_estimators=None,
    criterion=None,
    max_depth=None,
    max_leaf_nodes=None,
    n_jobs=None,
    verbose=True,
):
    train_split = 0.7 if train_split_size is None else train_split_size
    n_estimators = 100 if n_estimators is None else n_estimators
    criterion = "gini" if criterion is None else criterion

    input_train, input_test, target_train, target_test = train_test_split(
        input_data, target_data, train_size=train_split_size
    )

    if verbose:
        print("Number of samples for training = {0}".format(len(input_train)))
        print("Number of samples for testing = {0}\n".format(len(input_test)))

    rf_classifier = RandomForestClassifier(
        n_estimators=n_estimators,
        criterion=criterion,
        max_depth=max_depth,
        max_leaf_nodes=max_leaf_nodes,
        n_jobs=n_jobs,
    )

    print("Training the random forest classifier...\n")

    rf_classifier.fit(input_train, target_train)

    if verbose:
        print_rf_classifier_params(rf_classifier)

    return rf_classifier


def extract_rf_classifier_params(rf_classifier):
    tree_objects = []
    num_nodes = []
    for i in range(0, rf_classifier.n_estimators):
        tree = rf_classifier.estimators_[i].tree_
        classes = [int(np.argmax(val)) for val in tree.value]
        is_leaf = [1 if child_left == -1 else 0 for child_left in tree.children_left]
        tree_object = {
            "is_leaf": is_leaf,
            "features": list(map(int, tree.feature)),
            "classes": classes,
            "threshold": tree.threshold.tolist(),
            "children_left": list(map(int, tree.children_left)),
            "children_right": list(map(int, tree.children_right)),
        }
        tree_objects.append(tree_object)
        num_nodes.append(tree.node_count)

    rf_classifier_params = {
        "num_features": rf_classifier.n_features_,
        "num_classes": rf_classifier.n_classes_,
        "class_labels": list(map(int, rf_classifier.classes_)),
        "num_trees": rf_classifier.n_estimators,
        "num_nodes": num_nodes,
        "trees": tree_objects,
    }

    return rf_classifier_params


def print_rf_classifier_params(rf_classifier):
    print("Random Forest Classifier Parameters:")
    print("Number of features = {0}".format(rf_classifier.n_features_))
    print("Number of classes = {0}".format(rf_classifier.n_classes_))
    print("Number of trees = {0}\n".format(rf_classifier.n_estimators))
