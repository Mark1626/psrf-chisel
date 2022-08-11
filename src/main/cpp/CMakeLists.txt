cmake_minimum_required(VERSION 3.3)

project(rf_classifier_verilator_simulator)

set(BUILD_SIMULATOR "Default" CACHE STRING "Simulator to build")
set(VERILOG_SRC "default.v" CACHE PATH "Verilog file to verilate")
option(VERILATOR_TRACE "Enable VCD tracing" ON)

set(AVAILABLE_SIMULATORS "RandomForestClassifierTestHarness")

set(VERILATE_TRACE "")
if(VERILATOR_TRACE)
  set(VERILATE_TRACE TRACE)
endif()

set(VERILATOR_CFLAGS "-fopenmp")

find_package(verilator HINTS $ENV{VERILATOR_ROOT})

set(${PROJECT_NAME}_headers simulator.h verilated_vcd_file_rocket.h)

if (NOT BUILD_SIMULATOR IN_LIST AVAILABLE_SIMULATORS)
  message(FATAL_ERROR "${BUILD_SIMULATOR} is not a valid simulator. Exiting.")
endif()

if(${BUILD_SIMULATOR} STREQUAL "RandomForestClassifierTestHarness")
  set(${PROJECT_NAME}_headers rf_classifier_test_harness_sim.h ${${PROJECT_NAME}_headers})
  set(${PROJECT_NAME}_sources rf_classifier_test_harness_sim.cpp ${${PROJECT_NAME}_sources})
endif()

add_executable(${BUILD_SIMULATOR} ${${PROJECT_NAME}_headers} ${${PROJECT_NAME}_sources})

verilate(${BUILD_SIMULATOR} SOURCES ${VERILOG_SRC} PREFIX "V${BUILD_SIMULATOR}" ${VERILATE_TRACE})

message(STATUS "Verilator cmd: " ${VERILATOR_COMMAND})
