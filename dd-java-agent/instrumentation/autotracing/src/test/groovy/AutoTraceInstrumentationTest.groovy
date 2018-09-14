import datadog.trace.bootstrap.JDBCMaps
import datadog.trace.bootstrap.autotrace.TraceDiscoveryGraph
import io.opentracing.tag.Tags

import static datadog.trace.agent.test.TestUtils.runUnderTrace
import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces

import datadog.trace.agent.test.AgentTestRunner
import net.bytebuddy.utility.JavaModule

class AutoTraceInstrumentationTest extends AgentTestRunner {
  @Override
  protected boolean onInstrumentationError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
    // TODO: sane way to ignore these errors
    return false
  }

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
    // FIXME: NoClassDefFoundError when getting discovery graph. Groovy metaclass weirdness.
    println JDBCMaps.DB_QUERY
    println TraceDiscoveryGraph.AUTOTRACE_THRESHOLD_NANO
    /*
    Class<?> clazz = ClassLoader.getSystemClassLoader().loadClass("datadog.trace.bootstrap.autotrace.TraceDiscoveryGraph")
    clazz.getDeclaredMethod("discover", ClassLoader, String, String)
      .invoke(Helper1.getClassLoader(), Helper1.getName(), "someMethod(long)")
      */
    // TraceDiscoveryGraph.discover(Helper1.getClassLoader(), Helper1.getName(), "someMethod(long)")
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
  }

  static class Helper1 {
    void someMethod(long sleepTimeMS) {
      Thread.sleep(sleepTimeMS)
    }
  }
}
