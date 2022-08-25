Random Forest Classifier in Hardware
=======================

This project contains utilities to build a random forest classifier in hardware intended for FPGAs. The training of the classifier is done using a python-based script. The tree structure is then extracted and used to create verilog designs using chisel3-based parameterized circuit generators. The generated design may then be tested using a verilator-based simulator or used for synthesis using external FPGA toolchains.

## Requirements

- `python`- version 3.3 or greater.
- `sbt` - Refer [here](https://www.scala-sbt.org/download.html) for installation.
- `cmake` - version 3.3 or greater. Refer [here](https://cmake.org/download/) for installation.
- `verilator` - Refer [here](https://www.veripool.org/projects/verilator/wiki/Installing) for installation. On MacOS you can install it using `brew install verilator`.

## Running the build pipeline
The build pipeline, implemented in scala, can be triggered with default configurations using the following command:

``` sh
$ sbt "run --verbose config/default.json"
```

To understand how the build pipeline works and how it can be configured, please refer to the following documents:
- [*Build system*](docs/build-system.md)
- [*Configuration system*](docs/config-system.md)

## Running Chisel unit-tests
To test the hardware modules using Chisel IO testers:

``` sh
$ sbt test
```

## Known issues

### Java out-of-memory error for large designs

For large designs (high number of trees with many nodes), JVM may throw an out of memory error when compiling chisel to verilog. It may be necessary to increase the JVM heap size or change the garbage collector.

``` sh
$ export JAVA_OPTS="-XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC -Xmx4G -XX:MaxPermSize=1G -Xss2M"
```

### Verilator error for simulating large designs

Verilator may also encounter errors when compiling large verilog designs to C++ due to lack of memory. Please ensure that the system has adequate amount of free memory to avoid this.
