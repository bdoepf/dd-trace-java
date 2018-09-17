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
 * TODO: Use DI pattern for supplying graph to pieces of autodiscovery
 */
@Slf4j
public class TraceDiscoveryGraph {
  /**
   * Discovered Methods which exceed this threshold will be traced.
   */
  public static final long AUTOTRACE_THRESHOLD_NANO = 10 * 1000000; // 10ms

  private static final AtomicReference<Instrumentation> instrumentationRef = new AtomicReference<>(null);
  private static final WeakMap<ClassLoader, Map<String, List<DiscoveredNode>>> nodeMap = Provider.<ClassLoader, Map<String, List<DiscoveredNode>>>newWeakMap();

  // FIXME: use standard bootstrap placeholder (see Utils)
  private static final ClassLoader BOOTSTRAP = new ClassLoader() {
    @Override
    public String toString() {
      return "<bootstrap-classloader>";
    }
  };

  private TraceDiscoveryGraph() {}

  public static DiscoveredNode discoverOrGet(ClassLoader classloader, String className, String methodSignature) {
    if (null == classloader) classloader = BOOTSTRAP;
    try {
      // TODO: Classloading is a mess
      Class<?> clazz = classloader.loadClass(className);
      classloader = clazz.getClassLoader();
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    if (null == classloader) classloader = BOOTSTRAP;
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
    for (final DiscoveredNode node : nodeList) {
      if (node.getMethodSignature().equals(methodSignature)) {
        return node;
      }
    }

    final DiscoveredNode newNode = new DiscoveredNode(classloader, className, methodSignature);
    nodeList.add(newNode);
    final Instrumentation instrumentation = instrumentationRef.get();
    if (instrumentation != null) {
      try {
        instrumentation.retransformClasses(classloader.loadClass(className));
      } catch (Exception e) {
        log.debug("Failed to retransform " + className + " on loader " + classloader, e);
      }
    }
    return newNode;
  }

  public static void expand(Object clazz, Object method) {
  }

  public static boolean isDiscovered(ClassLoader classloader, String className) {
    return getDiscoveredNodes(classloader, className) != null;
  }

  public static List<DiscoveredNode> getDiscoveredNodes(ClassLoader classloader, String className) {
    if (null == classloader) classloader = BOOTSTRAP;
    if (nodeMap.containsKey(classloader)) {
      return nodeMap.get(classloader).get(className);
    }
    return null;
  }

  public static boolean isDiscovered(ClassLoader classloader, String className, String methodSignature) {
    if (null == classloader) classloader = BOOTSTRAP;
    final Map<String, List<DiscoveredNode>> classMap = nodeMap.get(classloader);
    if (null != classMap) {
      final List<DiscoveredNode> nodes = classMap.get(className);
      if (null != nodes) {
        for (final DiscoveredNode node : nodes) {
          if (node.getMethodSignature().equals(methodSignature)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static void registerInstrumentation(Instrumentation instrumentation) {
    instrumentationRef.set(instrumentation);
  }
}
