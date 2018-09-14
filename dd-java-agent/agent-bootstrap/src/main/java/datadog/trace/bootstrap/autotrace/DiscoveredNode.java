package datadog.trace.bootstrap.autotrace;

import java.lang.ref.WeakReference;

// TODO: autotrace package
public class DiscoveredNode {
  private final WeakReference<ClassLoader> classloader;
  private final String className;
  private final String methodSignature;

  public DiscoveredNode(ClassLoader classloader, String className, String methodSignature) {
    this.classloader = new WeakReference<>(classloader);
    this.className = className;
    this.methodSignature = methodSignature;
  }

  @Override
  public int hashCode() {
    return classloader.hashCode() + className.hashCode() + methodSignature.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof DiscoveredNode) {
      final DiscoveredNode otherNode = (DiscoveredNode) obj;
      return classloader.equals(otherNode.classloader) && className.equals(otherNode.className) && methodSignature.equals(otherNode.methodSignature);
    }
    return false;
  }
}
