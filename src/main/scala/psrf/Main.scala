package psrf

object Main {
  val usage = """
sbt "run [options] config_file"
options:
    -v, --verbose       Provide verbose output
    -h, --help          Print this help message
"""
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      println(usage)
      scala.sys.exit(1)
    }

    def decodeNextArg(opts: Map[String, Any], toDecode: List[String]): Map[String, Any] = {
      toDecode match {
        case Nil                          => opts
        case ("--verbose" | "-v") :: rest => decodeNextArg(opts ++ Map("verbose" -> true), rest)
        case ("--help" | "-h") :: rest => {
          println(usage)
          scala.sys.exit(0)
        }
        case s :: Nil => decodeNextArg(opts ++ Map("config" -> s), toDecode.tail)
        case u :: _ =>
          println("Unknown option " + u)
          println(usage)
          scala.sys.exit(1)
      }
    }

    val opts = decodeNextArg(Map(), args.toList)

    try {
      BuildPipeline(
        opts.getOrElse("config", "").asInstanceOf[String],
        opts.getOrElse("verbose", false).asInstanceOf[Boolean]
      )
    } catch {
      case e: Exception => {
        println(e)
        scala.sys.exit(1)
      }
    }
  }
}
