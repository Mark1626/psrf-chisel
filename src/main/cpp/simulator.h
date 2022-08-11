#ifndef SIMULATOR_H_
#define SIMULATOR_H_

#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <memory>

#include "verilated.h"
#if VM_TRACE
#include "verilated_vcd_file_rocket.h"
#endif

template <class VT>
class Simulator {
 public:
  int cycles;
  std::unique_ptr<VT> dut;

#if VM_TRACE
  FILE* vcd_file;
  std::unique_ptr<VerilatedVcdFileRocket> verilated_vcd_file;
  std::unique_ptr<VerilatedVcdC> tfp;
#endif

  Simulator(char* vcd_filename) : cycles(0), dut(std::make_unique<VT>()) {
#if VM_TRACE
    vcd_file = fopen(vcd_filename, "w");
    if (!vcd_file) {
      std::cerr << "Unable to open " << vcd_filename << " for VCD write\n";
      exit(1);
    }
    Verilated::traceEverOn(true);
    verilated_vcd_file = std::make_unique<VerilatedVcdFileRocket>(vcd_file);
    tfp = std::make_unique<VerilatedVcdC>(verilated_vcd_file.get());
    dut->trace(tfp.get(), 99);
    tfp->open("");
#endif
    dut->clock = 0;
    eval();
  }
  Simulator() { Simulator(NULL); }

  ~Simulator() {
    dut->final();
#if VM_TRACE
    if (tfp) tfp->close();
    if (vcd_file) fclose(vcd_file);
#endif
  }

  void eval() { dut->eval(); }

  void step(int times = 1) {
    for (int i = 0; i < times; i++) {
      dut->clock = 0;
      eval();
#if VM_TRACE
      tfp->dump((vluint64_t)(cycles * 2));
#endif
      dut->clock = 1;
      eval();
#if VM_TRACE
      tfp->dump((vluint64_t)(cycles * 2 + 1));
      tfp->flush();
#endif
      cycles++;
    }
  }

  void reset() {
    dut->reset = 1;
    step();
    dut->reset = 0;
  }

  uint64_t get_cycles() { return cycles; }
};

#endif  // SIMULATOR_H_
