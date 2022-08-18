package psrf.buildpipe

import java.io.File
import java.io.PrintWriter
import scala.sys.process._

case class SWStageParameters(
  val runDir:            File,
  val buildDir:          File,
  val stdOutStream:      Option[PrintWriter],
  val stdErrStream:      Option[PrintWriter],
  val verbose:           Boolean,
  val swStageConfigFile: File)
    extends BuildPipelineStageParameters {
  val hwStageConfigFile = new File(buildDir, "hwStageConfig.json")
}

class SWStage(val p: SWStageParameters) extends BuildPipelineStage {

  def pythonVenvCreateCmd   = Seq("python", "-m", "venv", p.pythonVenvDirectory.getAbsolutePath())
  def pythonVenvActivateCmd = Seq("/bin/cat", p.pythonVenvActivationFile.getAbsolutePath()) #| Seq("/bin/sh")
  def pythonPipInstallRequirementsCmd =
    pythonVenvActivateCmd #&& Seq("pip", "install", "-r", p.pythonPipRequirementsFile.getAbsolutePath())
  def swStageCmd = pythonVenvActivateCmd #&& Seq(
    "python",
    p.pythonSrcDirectory.getAbsolutePath() + File.separator + "main.py",
    "--config",
    p.swStageConfigFile.getAbsolutePath(),
    "--out",
    p.hwStageConfigFile.getAbsolutePath(),
    (if (p.verbose) "--verbose" else "")
  )

  override protected def executeUnsafe(): Option[HWStageParameters] = {
    // Create a python virtual environment
    printToConsoleAndStream("Creating a python virtual environment...")
    runProcess(pythonVenvCreateCmd, processLogger, "An error occured while creating the python virtual environment")

    // Install required python packages
    printToConsoleAndStream("Installing required python packages...")
    runProcess(
      pythonPipInstallRequirementsCmd,
      processLogger,
      "An error occured while installing the required python packages"
    )

    // Run software stage python script
    printToConsoleAndStream("Running software training script...")
    runProcess(swStageCmd, processLogger, "An error occured during the software training stage")
    Some(HWStageParameters(p.runDir, p.buildDir, p.stdOutStream, p.stdErrStream, p.verbose, p.hwStageConfigFile))
  }
}

object SWStage {
  def apply(p: SWStageParameters): Option[HWStageParameters] = {
    (new SWStage(p)).execute().map(_.asInstanceOf[HWStageParameters])
  }
}
