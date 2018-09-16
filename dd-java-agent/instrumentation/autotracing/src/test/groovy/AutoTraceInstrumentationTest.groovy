import datadog.trace.bootstrap.JDBCMaps
import datadog.trace.bootstrap.autotrace.TraceDiscoveryGraph
import io.opentracing.tag.Tags
import net.bytebuddy.agent.ByteBuddyAgent

import static datadog.trace.agent.test.TestUtils.runUnderTrace
import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces

import datadog.trace.agent.test.AgentTestRunner
import net.bytebuddy.utility.JavaModule

class AutoTraceInstrumentationTest extends AgentTestRunner {
  def "trace after discovery"() {
    when:
    runUnderTrace("someTrace") {
      new Helper1().someMethod(11)
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
    // FIXME: should use classloader: Helper1.getClassLoader()
    TraceDiscoveryGraph.discover(AgentTestRunner.getClassLoader(), Helper1.getName(), "someMethod(long)")
    // FIXME: manual retransform
    println "-- RETRANSFORM!"
    ByteBuddyAgent.getInstrumentation().retransformClasses(Helper1)
    println "-- /RETRANSFORM!"
    // /FIXME: manual retransform
    runUnderTrace("someTrace") {
      new Helper1().someMethod(11)
    }

    then:
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
          operationName Helper1.getName()+".someMethod"
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

    boolean slowTestRun = false
    long t1 = System.nanoTime()
    runUnderTrace("someTrace") {
      new Helper1().someMethod(0)
    }
    long duration = System.nanoTime() - t1
    if (duration >= TraceDiscoveryGraph.AUTOTRACE_THRESHOLD_NANO) {
      // Trace is abnormally slow. Skip assertion.
      slowTestRun = true
    }

    then:
    slowTestRun ? true : assertTraces(TEST_WRITER, 1) {
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
  }

  static class Helper1 {
    void someMethod(long sleepTimeMS) {
      Thread.sleep(sleepTimeMS)
    }
  }
}