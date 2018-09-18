package datadog.trace.instrumentation.autotrace;

import datadog.trace.agent.tooling.Utils;
import datadog.trace.bootstrap.autotrace.DiscoveredNode;
import datadog.trace.bootstrap.autotrace.TraceDiscoveryGraph;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;

import java.util.ArrayList;
import java.util.List;

/**
 * TODO: Doc
 */
@Slf4j
public class MethodExpander implements AsmVisitorWrapper {
  private final List<DiscoveredNode> nodesToExpand;
  private final ClassLoader classLoader;
  private final String className;

  public MethodExpander(ClassLoader classLoader, String className, List<DiscoveredNode> nodesToExpand) {
    for (final DiscoveredNode node : nodesToExpand) {
      if (!node.getClassName().equals(className)) {
        throw new IllegalStateException("Node <" + node + " does not match visited type: " + className);
      }
    }
    this.nodesToExpand = nodesToExpand;
    this.className = className;
    this.classLoader = classLoader;
  }

  @Override
  public int mergeWriter(int flags) {
    return flags;
  }

  @Override
  public int mergeReader(int flags) {
    return flags;
  }

  @Override
  public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor classVisitor, Implementation.Context implementationContext, TypePool typePool, FieldList<FieldDescription.InDefinedShape> fields, MethodList<?> methods, int writerFlags, int readerFlags) {
    if (className.equals(instrumentedType.getName())) {
      classVisitor = new ExpansionVisitor(classVisitor);
    } else {
      log.debug("Skipping expansion for {}. Class name does not match expected name: {}", instrumentedType.getName(), className);
    }
    return classVisitor;
  }

  private class ExpansionVisitor extends ClassVisitor {

    public ExpansionVisitor(ClassVisitor classVisitor) {
      super(Opcodes.ASM6, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
      final String nodeSignature = name+descriptor;
      for (final DiscoveredNode node : nodesToExpand) {
        if (node.getMethodSignature().equals(nodeSignature) && (!node.isExpanded())) {
          System.out.println("-- EXPAND METHOD: " + className + "." + name + descriptor);
          mv = new ExpansionMethodVisitor(node, mv);
        }
      }
      return mv;
    }

    private class ExpansionMethodVisitor extends MethodVisitor {
      private final List<DiscoveredNode> edges = new ArrayList<>();
      private final DiscoveredNode node;

      public ExpansionMethodVisitor(DiscoveredNode node, MethodVisitor methodVisitor) {
        super(Opcodes.ASM6, methodVisitor);
        this.node = node;
      }

      @Override
      public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        edges.add(TraceDiscoveryGraph.discoverOrGet(classLoader, Utils.getClassName(owner), name+descriptor));
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }

      @Override
      public void visitEnd() {
        node.addEdges(edges);
        super.visitEnd();
      }
    }
  }
}
