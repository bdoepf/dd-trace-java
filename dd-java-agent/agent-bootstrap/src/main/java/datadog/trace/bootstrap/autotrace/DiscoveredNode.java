package datadog.trace.bootstrap.autotrace;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class DiscoveredNode {
  private final WeakReference<ClassLoader> classloader;
  private final String className;
  private final String methodSignature;
  private final AtomicReference<ExpansionState> expansionState = new AtomicReference<>(ExpansionState.NOT_EXPANDED);
  private final AtomicReference<TracingState> tracingState = new AtomicReference<>(TracingState.TRACING_ENABLED);
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
    return classloader.get() + className + "." + methodSignature;
  }

  public void addEdges(List<DiscoveredNode> edges) {
    if (expansionState.compareAndSet(ExpansionState.NOT_EXPANDED, ExpansionState.EXPANDED)) {
      this.edges.addAll(edges);
    } else {
      throw new IllegalStateException("Cannot add edges to an expanded node: " + this);
    }
  }

  public void enableTracing(boolean allowTracing) {
    if (allowTracing) {
      tracingState.set(TracingState.TRACING_ENABLED);
    } else {
      tracingState.set(TracingState.TRACING_DISABLED);
    }
    // TODO: retransform?
  }

  public boolean isTracingEnabled() {
    return tracingState.get() == TracingState.TRACING_ENABLED;
  }

  public boolean isExpanded() {
    return expansionState.get() == ExpansionState.EXPANDED;
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
