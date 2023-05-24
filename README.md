Random Forest Classifier in Hardware
=======================

This project contains Chisel code to generate a random forest classifier for chipyard based SoC designs.
The training of the classification models are done using a python-based script.
The tree structure is serialized and stored in a scratchpad for the accelerator to use. 

This repo is meant to be added as a subproject in [chipyard](). Steps to add this are mentioned in the [chipyard section](#chipyard)

## Adding in Chipyard

Refer to [chipyard setup]() for setting up chipyard.
This project has been developed and tested in chipyard v1.8.1

1. `cd <CHIPYARD_ROOT>/generators`
2. `git clone <repo-url>`  (Adding this repo as submodule)
3. Within `build.sbt` of the chipyard root folder, add the project and add it as a dependency

```scala
// Add the sub project as one of the dependencies
lazy val chipyard = (project in file("generators/chipyard"))
     sha3, // On separate line to allow for cleaner tutorial-setup patches
     dsptools, `rocket-dsp-utils`,
     gemmini, icenet, tracegen, cva6, nvdla, sodor, ibex, fft_generator,
-    constellation, mempress)
+    constellation, mempress, psrf)
   .settings(libraryDependencies ++= rocketLibDeps.value)
   .settings(commonSettings)

// Add the repo as a sub project   
lazy val psrf = (project in file("./generators/psrf-chisel"))
  .dependsOn(rocketchip, testchipip, dsptools, `rocket-dsp-utils`)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(libraryDependencies ++= Seq("edu.berkeley.cs" %% "chiseltest"    % "0.5.1" % "test"))
  .settings(commonSettings)
```

### Generating a SoC with a Random Forest Accelerator

Create a Config which has the RandomForestAccelerator included

```scala
class RandomForestConfig extends Config(
  new psrf.accelerator.WithTLRandomForest(
    csrAddress=AddressSet(0x1100, 0xff),
    scratchpadAddress = AddressSet(0x200000, 0x1ffff)
  ) ++
    new freechips.rocketchip.subsystem.WithNBigCores(1) ++
    new chipyard.config.AbstractConfig
)
```

Add the peripheral within Digital Top

```scala
// DigitalTop.scala

class DigitalTop(implicit p: Parameters) extends ChipyardSystem
  // other peripherals 
  with psrf.accelerator.CanHaveTLRandomForestWithScratchpad
 {
   override lazy val module = new DigitalTopModule(this)
 }
```

### Running a Verilator based simulation of the SoC

```
cd sims/verilator
make CONFIG=RandomForestConfig
```

### Running Chisel unit-tests

To test the hardware modules using Chisel IO testers, within chipyard root folder

``` sh
$ sbt psrf/test
```
