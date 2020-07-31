package amf.exporters

trait Logger {
  def log(toLog: String): Unit
}

class ConsoleLogger extends Logger {
  override def log(toLog: String): Unit = println(toLog)
}

class DummyLogger extends Logger {
  override def log(toLog: String): Unit = Unit
}