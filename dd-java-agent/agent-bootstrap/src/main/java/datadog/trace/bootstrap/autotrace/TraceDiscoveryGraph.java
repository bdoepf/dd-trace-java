package datadog.trace.bootstrap.autotrace;

import datadog.trace.bootstrap.WeakMap;
import datadog.trace.bootstrap.WeakMap.Provider;
import lombok.extern.slf4j.Slf4j;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Global store of discovered nodes
 *
 * TODO: Doc
 * TODO: Implement
 * TODO: autotrace package
 */
@Slf4j
public class TraceDiscoveryGraph {
  /**
   * Discovered Methods which exceed this threshold will be traced.
   */
  public static final long AUTOTRACE_THRESHOLD_NANO = 10 * 1000000; // 10ms

  private static final AtomicReference<Instrumentation> instrumentationRef = new AtomicReference<>(null);
  private static final WeakMap<ClassLoader, Map<String, List<DiscoveredNode>>> nodeMap = Provider.<ClassLoader, Map<String, List<DiscoveredNode>>>newWeakMap();
  private static final Set<DiscoveredNode> discoveredNodes = Collections.newSetFromMap(new ConcurrentHashMap<DiscoveredNode, Boolean>());

  private TraceDiscoveryGraph() {}

  public static void discover(ClassLoader classloader, String className, String methodSignature) {
    final DiscoveredNode node = new DiscoveredNode(classloader, className, methodSignature);
    if (discoveredNodes.add(node)) {
      if (!nodeMap.containsKey(classloader)) {
        synchronized (nodeMap) {
          if (!nodeMap.containsKey(classloader)) {
            nodeMap.put(classloader, new ConcurrentHashMap<String, List<DiscoveredNode>>());
          }
        }
      }
      final Map<String, List<DiscoveredNode>> classMap = nodeMap.get(classloader);
      if (!classMap.containsKey(className)) {
        synchronized (classMap) {
          if (!classMap.containsKey(className)) {
            classMap.put(className, new CopyOnWriteArrayList<DiscoveredNode>());
          }
        }
      }
      final List<DiscoveredNode> nodeList = classMap.get(className);
      nodeList.add(node);

      final Instrumentation instrumentation = instrumentationRef.get();
      if (instrumentation != null) {
        try {
          instrumentation.retransformClasses(classloader.loadClass(className));
        } catch (Exception e) {
          log.debug("Failed to retransform " + className + " on loder " + classloader, e);
        }
      }
    }
  }

  public static void expand(Object clazz, Object method) {
  }

  public static boolean isDiscovered(ClassLoader classloader, String className) {
    return nodeMap.containsKey(classloader) && nodeMap.get(classloader).containsKey(className);
  }

  public static boolean isDiscovered(ClassLoader classloader, String className, String methodSignature) {
    return discoveredNodes.contains(new DiscoveredNode(classloader, className, methodSignature));
  }

  public static DiscoveredNode getDiscoveredNode(Object clazzloader, Object clazz, Object method) {
    return null;
  }

  public static void registerInstrumentation(Instrumentation instrumentation) {
    instrumentationRef.set(instrumentation);
  }
}
