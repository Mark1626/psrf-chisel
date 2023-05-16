#include "rf-acc.h"
#include <assert.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>

typedef struct {
  int num_trees;
  int num_features;
  int num_classes;
  int depth;
  int num_nodes;
} max_counts;

max_counts *counts_init(int num_trees, int num_features, int num_classes,
                        int depth, int num_nodes) {
  max_counts *counts = (max_counts *)malloc(sizeof(max_counts));
  counts->num_trees = num_trees;
  counts->num_features = num_features;
  counts->num_classes = num_classes;
  counts->depth = depth;
  counts->num_nodes = num_nodes;
  return counts;
}

int run_classification(max_counts *counts, rf_node_t *weights, int *offsets,
             float *candidates,rf_error_codes *res) {

  rf_acc_t *acc = rf_init(res, counts->num_features, counts->num_classes,
                          counts->num_trees, counts->num_nodes, counts->depth);

  if (acc == NULL) {
    return -1;
  }

  rf_store_weights(acc, weights, counts->num_nodes, offsets, counts->num_trees);

  volatile uint64_t *csr_ptr = (volatile uint64_t *)rf_acc_csr_address;

  int decision = rf_classify(acc, candidates, counts->num_features);


  rf_delete(acc);
  return decision;
}

int test_should_be_able_to_run_complete_model() {
  int expected_decisions[] = {2,2,1,0,1};
  max_counts *counts = counts_init(21, 10, 3, 10, 103);

  float candidates[][10] = {{10.52178765, 6.07072253, -1.99584827, 6.4549465,
                              -8.63472683, 1.25364933, -5.94490446, 9.21032095,
                              1.35782526,  -1.38803355},{9.533084026427785, 4.815077786593274, -0.24713609440960937, 5.43903719450686, -6.862720931407669, 3.621924580514208, -4.969698302538382, 10.229906290428069, 0.06778459705899037, -1.9461403777654556},{6.001174257025821, 1.212929831950195, 3.744035996742588, 9.456412252843634, -9.49210106148642, -7.140397717873332, -10.911539461705006, 6.190812306144052, 5.494893413672378, 9.113585686585749},{2.1156707630897955, 3.0689615070947376, 2.457609162610427, 0.21285356899762364, -2.397701162403787, 2.3390325965687073, -1.559808306873523, 7.891625357871339, 8.10810536923723, -1.4303431365302584},{6.0427757397301525, 1.5545374315418015, 1.7172576190530475, 9.218505934045169, -8.56877881532144, -6.4715435120633495, -9.468719958489865, 7.054386274403462, 7.446285716053263, 6.052483903793935}};

  int offsets[] = {0,  5,  10, 15, 20, 25, 28, 33, 38, 43, 48,
                         53, 58, 63, 68, 73, 78, 83, 88, 93, 98};

  rf_node_t weights[] = {{0, 6, -3.5963168144226074, 1, 4},
                          {0, 0, 8.24571704864502, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {1, 0, -2.0, -1, -1},
                          {0, 6, -3.5963168144226074, 1, 4},
                          {0, 7, 8.492016315460205, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {1, 0, -2.0, -1, -1},
                          {0, 1, 3.6606953144073486, 1, 4},
                          {0, 9, 2.311070442199707, 1, 2},
                          {1, 0, -2.0, -1, -1},
                          {1, 1, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {0, 0, 3.5444729328155518, 1, 2},
                          {1, 0, -2.0, -1, -1},
                          {0, 7, 8.212862253189087, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {0, 9, 2.311070442199707, 1, 4},
                          {0, 0, 5.8243772983551025, 1, 2},
                          {1, 0, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {1, 1, -2.0, -1, -1},
                          {0, 1, 3.6606953144073486, 1, 4},
                          {0, 4, -5.579895377159119, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {1, 0, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {0, 7, 8.492016315460205, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {0, 2, 0.021293260157108307, 1, 2},
                          {1, 2, -2.0, -1, -1},
                          {0, 4, -5.579895377159119, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {1, 0, -2.0, -1, -1},
                          {0, 5, -2.608946979045868, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {0, 4, -5.0099116563797, 1, 2},
                          {1, 2, -2.0, -1, -1},
                          {1, 0, -2.0, -1, -1},
                          {0, 9, 2.311070442199707, 1, 4},
                          {0, 1, 3.4143649339675903, 1, 2},
                          {1, 0, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {1, 1, -2.0, -1, -1},
                          {0, 0, 3.5444729328155518, 1, 2},
                          {1, 0, -2.0, -1, -1},
                          {0, 0, 7.485761880874634, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {0, 6, -8.427025079727173, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {0, 6, -3.5963168144226074, 1, 2},
                          {1, 2, -2.0, -1, -1},
                          {1, 0, -2.0, -1, -1},
                          {0, 0, 3.5444729328155518, 1, 2},
                          {1, 0, -2.0, -1, -1},
                          {0, 5, -2.9433740973472595, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {0, 0, 3.202361047267914, 1, 2},
                          {1, 0, -2.0, -1, -1},
                          {0, 6, -7.630578279495239, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {0, 8, 3.0755221843719482, 1, 2},
                          {1, 2, -2.0, -1, -1},
                          {0, 2, 1.5599865913391113, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {1, 0, -2.0, -1, -1},
                          {0, 3, 4.542865514755249, 1, 2},
                          {1, 0, -2.0, -1, -1},
                          {0, 5, -2.5772504210472107, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {0, 3, 6.900667667388916, 1, 4},
                          {0, 6, -3.2647533416748047, 1, 2},
                          {1, 2, -2.0, -1, -1},
                          {1, 0, -2.0, -1, -1},
                          {1, 1, -2.0, -1, -1},
                          {0, 0, 7.0444581508636475, 1, 4},
                          {0, 5, -2.837233304977417, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {1, 0, -2.0, -1, -1},
                          {1, 2, -2.0, -1, -1},
                          {0, 6, -7.4341206550598145, 1, 2},
                          {1, 1, -2.0, -1, -1},
                          {0, 2, 0.1561131477355957, 1, 2},
                          {1, 2, -2.0, -1, -1},
                          {1, 0, -2.0, -1, -1},
                          {0, 3, 7.740272045135498, 1, 4},
                          {0, 8, 4.095539093017578, 1, 2},
                          {1, 2, -2.0, -1, -1},
                          {1, 0, -2.0, -1, -1},
                          {1, 1, -2.0, -1, -1},
                          {0, 4, -5.0099116563797, 1, 4},
                          {0, 2, 0.021293260157108307, 1, 2},
                          {1, 2, -2.0, -1, -1},
                          {1, 1, -2.0, -1, -1},
                          {1, 0, -2.0, -1, -1}};


  rf_error_codes res;

  for (int i = 0; i < 5; i++) {
    int decision = run_classification(counts, weights, offsets, candidates[i],&res);
    if (decision != expected_decisions[i]) {
      printf("idx: %d Expected: %d actual: %d \n", i, expected_decisions[i], decision);
      printf("- Assertion faild at simple test case using 21 trees;\n");
    }
    assert(decision == expected_decisions[i]);
  }

  printf("PASS - simple test case using 21 trees\n");
  return 0;
}

int test_should_return_error_when_trees_exceed_max() {
  max_counts *counts = counts_init(101, 10, 3, 10, 103);

  // weights,offsets,candidates are not needed as we are only intrested in count;
  float candidates[]= {};
  int offsets[] = {};
  rf_node_t weights[] = {};

  rf_error_codes res;
  int decision = run_classification(counts, weights, offsets, candidates, &res);
  
  if (res != ARGUMENT_GREATER_THAN_MAX_SUPPORTED) {
    printf("FAILED - Error not thrown when tree count is exceeding max\n");
  }

  assert(decision == -1);
  assert(res == ARGUMENT_GREATER_THAN_MAX_SUPPORTED);

  printf("PASS - tree count execding max\n");
  return 0;
}

int test_should_return_error_when_features_exceed_max() {
  max_counts *counts = counts_init(10, 11, 3, 10, 103);

  // weights,offsets,candidates are not needed as we are only intrested in count;
  float candidates[]= {};
  int offsets[] = {};
  rf_node_t weights[] = {};

  rf_error_codes res;
  int decision = run_classification(counts, weights, offsets, candidates,&res);

  if (res != ARGUMENT_GREATER_THAN_MAX_SUPPORTED) {
    printf("FAILED - Error not thrown when features count is exceeding max\n");
  }

  assert(decision == -1);
  assert(res == ARGUMENT_GREATER_THAN_MAX_SUPPORTED);

  printf("PASS - features count execding max\n");
  return 0;
}

int test_should_return_error_when_classes_exceed_max() {
  max_counts *counts = counts_init(10, 10, 12, 10, 103);

  // weights,offsets,candidates are not needed as we are only intrested in count;
  float candidates[]= {};
  int offsets[] = {};
  rf_node_t weights[] = {};

  rf_error_codes res;
  int decision = run_classification(counts, weights, offsets, candidates, &res);
  
  if (res != ARGUMENT_GREATER_THAN_MAX_SUPPORTED) {
    printf("FAILED - Error not thrown when classes count is exceeding max\n");
  }
  
  assert(decision == -1);
  assert(res == ARGUMENT_GREATER_THAN_MAX_SUPPORTED);

  printf("PASS - classes count execding max\n");
  return 0;
}

int test_should_return_error_when_nodes_exceed_max() {
  max_counts *counts = counts_init(10, 10, 12, 10, 1001);

  // weights,offsets,candidates are not needed as we are only intrested in count;
  float candidates[]= {};
  int offsets[] = {};
  rf_node_t weights[] = {};

  rf_error_codes res;
  int decision = run_classification(counts, weights, offsets, candidates, &res);
  
  if (res != ARGUMENT_GREATER_THAN_MAX_SUPPORTED) {
    printf("FAILED - Error not thrown when nodes count is exceeding max\n");
  }

  assert(decision == -1);
  assert(res == ARGUMENT_GREATER_THAN_MAX_SUPPORTED);

  printf("PASS - nodes count execding max\n");
  return 0;
}

int test_should_return_error_when_depth_exceed_max() {
  max_counts *counts = counts_init(10, 10, 3, 20, 100);

  // weights,offsets,candidates are not needed as we are only intrested in count;
  float candidates[]= {};
  int offsets[] = {};
  rf_node_t weights[] = {};

  rf_error_codes res;
  int decision = run_classification(counts, weights, offsets, candidates, &res);
  
  if (res != ARGUMENT_GREATER_THAN_MAX_SUPPORTED) {
    printf("FAILED - Error not thrown when depth count is exceeding max\n");
  }

  assert(decision == -1);
  assert(res == ARGUMENT_GREATER_THAN_MAX_SUPPORTED);

  printf("PASS - depth count execding max\n");
  return 0;
}

int test_should_return_error_when_zero_trees_are_passed() {
  max_counts *counts = counts_init(0, 10, 3, 10, 0);

  float candidates[][10] = {10.52178765, 6.07072253, -1.99584827, 6.4549465,
                              -8.63472683, 1.25364933, -5.94490446, 9.21032095,
                              1.35782526,  -1.38803355};

  int offsets[] = {0};

  rf_node_t weights[] = {};

  rf_error_codes res;
  int decision = run_classification(counts, weights, offsets, candidates, &res);
 
  if(res != ARGUMENT_ZERO_ERROR){
	printf("FAILED - Error not thrown when trees are zero\n");
}

  assert(decision == -1);
  assert(res == ARGUMENT_ZERO_ERROR);
  printf("PASS - when trees count is 0\n");
  return 0;
}

int main() {
  test_should_be_able_to_run_complete_model();
  test_should_return_error_when_trees_exceed_max();
  test_should_return_error_when_features_exceed_max();
  test_should_return_error_when_classes_exceed_max();
  test_should_return_error_when_nodes_exceed_max();
  test_should_return_error_when_depth_exceed_max();
  test_should_return_error_when_zero_trees_are_passed();
}
