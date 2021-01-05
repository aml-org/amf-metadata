package amf.exporters

trait Logger {
  def log(toLog: String): Unit
}

object ConsoleLogger extends Logger {
  override def log(toLog: String): Unit = println(toLog)
}

object TestLogger extends Logger {
  override def log(toLog: String): Unit = Unit
}