import datadog.trace.bootstrap.autotrace.TraceDiscoveryGraph
import io.opentracing.tag.Tags
import some.org.Helper

import static datadog.trace.agent.test.TestUtils.runUnderTrace
import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces

import datadog.trace.agent.test.AgentTestRunner

class AutoTraceInstrumentationTest extends AgentTestRunner {
  def "trace after discovery"() {
    when:
    runUnderTrace("someTrace") {
      new Helper().someMethod(11)
    }

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "someTrace"
          errored false
          tags {
            defaultTags()
          }
        }
      }
    }

    when:
    TEST_WRITER.clear()
    TraceDiscoveryGraph.discoverOrGet(Helper.getClassLoader(), Helper.getName(), "someMethod(J)V").enableTracing(true)
    runUnderTrace("someTrace") {
      new Helper().someMethod(11)
    }

    then:
    TraceDiscoveryGraph.isDiscovered(null, Thread.getName(), "sleep(J)V")
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "someTrace"
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          serviceName "unnamed-java-app"
          operationName Helper.getSimpleName() + '.someMethod'
          errored false
          tags {
            "$Tags.COMPONENT.key" "autotrace"
            defaultTags()
          }
        }
      }
    }

    when:
    TEST_WRITER.clear()
    // someMethod should not create a span outside of a trace
    new Helper().someMethod(11)
    runUnderTrace("someTrace") {}
    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "someTrace"
          errored false
          tags {
            defaultTags()
          }
        }
      }
    }

    when:
    TEST_WRITER.clear()
    boolean slowTestRun = false
    long t1 = System.nanoTime()
    runUnderTrace("someTrace") {
      new Helper().someMethod(0)
    }
    long duration = System.nanoTime() - t1
    if (duration >= TraceDiscoveryGraph.AUTOTRACE_THRESHOLD_NANO) {
      // Trace is abnormally slow. Skip assertion.
      slowTestRun = true
    }
    TEST_WRITER.waitForTraces(1)

    then:
    TEST_WRITER.size() == 1
    TEST_WRITER[0].size() == slowTestRun ? 2 : 1

    // TODO: test tracing disable
  }

  def "discovery expansion propagates" () {
    when:
    // first-pass: n1 is manually discovered
    TraceDiscoveryGraph.discoverOrGet(Helper.getClassLoader(), Helper.getName(), "n1(JJJJ)V").enableTracing(true)
    runUnderTrace("someTrace") {
      new Helper().n1(0, 0, 11, 0)
    }
    TEST_WRITER.waitForTraces(1)

    then:
    TEST_WRITER.size() == 1
    TEST_WRITER[0].size() == 2

    // second-pass: n2 is automatically discovered
    when:
    TEST_WRITER.clear()
    TraceDiscoveryGraph.discoverOrGet(Helper.getClassLoader(), Helper.getName(), "n1(JJJJ)V")
    runUnderTrace("someTrace") {
      new Helper().n1(0, 0, 11, 0)
    }
    TEST_WRITER.waitForTraces(1)

    then:
    TEST_WRITER.size() == 1
    TEST_WRITER[0].size() == 3

    // third-pass: n3 is automatically discovered
    when:
    TEST_WRITER.clear()
    TraceDiscoveryGraph.discoverOrGet(Helper.getClassLoader(), Helper.getName(), "n1(JJJJ)V")
    runUnderTrace("someTrace") {
      new Helper().n1(0, 0, 11, 0)
    }
    // TEST_WRITER.waitForTraces(1)

    then:
    // TEST_WRITER.size() == 1
    // TEST_WRITER[0].size() == 4
    assertTraces(TEST_WRITER, 1) {
      trace(0, 4) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "someTrace"
          errored false
          tags {
            defaultTags()
          }
        }
        span(1) {
          serviceName "unnamed-java-app"
          operationName Helper.getSimpleName() + '.n1'
          errored false
          tags {
            "$Tags.COMPONENT.key" "autotrace"
            defaultTags()
          }
        }
        span(2) {
          serviceName "unnamed-java-app"
          operationName Helper.getSimpleName() + '.n2'
          errored false
          tags {
            "$Tags.COMPONENT.key" "autotrace"
            defaultTags()
          }
        }
        span(3) {
          serviceName "unnamed-java-app"
          operationName Helper.getSimpleName() + '.n3'
          errored false
          tags {
            "$Tags.COMPONENT.key" "autotrace"
            defaultTags()
          }
        }
      }
    }
  }

  def "auto instrumentation captures exceptions" () {
    // TODO
  }
}
