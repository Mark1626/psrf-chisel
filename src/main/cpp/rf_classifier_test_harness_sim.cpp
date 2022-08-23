#include <iostream>
#include <ostream>
#include "simulator.h"
#include "rf_classifier_test_harness_sim.h"

RFClassifierTestHarnessSim::RFClassifierTestHarnessSim(char *vcd_filename) : Simulator(vcd_filename) {}
RFClassifierTestHarnessSim::RFClassifierTestHarnessSim() : Simulator() {}

bool RFClassifierTestHarnessSim::execTest() {

  bool pass = true;
  unsigned test_cnt = 0;
  unsigned sw_relative_fail_cnt = 0;
  unsigned target_fail_cnt = 0;
  unsigned no_clear_majority_cnt = 0;

  dut->io_start = 1;
  dut->io_out_ready = 1;
  step();
  dut->io_start = 0;
  step();

  while (dut->io_done != 1) {
    if (dut->io_out_valid == 1) {
      bool sw_relative_fail = dut->io_out_bits_swRelativePass != 1;
      bool target_fail = dut->io_out_bits_targetPass != 1;
      sw_relative_fail_cnt += sw_relative_fail;
      target_fail_cnt += target_fail;
      no_clear_majority_cnt += (dut->io_out_bits_noClearMajority == 1);
      test_cnt++;

      if (sw_relative_fail | target_fail) {
        std::cout << "Mismatch occured at test case: " << test_cnt << " Software relative expected: "
                  << int(dut->io_out_bits_swRelativeClassification)
                  << " Target expected: "
                  << int(dut->io_out_bits_targetClassification) << " Result: "
                  << int(dut->io_out_bits_resultantClassification) << std::endl;
        pass = false;
      }
    }
    step();
  }

    std::cout << "Test count: " << test_cnt << std::endl;
    std::cout << "Mismatches with software detected: " << sw_relative_fail_cnt << std::endl;
    std::cout << "Mismatches with target detected: " << target_fail_cnt << std::endl;
    std::cout << "Accuracy of Random Forest Classifier in hardware: " << double(test_cnt - target_fail_cnt)/test_cnt << std::endl;
    std::cout << "No clear majorities detected: " << no_clear_majority_cnt
              << std::endl;
    return pass;
}

int main(int argc, char *argv[]) {
#ifdef VM_TRACE
  if (argc != 2) {
    std::cerr << "Usage: " << argv[0] << " [vcdfile]" << std::endl;
#else
  if (argc > 1) {
    std::cerr << "Usage: " << argv[0] << std::endl;
#endif
    exit(1);
  }

#ifdef VM_TRACE
  RFClassifierTestHarnessSim *tb = new RFClassifierTestHarnessSim(argv[1]);
#else
  RFClassifierTestHarnessSim *tb = new RFClassifierTestHarnessSim();
#endif

  if (tb->execTest()) {
    std::cout << "TEST PASSED\n";
    exit(0);
  }
  std::cout << "TEST FAILED\n";
  exit(1);
}
