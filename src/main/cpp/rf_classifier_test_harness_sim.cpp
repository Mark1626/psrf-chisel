#include <iostream>
#include <ostream>
#include "simulator.h"
#include "rf_classifier_test_harness_sim.h"

RFClassifierTestHarnessSim::RFClassifierTestHarnessSim(char *vcd_filename) : Simulator(vcd_filename) {}
RFClassifierTestHarnessSim::RFClassifierTestHarnessSim() : Simulator() {}

bool RFClassifierTestHarnessSim::execTest() {
  bool pass = true;
  unsigned test_cnt = 0;
  unsigned fail_cnt = 0;
  unsigned no_clear_majority_cnt = 0;

  dut->io_start = 1;
  dut->io_out_ready = 1;
  step();
  dut->io_start = 0;
  step();

  while (dut->io_done != 1) {
    if (dut->io_out_valid == 1) {
        if(dut->io_out_bits_pass != 1) {
          std::cout << "Test failed at case: " << test_cnt << " "
                    << "Expected: " << int(dut->io_out_bits_expectedClassification)
                    << " Result: " << int(dut->io_out_bits_resultantClassification) << std::endl;
            pass = false;
            fail_cnt++;
        }
        if(dut->io_out_bits_noClearMajority == 1) {
            no_clear_majority_cnt++;
        }
        test_cnt++;
    }
    step();
    }

    std::cout << "Test failures detected: " << fail_cnt << std::endl;
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
