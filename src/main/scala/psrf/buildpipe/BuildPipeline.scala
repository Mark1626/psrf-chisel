package psrf.buildpipe

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Calendar

object BuildPipeline {

  lazy val runsDirectory = new File("runs")

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

  def apply(pipelineConfigFilePath: String, verbose: Boolean): Unit = {
    val (buildDir, runDir) = createRunDirectories()
    val stdOutFile         = new File(s"${runDir.getAbsolutePath()}/stdout.txt")
    val stdErrFile         = new File(s"${runDir.getAbsolutePath()}/stderr.log")
    val pipelineConfigFile = new File(pipelineConfigFilePath)

    val stdOutStream = new PrintWriter(stdOutFile)
    val stdErrStream = new PrintWriter(stdErrFile)

    val swStageParams =
      SWStageParameters(runDir, buildDir, Some(stdOutStream), Some(stdErrStream), verbose, pipelineConfigFile)
    val hwStageParams  = SWStage(swStageParams).get
    val runStageParams = HWStage(hwStageParams).get
    RunStage(runStageParams)
    stdOutStream.close()
    stdErrStream.close()
  }
}
