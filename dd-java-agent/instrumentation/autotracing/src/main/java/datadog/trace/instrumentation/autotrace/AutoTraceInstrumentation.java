package datadog.trace.instrumentation.autotrace;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.ExceptionHandlers;
import datadog.trace.agent.tooling.Instrumenter;

import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import datadog.trace.agent.tooling.Utils;
import datadog.trace.bootstrap.autotrace.TraceDiscoveryGraph;
import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;

import static io.opentracing.log.Fields.ERROR_OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.any;

@Slf4j
@AutoService(Instrumenter.class)
public final class AutoTraceInstrumentation extends Instrumenter.Default {
  public AutoTraceInstrumentation() {
    super("autotrace");
  }

  // TODO:
  @Override
  public AgentBuilder instrument(final AgentBuilder parentAgentBuilder) {
    // TODO: ugly
    // FIXME: strong classloader ref
    final ThreadLocal<ClassLoader> loaderUnderTransform = new ThreadLocal<>();
    final ThreadLocal<TypeDescription> typeUnderTransform = new ThreadLocal<>();
    return parentAgentBuilder
        .type(
            new AgentBuilder.RawMatcher() {
              @Override
              public boolean matches(
                  TypeDescription typeDescription,
                  ClassLoader classLoader,
                  JavaModule module,
                  Class<?> classBeingRedefined,
                  ProtectionDomain protectionDomain) {
                if (TraceDiscoveryGraph.isDiscovered(classLoader, typeDescription.getName())) {
                  loaderUnderTransform.set(classLoader);
                  typeUnderTransform.set(typeDescription);
                  return true;
                } else {
                  return false;
                }
              }
            })
        .transform(DDTransformers.defaultTransformers())
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .include(Utils.getAgentClassLoader())
                .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
                .advice(
                    new ElementMatcher<MethodDescription>() {
                      @Override
                      public boolean matches(MethodDescription target) {
                        return !target.isConstructor();
                      }
                    },
                    AutoTraceAdvice.class.getName()))
        .asDecorator();
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    throw new RuntimeException("FIXME");
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    throw new RuntimeException("FIXME");
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
    public static void stopTimerAndCreateSpan(
        @Advice.Origin("#t.#m") final String typeAndMethodName,
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
            final Scope scope =
                GlobalTracer.get()
                    .buildSpan(typeAndMethodName)
                    .withTag(Tags.COMPONENT.getKey(), "autotrace")
                    .withStartTimestamp(TimeUnit.NANOSECONDS.toMicros(startTS))
                    .startActive(true);
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
