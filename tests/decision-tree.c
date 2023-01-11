#include "mmio.h"

#define PASSTHROUGH_WRITE 0x2000
#define PASSTHROUGH_WRITE_LAST 0x2008
#define PASSTHROUGH_WRITE_COUNT 0x2010
//#define PASSTHROUGH_WRITE_COUNT 0x2208
#define PASSTHROUGH_READ 0x2100
#define PASSTHROUGH_READ_COUNT 0x2108

#define BP 2
#define BP_SCALE ((double)(1<<BP))

int64_t roundi(double x)
{
  if (x < 0.0) {
    return (int64_t)(x - 0.5);
  } else {
    return (int64_t)(x + 0.5);
  }
}

int main() {
    double features[] = { 0.5, 2 };
    int n = sizeof(features) / sizeof(double);

    printf("Starting writitng %d inputs\n", n);

    for (int i=0; i < n; i++) {
        reg_write8(PASSTHROUGH_WRITE_LAST, i & 1);
        reg_write64(PASSTHROUGH_WRITE, roundi(features[i] * BP_SCALE));
    }

    int32_t res = reg_read32(PASSTHROUGH_READ);
    printf("Result %d\n", res);

}
