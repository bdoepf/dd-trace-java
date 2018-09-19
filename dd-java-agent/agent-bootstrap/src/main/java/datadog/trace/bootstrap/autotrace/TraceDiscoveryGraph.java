package datadog.trace.bootstrap.autotrace;

import datadog.trace.bootstrap.WeakMap;
import datadog.trace.bootstrap.WeakMap.Provider;
import lombok.extern.slf4j.Slf4j;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
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
  public static final long AUTOTRACE_EXPAND_THRESHOLD_NANO = 10 * 1000000; // 10ms
  public static final long AUTOTRACE_DISABLE_THRESHOLD_NANO = 1 * 1000000; // 1ms

  static final AtomicReference<Instrumentation> instrumentationRef = new AtomicReference<>(null);
  private static final WeakMap<ClassLoader, Map<String, List<DiscoveredNode>>> nodeMap = Provider.<ClassLoader, Map<String, List<DiscoveredNode>>>newWeakMap();

  // FIXME: use standard bootstrap placeholder (see Utils)
  private static final ClassLoader BOOTSTRAP = new ClassLoader() {
    @Override
    public String toString() {
      return "<bootstrap-classloader>";
    }
  };

  private TraceDiscoveryGraph() {}

  private static String getDescriptorForClass(final Class c) {
    if(c.isPrimitive()) {
        if(c==byte.class)
            return "B";
        if(c==char.class)
            return "C";
        if(c==double.class)
            return "D";
        if(c==float.class)
            return "F";
        if(c==int.class)
            return "I";
        if(c==long.class)
            return "J";
        if(c==short.class)
            return "S";
        if(c==boolean.class)
            return "Z";
        if(c==void.class)
            return "V";
        throw new RuntimeException("Unrecognized primitive "+c);
    }
    if(c.isArray()) {
      // FIXME: primitive arrays?
      return c.getName().replace('.', '/');
    }
    return ('L'+c.getName()+';').replace('.', '/');
}

  public static String getMethodDescriptor(Method m) {
    String s = "(";
    for(final Class paramClass : m.getParameterTypes()) {
      s += getDescriptorForClass(paramClass);
    }
    s += ')';
    return s + getDescriptorForClass(m.getReturnType());
  }

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
    return newNode;
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

  public static DiscoveredNode getDiscoveredNode(ClassLoader classloader, String className, String methodSignature) {
    if (null == classloader) classloader = BOOTSTRAP;
    final Map<String, List<DiscoveredNode>> classMap = nodeMap.get(classloader);
    if (null != classMap) {
      final List<DiscoveredNode> nodes = classMap.get(className);
      if (null != nodes) {
        for (final DiscoveredNode node : nodes) {
          if (node.getMethodSignature().equals(methodSignature)) {
            return node;
          }
        }
      }
    }
    return null;
  }

  public static boolean isDiscovered(ClassLoader classloader, String className, String methodSignature) {
    return getDiscoveredNode(classloader, className, methodSignature) != null;
  }

  public static void registerInstrumentation(Instrumentation instrumentation) {
    instrumentationRef.set(instrumentation);
  }
}
