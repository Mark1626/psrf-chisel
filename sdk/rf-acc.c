#include "rf-acc.h"
#include <stdint.h>
#include <stdlib.h>

rf_acc_t* rf_init(rf_error_codes *res,
    int num_features,
    int num_classes,
    int num_trees,
    int num_nodes,
    int depth) {

    int result = 0;

    // TODO: Do we have individual return and indiviual error message
    result = result || (num_features > rf_acc_meta_max_features);
    result = result || (num_classes > rf_acc_meta_max_classes);
    result = result || (num_trees > rf_acc_meta_max_trees);
    result = result || (num_nodes > rf_acc_meta_max_nodes);
    result = result || (depth > rf_acc_meta_max_depth);

    *res = result;
    if (result) {
        *res = ARGUMENT_GREATER_THAN_MAX_SUPPORTED;
        return NULL;
    }

    volatile uint64_t *csr_ptr = (volatile uint64_t *) rf_acc_csr_address;
    csr_ptr[3] = num_trees;

    rf_acc_t *self = malloc(sizeof(rf_acc_t));
    if (!self) {
        *res = MALLOC_ERROR;
        return NULL;
    }

    self->num_features = num_features;
    self->num_classes = num_classes;
    self->num_trees = num_trees;
    self->num_nodes = num_nodes;
    self->depth = depth;

    *res = RF_SUCCESS;
    return self;
}

int rf_delete(rf_acc_t *self) {
    if (self) {
        free(self);
    }
    return 0;
}

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

rf_hw_node_t convert_to_hw_node(const rf_node_t *node) {
    rf_hw_node_t hw_node = 0;

    hw_node += ((uint64_t)(node->is_leaf) << 63);
    hw_node += ((uint64_t)(node->feature) << 54);
    hw_node += ((uint64_t)toFixedPoint(node->threshold) << 22);
    hw_node += ((uint64_t)node->left << 11);
    hw_node += ((uint64_t)node->right);

    return hw_node;
}

int rf_store_weights(rf_acc_t *self, const rf_node_t *node, const int size, const int* offsets, const int offsetSize) {
    volatile uint64_t *spad_ptr = (volatile uint64_t *) rf_acc_scratchpad_address;

    // TODO: Return enum maybe
    if (offsetSize > 127) {
        return 1;
    }

    for (int i=0; i < offsetSize; i++) {
        spad_ptr[i] = offsets[i];
    }

    // Start address of weights
    for (int i = 0; i < size; i++) {
        uint64_t hw_weight = convert_to_hw_node(&node[i]);
        spad_ptr[128 + i] = hw_weight;
    }
    return 0;
}

int rf_classify(rf_acc_t *self, float *candidates, int size) {
    volatile uint64_t *csr_ptr = (volatile uint64_t *) rf_acc_csr_address;
    // TODO: Change this
    if (!csr_ptr[0]) {
        for (int i=0; i < self->num_features-1; i++) {
            csr_ptr[1] = toFixedPoint(candidates[i]);
        }
        // Mark last to start the computation
        uint64_t val = (toFixedPoint(candidates[self->num_features-1]));
        val += 1LL << 50;
        csr_ptr[1] = val;

        while (!(csr_ptr[0] & 1)) { continue; };

        return csr_ptr[2];
    }
    return -1;
}

