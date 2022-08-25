package psrf.buildpipe

import java.io.File
import java.io.PrintWriter
import scala.sys.process._

trait BuildPipelineStageParameters {
  val runDir:       File
  val buildDir:     File
  val stdOutStream: Option[PrintWriter]
  val stdErrStream: Option[PrintWriter]
  val verbose:      Boolean

  lazy val srcDirectory              = new File("src")
  lazy val pythonSrcDirectory        = new File(srcDirectory, "main/python")
  lazy val cppSrcDirectory           = new File(srcDirectory, "main/cpp")
  lazy val swStageSrcFile            = new File(pythonSrcDirectory, "main.py")
  lazy val pythonVenvDirectory       = new File("env")
  lazy val pythonVenvActivationFile  = new File(pythonVenvDirectory, "/bin/activate")
  lazy val pythonPipRequirementsFile = new File("requirements.txt")
}

trait BuildPipelineStage {
  val p: BuildPipelineStageParameters
  def buildDirAbsolutePath = p.buildDir.getAbsolutePath()

  /** Closes stdout and stderr streams if present. */
  def closeStreams(): Unit = {
    if (p.stdOutStream.isDefined) p.stdOutStream.get.close()
    if (p.stdErrStream.isDefined) p.stdErrStream.get.close()
  }

  /** Runs a process and throws exception if it fails. */
  def runProcess(cmd: ProcessBuilder, logger: ProcessLogger, errorMsg: String = "An exception occured"): Unit =
    if (cmd.!(logger) == 1) sys.error(errorMsg)

  /** Prints a message to both console and a stream */
  def printToConsoleAndStream(s: String, stream: Option[PrintWriter] = p.stdOutStream, v: Boolean = p.verbose) =
    if (p.verbose) {
      println(s)
      if (stream.isDefined) {
        stream.get.write(s + "\n")
      }
    }

  def processLogger: ProcessLogger = ProcessLogger(
    s => printToConsoleAndStream(s, p.stdOutStream),
    s => printToConsoleAndStream(s, p.stdErrStream)
  )

  protected def executeUnsafe(): Option[BuildPipelineStageParameters]

  def execute(): Option[BuildPipelineStageParameters] = {
    try {
      executeUnsafe()
    } catch {
      case e: Exception => {
        closeStreams()
        throw e
      }
    }
  }
}
