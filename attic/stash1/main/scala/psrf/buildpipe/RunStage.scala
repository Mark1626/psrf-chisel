package psrf.buildpipe

import java.io.File
import java.io.PrintWriter
import scala.sys.process._

case class RunStageParameters(
  val runDir:            File,
  val buildDir:          File,
  val stdOutStream:      Option[PrintWriter],
  val stdErrStream:      Option[PrintWriter],
  val verbose:           Boolean,
  val buildPrefix:       String,
  val outputVerilogFile: File,
  val buildTarget:       String,
  val trace:             Boolean = true)
    extends BuildPipelineStageParameters {
  def vcdFile = new File(runDir, "waveform.vcd")
}

/** Run stage that simulates the generated design or prepares it for synthesis. */
class RunStage(val p: RunStageParameters) extends BuildPipelineStage {

  /** Command to initiate cmake for the verilator-based simulator. */
  def verilatorCmakeCmd = Seq(
    "cmake",
    "-S",
    s"${p.cppSrcDirectory.getAbsolutePath()}",
    "-B",
    p.buildDir.getAbsolutePath(),
    s"-DBUILD_SIMULATOR=${p.buildPrefix}",
    s"-DVERILOG_SRC=${p.outputVerilogFile.getAbsolutePath()}",
    s"-DVERILATOR_TRACE=${if (p.trace) "ON" else "OFF"}"
  )

  /** Command to build the verilator-based simulator. */
  def verilatorBuildCmd = Seq(
    "cmake",
    "--build",
    p.buildDir.getAbsolutePath()
  )

  /** Command to execute the verilator-based simulator. */
  def verilatorExecuteCmd =
    Seq(
      p.buildDir.getAbsolutePath + File.separator + p.buildPrefix,
      { if (p.trace) p.vcdFile.getAbsolutePath() else "" }
    )

  override protected def executeUnsafe(): Option[BuildPipelineStageParameters] = {
    p.buildTarget match {
      case "sim" => {
        printToConsoleAndStream("Simulation build target chosen")
        printToConsoleAndStream("Building the Verilator simulator...")
        runProcess(
          verilatorCmakeCmd #&& verilatorBuildCmd,
          processLogger,
          "An error occured while building the verilator simulator"
        )
        printToConsoleAndStream("Running the Verilator simulator...")
        runProcess(verilatorExecuteCmd, processLogger, "An error occured while running the verilator simulator")
        printToConsoleAndStream("Build done!!!")
      }
      case "synth" => {
        printToConsoleAndStream("Synthesis build target chosen")
        printToConsoleAndStream("Build done!!!")
      }
    }
    None
  }
}

object RunStage {
  def apply(p: RunStageParameters) = {
    (new RunStage(p)).execute()
  }
}
