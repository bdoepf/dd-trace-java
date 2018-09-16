package datadog.trace.bootstrap.autotrace;

import datadog.trace.bootstrap.WeakMap;
import datadog.trace.bootstrap.WeakMap.Provider;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Global store of discovered nodes
 *
 * TODO: Doc
 * TODO: Implement
 * TODO: autotrace package
 */
public class TraceDiscoveryGraph {
  /**
   * Discovered Methods which exceed this threshold will be traced.
   */
  public static final long AUTOTRACE_THRESHOLD_NANO = 10 * 1000000; // 10ms

  // private static final Map<ClassLoader, Map<String, List<DiscoveredNode>>> nodeMap = (Map<ClassLoader, Map<String, List<DiscoveredNode>>>) Provider.newWeakMap();
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
}
