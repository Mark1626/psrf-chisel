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

/** Software stage that performs the training of random forest classifier in python. */
class SWStage(val p: SWStageParameters) extends BuildPipelineStage {

  /** Command to create a python virtual environment. */
  def pythonVenvCreateCmd = Seq("python3", "-m", "venv", p.pythonVenvDirectory.getAbsolutePath())

  /** Command to activate python virtual environment. */
  def pythonVenvActivateCmd = Seq("/bin/cat", p.pythonVenvActivationFile.getAbsolutePath()) #| Seq("/bin/sh")

  /** Command to install all required python libraries in the virtual environment. */
  def pythonPipInstallRequirementsCmd =
    pythonVenvActivateCmd #&& Seq("pip3", "install", "-r", p.pythonPipRequirementsFile.getAbsolutePath())

  /** Command to invoke the main python training script. */
  def swStageCmd = pythonVenvActivateCmd #&& Seq(
    "python3",
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
