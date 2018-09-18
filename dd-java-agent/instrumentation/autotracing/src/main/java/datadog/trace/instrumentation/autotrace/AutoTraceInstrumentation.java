package datadog.trace.instrumentation.autotrace;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.DDTransformers;
import datadog.trace.agent.tooling.ExceptionHandlers;
import datadog.trace.agent.tooling.Instrumenter;

import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import datadog.trace.agent.tooling.Utils;
import datadog.trace.bootstrap.autotrace.DiscoveredNode;
import datadog.trace.bootstrap.autotrace.TraceDiscoveryGraph;
import io.opentracing.Scope;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

import static io.opentracing.log.Fields.ERROR_OBJECT;

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
                loaderUnderTransform.set(null);
                typeUnderTransform.set(null);
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
                        final String signature = target.getName() + target.getDescriptor();
                        final DiscoveredNode node = TraceDiscoveryGraph.getDiscoveredNode(loaderUnderTransform.get(), typeUnderTransform.get().getName(), signature);
                        boolean match = !target.isConstructor() && node != null && node.isTracingEnabled();
                        if (match) {
                          System.out.println(" autotrace! " + target);
                        }
                        return match;
                      }
                    },
                    AutoTraceAdvice.class.getName()))
      .transform(new AgentBuilder.Transformer() {
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
          // Hook up last to avoid discovering advice bytecode.
          // TODO: How to handle other instrumentation's bytecode?
          return builder.visit(new MethodExpander(classLoader, typeDescription.getName(), TraceDiscoveryGraph.getDiscoveredNodes(classLoader, typeDescription.getName())));
        }
      })
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
      // TODO: improve no-trace path
      return Long.MIN_VALUE;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopTimerAndCreateSpan(
        @Advice.This final Object thiz,
        @Advice.Origin("#t") final String typeName,
        @Advice.Origin("#m") final String methodName,
        @Advice.Origin("#m#d") final String nodeSig,
        @Advice.Enter final long startTS,
        @Advice.Thrown final Throwable throwable) {
      // TODO
      if (startTS > Long.MIN_VALUE) {
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
                    .buildSpan(typeName.replaceAll("^.*\\.([^\\.]+)", "$1") + "." + methodName)
                    .withTag(Tags.COMPONENT.getKey(), "autotrace")
                    .withStartTimestamp(TimeUnit.NANOSECONDS.toMicros(startTS))
                    .startActive(true);
            if (throwable != null) {
              Tags.ERROR.set(scope.span(), true);
              scope.span().log(Collections.singletonMap(ERROR_OBJECT, throwable));
            }
            scope.close();

            // TODO: retransform to remove unneeded expansion calls after first pass
            {
              System.out.println(" LOOK FOR NODE: " + thiz.getClass().getClassLoader() + " (" + thiz.getClass().getName() + ":" + thiz +  ") <-> " + typeName + " " + nodeSig);
              final DiscoveredNode node = TraceDiscoveryGraph.getDiscoveredNode(thiz.getClass().getClassLoader(), typeName, nodeSig);
              if (node != null) {
                node.expand();
                for (DiscoveredNode edge : node.getEdges()) {
                  edge.enableTracing(true);
                }
              }
            }
          }
        }
      }
    }
  }
}
