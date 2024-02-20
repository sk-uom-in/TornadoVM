package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.RawConstant;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.phases.Phase;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLAddressNode;
import uk.ac.manchester.tornado.runtime.graal.nodes.VectorHalfRead;

import java.util.Optional;

public class TornadoHalfFloatVectorOffset extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void run(StructuredGraph graph) {
        for (ReadNode readNode : graph.getNodes().filter(ReadNode.class)) {
            if (readNode.successors().filter(VectorHalfRead.class).isNotEmpty()) {
                VectorHalfRead vectorHalfRead = readNode.successors().filter(VectorHalfRead.class).first();
                replaceOffset(readNode, vectorHalfRead, graph);
                deleteFixed(vectorHalfRead);
            }
        }
    }

    private static void replaceOffset(ReadNode readNode, VectorHalfRead vectorHalfRead, StructuredGraph graph) {
        OCLAddressNode oclAddressNode = (OCLAddressNode) readNode.getAddress();
        ValueNode index = oclAddressNode.getIndex();
        if (index instanceof ConstantNode) {
            int vectorReadIndex = vectorHalfRead.getIndex();
            // if the index value has been initialized
            if (vectorReadIndex != -1) {
                Constant shortOffset = new RawConstant(vectorReadIndex * 2 + 24);
                ConstantNode shortOffsetNode = new ConstantNode(shortOffset, StampFactory.forKind(JavaKind.Int));
                graph.addWithoutUnique(shortOffsetNode);
                oclAddressNode.replaceFirstInput(index, shortOffsetNode);
                if (index.usages().isEmpty()) {
                    index.safeDelete();
                }
            }
        } else if (index.inputs().filter(LeftShiftNode.class).isNotEmpty()) {
            LeftShiftNode leftShiftNode = index.inputs().filter(LeftShiftNode.class).first();
            ConstantNode currentOffset = leftShiftNode.inputs().filter(ConstantNode.class).first();
            if (currentOffset.getValue().toValueString().equals("3")) {
                Constant shortOffset = new RawConstant(1);
                ConstantNode shortOffsetNode = new ConstantNode(shortOffset, StampFactory.forKind(JavaKind.Int));
                graph.addWithoutUnique(shortOffsetNode);
                leftShiftNode.replaceFirstInput(currentOffset, shortOffsetNode);
                if (currentOffset.usages().isEmpty()) {
                    currentOffset.safeDelete();
                }
            }
        }
    }

    private static void deleteFixed(Node n) {
        Node pred = n.predecessor();
        Node suc = n.successors().first();

        n.replaceFirstSuccessor(suc, null);
        n.replaceAtPredecessor(suc);
        pred.replaceFirstSuccessor(n, suc);

        for (Node us : n.usages()) {
            n.removeUsage(us);
        }
        n.clearInputs();

        n.safeDelete();
    }
}
