import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn import tree


def train_rf_classifier(
    input_train_data,
    target_train_data,
    n_estimators,
    criterion,
    max_leaf_nodes,
    max_depth=None,
    n_jobs=None,
    verbose=True,
):

    rf_classifier = RandomForestClassifier(
        n_estimators=n_estimators,
        criterion=criterion,
        max_depth=max_depth,
        max_leaf_nodes=max_leaf_nodes,
        n_jobs=n_jobs,
    )

    print("Training the random forest classifier...\n")

    rf_classifier.fit(input_train_data, target_train_data)

    if verbose:
        print_rf_classifier_params(rf_classifier)

    return rf_classifier


def extract_rf_classifier_params(rf_classifier):
    tree_objects = []
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

    rf_classifier_params = {
        "num_features": rf_classifier.n_features_,
        "num_classes": rf_classifier.n_classes_,
        "class_labels": list(map(int, rf_classifier.classes_)),
        "num_trees": rf_classifier.n_estimators,
        "trees": tree_objects,
    }

    return rf_classifier_params


def print_rf_classifier_params(rf_classifier):
    print("Random Forest Classifier Parameters:")
    print("Number of features = {0}".format(rf_classifier.n_features_))
    print("Number of classes = {0}".format(rf_classifier.n_classes_))
    print("Number of trees = {0}\n".format(rf_classifier.n_estimators))
