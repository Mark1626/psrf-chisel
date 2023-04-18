#ifndef RF_ACC_H
#define RF_ACC_H

#include <stdint.h>

static const int rf_acc_csr_address = 0x1100;
static const int rf_acc_scratchpad_address = 0x2000;

static const int rf_acc_meta_max_features = 10;
static const int rf_acc_meta_max_classes = 10;
static const int rf_acc_meta_max_trees = 100;
static const int rf_acc_meta_max_nodes = 1000;
static const int rf_acc_meta_max_depth = 16;
static const int rf_acc_fixed_point_width = 32;
static const int rf_acc_fixed_point_bp_width = 16;

typedef struct {
    int num_features;
    int num_classes;
    int num_trees;
    int num_nodes;
    int depth;
} rf_acc_t;

typedef struct {
  int is_leaf;
  int feature;
  float threshold;
  int left;
  int right;
} rf_node_t;

typedef uint64_t rf_hw_node_t;

typedef enum {
    ARGUMENT_GREATER_THAN_MAX_SUPPORTED,
    MALLOC_ERROR,
    RF_SUCCESS
} rf_error_codes;

rf_acc_t* rf_init(rf_error_codes *res,
    int num_features,
    int num_classes,
    int num_trees,
    int num_nodes,
    int depth);

int rf_delete(rf_acc_t *self);
int rf_store_weights(rf_acc_t *self, const rf_node_t *node, const int size, const int* offsets, const int offsetSize);

int rf_classify(rf_acc_t *self, float *candidates, int size);

#endif
