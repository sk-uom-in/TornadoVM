package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXBinary;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXLIRStmt;

import static uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.PTXBuiltInRegisterArray;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXBinaryOp.MUL;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXBinaryOp.MUL_LO;
import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXBinaryOp.MUL_WIDE;

@NodeInfo
public class GlobalThreadSizeNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<GlobalThreadSizeNode> TYPE = NodeClass.create(GlobalThreadSizeNode.class);

    @Input protected ConstantNode index;

    public GlobalThreadSizeNode(ConstantNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        assert stamp != null;
        index = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
//        LIRKind kind = tool.getLIRKind(stamp);
//        LIRKind kind = LIRKind.value(PTXKind.U32);
        Variable result = tool.newVariable(LIRKind.value(PTXKind.U64));
        PTXNodeLIRBuilder ptxNodeBuilder = (PTXNodeLIRBuilder) gen;

        PTXBuiltInRegisterArray builtIns = new PTXBuiltInRegisterArray(((ConstantValue)gen.operand(index)).getJavaConstant().asInt());
        Variable gridDim = ptxNodeBuilder.getBuiltInAllocation(builtIns.gridDim);
        Variable blockDim = ptxNodeBuilder.getBuiltInAllocation(builtIns.blockDim);


        Value var = tool.append(new PTXLIRStmt.AssignStmt(result, new PTXBinary.Expr(MUL_WIDE, LIRKind.value(PTXKind.U32), gridDim, blockDim))).getResult();

        Variable actualResult = tool.newVariable(LIRKind.value(PTXKind.S32));
        tool.append(new PTXLIRStmt.AssignStmt(actualResult, var));
        gen.setResult(this, actualResult);
    }

}
