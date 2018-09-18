package datadog.trace.bootstrap.autotrace;

import java.lang.instrument.UnmodifiableClassException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class DiscoveredNode {
  private final WeakReference<ClassLoader> classloader;
  private final String className;
  private final String methodSignature;
  // TODO: since each state only has two values use atomic booleans
  private final AtomicReference<ExpansionState> expansionState = new AtomicReference<>(ExpansionState.NOT_EXPANDED);
  private final AtomicReference<TracingState> tracingState = new AtomicReference<>(TracingState.UNSET);
  private final List<DiscoveredNode> edges = new CopyOnWriteArrayList<>();

  public DiscoveredNode(ClassLoader classloader, String className, String methodSignature) {
    this.classloader = new WeakReference<>(classloader);
    this.className = className;
    this.methodSignature = methodSignature;
  }

  public String getClassName() {
    return className;
  }

  public String getMethodSignature() {
    return methodSignature;
  }

  @Override
  public String toString() {
    return "<" + classloader.get() + "> " + className + "." + methodSignature;
  }

  public void enableTracing(boolean allowTracing) {
    if (allowTracing) {
      if (tracingState.compareAndSet(TracingState.UNSET, TracingState.TRACING_ENABLED)) {
        System.out.println("-- trace node: " + this);
        retransform();
      }
    } else {
      if (tracingState.compareAndSet(TracingState.TRACING_ENABLED, TracingState.TRACING_DISABLED)) {
        System.out.println("-- stop trace node: " + this);
        retransform();
      } else if (tracingState.compareAndSet(TracingState.UNSET, TracingState.TRACING_DISABLED)) {
        System.out.println("-- never start to trace trace node: " + this);
      }
    }
  }

  public boolean isTracingEnabled() {
    return tracingState.get() == TracingState.TRACING_ENABLED;
  }

  public boolean isExpanded() {
    return expansionState.get() == ExpansionState.EXPANDED;
  }

  public void expand() {
    if (expansionState.get() == ExpansionState.NOT_EXPANDED) {
      System.out.println("-- expand node: " + this);
      retransform();
    }
  }

  public void addEdges(List<DiscoveredNode> edges) {
    if (expansionState.compareAndSet(ExpansionState.NOT_EXPANDED, ExpansionState.EXPANDED)) {
      this.edges.addAll(edges);
    } else {
      throw new IllegalStateException("Cannot add edges to an expanded node: " + this);
    }
  }

  // FIXME: bad design
  private void retransform() {
    try {
      TraceDiscoveryGraph.instrumentationRef.get().retransformClasses(classloader.get().loadClass(className));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public List<DiscoveredNode> getEdges() {
    return edges;
  }

  /**
   * Whether outgoing edges of this node have been discovered.
   */
  private enum ExpansionState {
    /**
     * Outgoing edges have been discovered.
     */
    EXPANDED,
    /**
     * Outgoing edges have not been discovered.
     */
    NOT_EXPANDED
  }

  /**
   * Determines if this node can be auto-traced.
   */
  private enum TracingState {
    UNSET,
    /**
     * In the graph and viable for tracing.
     */
    TRACING_ENABLED,
    /**
     * In the graph but not viable for tracing.
     */
    TRACING_DISABLED
  }
}
