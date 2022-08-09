package psrf

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Calendar
import scala.sys.process._

object BuildPipeline {
  lazy val runsDirectory             = new File("runs")
  lazy val pythonSrcDirectory        = new File("src/main/python")
  lazy val swStageSrcFile            = new File(pythonSrcDirectory, "main.py")
  lazy val pythonVenvDirectory       = new File("env")
  lazy val pythonVenvActivationFile  = new File(pythonVenvDirectory, "/bin/activate")
  lazy val pythonPipRequirementsFile = new File("requirements.txt")

  val hwStageConfigFileName = "hwStageConfig.json"

  def timeStamp: String = {
    val format = new SimpleDateFormat("yyyyMMddHHmmss")
    val now    = Calendar.getInstance.getTime
    format.format(now)
  }

  def createRunDirectories(): (File, File) = {
    runsDirectory.mkdirs()
    val runDir   = Files.createTempDirectory(runsDirectory.toPath(), timeStamp).toFile
    val buildDir = new File(runDir, "build")
    buildDir.mkdirs()
    (buildDir, runDir)
  }

  def runProcess(cmd: ProcessBuilder, logger: ProcessLogger, errorMsg: String = "An exception occured"): Unit =
    if (cmd.!(logger) == 1) scala.sys.error(errorMsg)

  def apply(pipelineConfigFilePath: String, verbose: Boolean): Unit = {

    // Create directories and files for storing build products
    val (buildDir, runDir)   = createRunDirectories()
    val buildDirAbsolutePath = buildDir.getAbsolutePath()
    val runDirAbsolutePath   = runDir.getAbsolutePath()

    val stdoutFile         = new File(s"${runDirAbsolutePath}/stdout.txt")
    val logFile            = new File(s"${runDirAbsolutePath}/stderr.log")
    val vcdFile            = new File(s"${runDirAbsolutePath}/waveform.vcd")
    val pipelineConfigFile = new File(pipelineConfigFilePath)
    val hwStageConfigFile  = new File(buildDir, hwStageConfigFileName)

    // Process logger for the build pipeline
    val stdout = new PrintWriter(stdoutFile)
    val stderr = new PrintWriter(logFile)
    val logger = ProcessLogger(s => { println(s); stdout.write(s + "\n") }, s => { println(s); stderr.write(s + "\n") })

    def printIfVerbose(s: String): Unit = if (verbose) { println(s); stdout.write(s + "\n") }

    // Software stage
    // Commands
    val pythonVenvCreateCmd = Seq("python", "-m", "venv", pythonVenvDirectory.getAbsolutePath())
    //val pythonVenvActivateCmd = Seq("source", pythonVenvActivationFile.getAbsolutePath())
    val pythonVenvActivateCmd = Seq("/bin/cat", pythonVenvActivationFile.getAbsolutePath()) #| Seq("/bin/sh")
    val pythonPipInstallRequirementsCmd =
      pythonVenvActivateCmd #&& Seq("pip", "install", "-r", pythonPipRequirementsFile.getAbsolutePath())
    val swStageCmd = pythonVenvActivateCmd #&& Seq(
      "python",
      pythonSrcDirectory.toPath.toAbsolutePath.toString + File.separator + "main.py",
      "--config",
      pipelineConfigFile.toPath.toAbsolutePath.toString,
      "--out",
      hwStageConfigFile.toPath.toAbsolutePath.toString,
      (if (verbose) "--verbose" else "")
    )

    // Create a python virtual environment
    printIfVerbose("Creating a python virtual environment...")
    runProcess(pythonVenvCreateCmd, logger, "An error occured when creating the python virtual environment")

    // Install required python packages
    printIfVerbose("Installing required python packages...")
    runProcess(pythonPipInstallRequirementsCmd, logger, "An error occured when creating the python virtual environment")

    // Run software stage python script
    printIfVerbose("Running software training stage...")
    runProcess(swStageCmd, logger, "An error occured during the software training stage")

    stdout.close()
    stderr.close()
  }
}
