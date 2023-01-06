#ifndef VERILATED_VCD_FILE_ROCKET_H_
#define VERILATED_VCD_FILE_ROCKET_H_

#include "verilated_vcd_c.h"
#include <stdlib.h>
#include <stdio.h>

extern bool verbose;
extern bool done_reset;

class VerilatedVcdFileRocket : public VerilatedVcdFile {
 public:
  VerilatedVcdFileRocket(FILE* file) : file(file) {}
  ~VerilatedVcdFileRocket() {}
  bool open(const std::string& name) override {
    // file should already be open
    return file != NULL;
  }
  void close() override {
    // file should be closed elsewhere
  }
  ssize_t write(const char* bufp, ssize_t len) override {
    return fwrite(bufp, 1, len, file);
  }
 private:
  FILE* file;
};

#endif // VERILATED_VCD_FILE_ROCKET_H_
