package datadog.trace

import datadog.opentracing.DDTracer
import datadog.trace.api.Config
import datadog.trace.common.sampling.AllSampler
import datadog.trace.common.sampling.RateByServiceSampler
import datadog.trace.common.writer.LoggingWriter
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import spock.lang.Specification

import static datadog.trace.api.Config.HEADER_TAGS
import static datadog.trace.api.Config.PREFIX
import static datadog.trace.api.Config.PRIORITY_SAMPLING
import static datadog.trace.api.Config.SERVICE_MAPPING
import static datadog.trace.api.Config.SPAN_TAGS
import static datadog.trace.api.Config.WRITER_TYPE

class DDTracerTest extends Specification {
  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  def "verify defaults on tracer"() {
    when:
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "unnamed-java-app"
    tracer.sampler instanceof AllSampler
    tracer.writer.toString() == "DDAgentWriter { api=DDApi { tracesEndpoint=http://localhost:8126/v0.3/traces } }"

    tracer.spanContextDecorators.size() == 12
  }

  def "verify overriding sampler"() {
    setup:
    System.setProperty(PREFIX + PRIORITY_SAMPLING, "true")

    when:
    def tracer = new DDTracer(new Config())

    then:
    tracer.sampler instanceof RateByServiceSampler
  }

  def "verify overriding writer"() {
    setup:
    System.setProperty(PREFIX + WRITER_TYPE, "LoggingWriter")

    when:
    def tracer = new DDTracer(new Config())

    then:
    tracer.writer instanceof LoggingWriter
  }

  def "verify mapping configs on tracer"() {
    setup:
    System.setProperty(PREFIX + SERVICE_MAPPING, mapString)
    System.setProperty(PREFIX + SPAN_TAGS, mapString)
    System.setProperty(PREFIX + HEADER_TAGS, mapString)

    when:
    def tracer = new DDTracer(new Config())
    def taggedHeaders = tracer.registry.codecs.values().first().taggedHeaders

    then:
    tracer.defaultSpanTags == map
    tracer.serviceNameMappings == map
    taggedHeaders == map

    where:
    mapString       | map
    "a:1, a:2, a:3" | [a: "3"]
    "a:b,c:d,e:"    | [a: "b", c: "d"]
  }

  def "verify single override on #source for #key"() {
    when:
    System.setProperty(PREFIX + key, value)
    def tracer = new DDTracer(new Config())

    then:
    tracer."$source".toString() == expected

    where:

    source   | key           | value           | expected
    "writer" | "default"     | "default"       | "DDAgentWriter { api=DDApi { tracesEndpoint=http://localhost:8126/v0.3/traces } }"
    "writer" | "writer.type" | "LoggingWriter" | "LoggingWriter { }"
    "writer" | "agent.host"  | "somethingelse" | "DDAgentWriter { api=DDApi { tracesEndpoint=http://somethingelse:8126/v0.3/traces } }"
    "writer" | "agent.port"  | "9999"          | "DDAgentWriter { api=DDApi { tracesEndpoint=http://localhost:9999/v0.3/traces } }"
  }
}
