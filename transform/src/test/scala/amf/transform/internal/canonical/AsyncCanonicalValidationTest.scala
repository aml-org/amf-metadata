package amf.transform.internal.canonical

import amf.core.internal.remote.{Async20YamlHint, Hint}


class AsyncCanonicalValidationTest extends CanonicalSpecValidationTest {

  override val basePath: String = "file://transform/src/test/resources/specs/async20/"
  override val hint: Hint = Async20YamlHint

  override val apiPaths = Set(
    "amqp-exchange-channel-binding.yaml",
    "amqp-message-binding.yaml",
    "amqp-operation-binding.yaml",
    "amqp-queue-channel-binding.yaml",
    "channel-parameters.yaml",
    "draft-7-schemas.yaml",
    "empty-dynamic-binding.yaml",
    "http-message-binding.yaml",
    "http-operation-binding.yaml",
    "kafka-message-binding.yaml",
    "kafka-operation-binding.yaml",
    "message-obj.yaml",
    "mqtt-message-binding.yaml",
    "mqtt-operation-binding.yaml",
    "mqtt-server-binding.yaml",
    "publish-subscribe.yaml",
    "rpc-server.yaml",
    "security-schemes.yaml",
    "ws-channel-binding.yaml",
    "components/async-components.yaml",
    "components/external-operation-traits.yaml",
    "components/message-traits.yaml",
    "components/operation-traits.yaml"
  )

  runValidations()
}
