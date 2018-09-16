package datadog.trace.instrumentation.autotrace;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import datadog.trace.bootstrap.autotrace.TraceDiscoveryGraph;
import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.any;

@Slf4j
@AutoService(Instrumenter.class)
public final class AutoTraceInstrumentation extends Instrumenter.Default {
  public AutoTraceInstrumentation() {
    super("autotrace");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return new ElementMatcher<TypeDescription>() {
      @Override
      public boolean matches(TypeDescription target) {
        // FIXME: Classloader matcher
        // FIXME: should not use this classloader!
        return TraceDiscoveryGraph.isDiscovered(AutoTraceInstrumentation.class.getClassLoader(), target.getName());
      }
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    // FIXME:
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    transformers.put(new ElementMatcher<MethodDescription>() {
      @Override
      public boolean matches(MethodDescription target) {
        return !target.isConstructor();
      }
    }, AutoTraceAdvice.class.getName());
    return transformers;
    /*
    return Collections.singletonMap(new ElementMatcher<TypeDescription>() {
      @Override
      public boolean matches(TypeDescription target) {
        // FIXME matching all methods in a discovered node
        System.out.println("  -- Autotrace Matches method? " + target.getName());
        return true;
      }
    }
    , AutoTraceAdvice.class.getName());
    */
  }

  public static class AutoTraceAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long startTimer() {
      if (GlobalTracer.get().activeSpan() != null) {
        return System.nanoTime();
      }
      return -1l;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopTimerAndCreateSpan(@Advice.Origin("#t.#m") final String typeAndMethodName,
                                              @Advice.Enter final long startTS,
                                              @Advice.Thrown final Throwable throwable) {
      // TODO
      // System.out.println("TRACE METHOD? " + typeAndMethodName);
      if (startTS > 0l) {
        final Tracer globalTracer = GlobalTracer.get(); // TODO: move to method variable scope
        // ensure the active span was not removed in the method body
        // System.out.println(" ^^ maybe: " + globalTracer.activeSpan());
        if (globalTracer.activeSpan() != null) {
          // TODO: span builder api also collects nano time
          final long durationNano = System.nanoTime() - startTS;
          // System.out.println(" ^^ maybe2: " + durationNano);
          if (durationNano >= TraceDiscoveryGraph.AUTOTRACE_THRESHOLD_NANO) {
            final Scope scope = GlobalTracer.get().buildSpan(typeAndMethodName)
              .withTag(Tags.COMPONENT.getKey(), "autotrace")
              .withStartTimestamp(TimeUnit.NANOSECONDS.toMicros(startTS)).startActive(true);
            if (throwable != null) {
              Tags.ERROR.set(scope.span(), true);
              scope.span().log(Collections.singletonMap(ERROR_OBJECT, throwable));
            }
            scope.close();
          }
        }
      }
    }
  }
}
