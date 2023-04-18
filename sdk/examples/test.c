#include "rf-acc.h"
#include <stdlib.h>

static int64_t roundi(double x)
{
  if (x < 0.0) {
    return (int64_t)(x - 0.5);
  } else {
    return (int64_t)(x + 0.5);
  }
}

static int64_t toFixedPoint(float x) {
    double BP_SCALE = ((double)(1<<rf_acc_fixed_point_bp_width));
    return roundi(x * BP_SCALE);
}

int main() {
    int num_trees = 1;
    int num_features = 2;
    int num_classes = 2;
    int depth = 16;
    int num_nodes = 2;
    rf_error_codes res;

    rf_acc_t *acc = rf_init(&res,
        num_features,
        num_classes,
        num_trees,
        num_nodes,
        depth);

    rf_node_t *weights = malloc(num_nodes * sizeof(rf_node_t));
    weights[0].is_leaf = 0;
    weights[0].feature = 1;
    weights[0].threshold = 0.5;
    weights[0].left = 1;
    weights[0].right = 1;

    weights[1].is_leaf = 1;
    weights[1].feature = 2;
    weights[1].threshold = 0;
    weights[1].left = 1;
    weights[1].right = 1;

    rf_store_weights(acc, weights, num_nodes);

    volatile uint64_t *csr_ptr = (volatile uint64_t *) rf_acc_csr_address;

    float *candidates = malloc(2 * sizeof(float));
    candidates[0] = 0.0f;
    candidates[1] = 0.5f;

    int decision = rf_classify(acc, candidates, 2);

    printf("Decision: %d\n", decision);

    free(weights);
    rf_delete(acc);
}