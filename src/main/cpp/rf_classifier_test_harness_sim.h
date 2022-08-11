#ifndef RF_CLASSIFIER_TEST_HARNESS_SIM_H_
#define RF_CLASSIFIER_TEST_HARNESS_SIM_H_

#include "simulator.h"
#include "VRandomForestClassifierTestHarness.h"

#define VPREFIX VRandomForestClassifierTestHarness

class RFClassifierTestHarnessSim : public Simulator<VPREFIX> {
    public:
        RFClassifierTestHarnessSim();
        RFClassifierTestHarnessSim(char* vcd_filename);
        bool execTest();
};

#endif // RF_CLASSIFIER_TEST_HARNESS_SIM_H_
