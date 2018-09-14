package datadog.trace.instrumentation.trace_annotation;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public final class AutoTraceInstrumentation extends Instrumenter.Default {
  public AutoTraceInstrumentation() {
    super("autotrace");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // FIXME:
    return new ElementMatcher<TypeDescription>() {
      @Override
      public boolean matches(TypeDescription target) {
        return false;
      }
    };
  }

  @Override
  public Map<ElementMatcher, String> transformers() {
    // FIXME:
    final Map<ElementMatcher, String> transformers = new HashMap<>();
    return transformers;
  }

  public static class AutotraceAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static long startTimer() {
      return 0l;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopTimerAndCreateSpan(@Advice.Origin("#m") final String methodName,
                                              @Advice.Enter final long startTS,
                                              @Advice.Thrown final Throwable throwable) {
      // TODO
    }
  }
}
