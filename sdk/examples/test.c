#include "../rf-acc.h"

int main() {
    int num_trees = 10;
    int num_features = 10;
    int num_classes = 4;
    int depth = 16;
    int num_nodes = 1000;
    rf_error_codes res;

    rf_acc_t *acc = rf_init(&res,
        num_features,
        num_classes,
        num_trees,
        num_nodes,
        depth);

    
    const int size = 100;
    rf_node_t *weights = malloc(size * sizeof(rf_node_t));

    rf_store_weights(acc, weights, size);

    float *candidates = malloc(10 * sizeof(float));

    int res = rf_classify(candidates);
}