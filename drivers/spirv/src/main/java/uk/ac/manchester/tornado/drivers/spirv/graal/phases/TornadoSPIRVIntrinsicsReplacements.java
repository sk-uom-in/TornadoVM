package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVLoweringProvider;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalGroupSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalThreadIdFixedNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVBarrierNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoSPIRVIntrinsicsReplacements extends BasePhase<TornadoHighTierContext> {

    private MetaAccessProvider metaAccessProvider;

    public TornadoSPIRVIntrinsicsReplacements(MetaAccessProvider metaAccessProvider) {
        this.metaAccessProvider = metaAccessProvider;
    }

    private ConstantNode getConstantNodeFromArguments(InvokeNode invoke, int index) {
        NodeInputList<ValueNode> arguments = invoke.callTarget().arguments();
        return (ConstantNode) arguments.get(index);
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        NodeIterable<InvokeNode> invokeNodes = graph.getNodes().filter(InvokeNode.class);

        for (InvokeNode invoke : invokeNodes) {
            String methodName = invoke.callTarget().targetName();

            switch (methodName) {
                case "Direct#NewArrayNode.newArray": {
                    lowerInvokeNode(invoke);
                    break;
                }
                case "Direct#OpenCLIntrinsics.localBarrier": {
                    SPIRVBarrierNode barrierNode = graph.addOrUnique(new SPIRVBarrierNode(SPIRVBarrierNode.SPIRVMemFenceFlags.LOCAL));
                    graph.replaceFixed(invoke, barrierNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.globalBarrier": {
                    SPIRVBarrierNode barrierNode = graph.addOrUnique(new SPIRVBarrierNode(SPIRVBarrierNode.SPIRVMemFenceFlags.GLOBAL));
                    graph.replaceFixed(invoke, barrierNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_local_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    LocalThreadIdFixedNode localIdNode = graph.addOrUnique(new LocalThreadIdFixedNode(dimension));
                    graph.replaceFixed(invoke, localIdNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_local_size": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    LocalGroupSizeNode groupSize = graph.addOrUnique(new LocalGroupSizeNode(dimension));
                    graph.replaceFixed(invoke, groupSize);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_global_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GlobalThreadIdNode globalThreadIdNode = graph.addOrUnique(new GlobalThreadIdNode(dimension));
                    graph.replaceFixed(invoke, globalThreadIdNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_global_size": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GlobalThreadSizeNode globalThreadSizeNode = graph.addOrUnique(new GlobalThreadSizeNode(dimension));
                    graph.replaceFixed(invoke, globalThreadSizeNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_group_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GroupIdNode groupIdNode = graph.addOrUnique(new GroupIdNode(dimension));
                    graph.replaceFixed(invoke, groupIdNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.printEmpty": {
                    throw new RuntimeException("Unimplemented");
                }
            }
        }
    }

    private void lowerLocalInvokeNodeNewArray(StructuredGraph graph, int length, JavaKind elementKind, InvokeNode invokeWithNewArray) {
        ConstantNode newLengthNode = ConstantNode.forInt(length, graph);
        ResolvedJavaType elementType = metaAccessProvider.lookupJavaType(elementKind.toJavaClass());
        LocalArrayNode localArrayNode = graph.addOrUnique(new LocalArrayNode(SPIRVArchitecture.localSpace, elementType, newLengthNode));
        invokeWithNewArray.replaceAtUsages(localArrayNode);
    }

    private void lowerPrivateInvokeNodeNewArray(StructuredGraph graph, int size, JavaKind elementKind, InvokeNode invokeWithNewArray) {
        final ConstantNode newLenghNode = ConstantNode.forInt(size, graph);
        ResolvedJavaType elementType = metaAccessProvider.lookupJavaType(elementKind.toJavaClass());
        FixedArrayNode fixedArrayNode = graph.addOrUnique(new FixedArrayNode(SPIRVArchitecture.globalSpace, elementType, newLenghNode));
        invokeWithNewArray.replaceAtUsages(fixedArrayNode);
    }

    private void lowerInvokeNode(InvokeNode invokeWithArrayAlloc) {
        CallTargetNode callTarget = invokeWithArrayAlloc.callTarget();
        final StructuredGraph graph = invokeWithArrayAlloc.graph();
        final ValueNode secondInput = callTarget.arguments().get(1);

        if (secondInput instanceof ConstantNode) {
            final ConstantNode lengthNode = (ConstantNode) secondInput;
            if (lengthNode.getValue() instanceof PrimitiveConstant) {
                final int length = ((PrimitiveConstant) lengthNode.getValue()).asInt();
                JavaKind elementKind = getJavaKindFromConstantNode((ConstantNode) callTarget.arguments().get(0));
                final int offset = metaAccessProvider.getArrayBaseOffset(elementKind);
                final int size = offset + (elementKind.getByteCount() * length);

                if (SPIRVLoweringProvider.isGPUSnippet()) {
                    lowerLocalInvokeNodeNewArray(graph, length, elementKind, invokeWithArrayAlloc);
                } else {
                    lowerPrivateInvokeNodeNewArray(graph, size, elementKind, invokeWithArrayAlloc);
                }

                invokeWithArrayAlloc.clearInputs();
                GraphUtil.unlinkFixedNode(invokeWithArrayAlloc);
            } else {
                throw new RuntimeException("Unimplemented");
            }
        } else {
            throw new RuntimeException("dynamically sized array declarations are not supported");
        }
    }

    private JavaKind getJavaKindFromConstantNode(ConstantNode signatureNode) {
        switch (signatureNode.getValue().toValueString()) {
            case "Class:int":
                return JavaKind.Int;
            case "Class:long":
                return JavaKind.Long;
            case "Class:float":
                return JavaKind.Float;
            case "Class:double":
                return JavaKind.Double;
            default:
                unimplemented("Other types not supported yet: " + signatureNode.getValue().toValueString());
        }
        return null;
    }
}
