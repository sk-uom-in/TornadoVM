package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.alloc.ComputeBlockOrder;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.phases.AllocationPhase;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.phases.util.Providers;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCanonicalizer;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLLIRGenerationPhase;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVBackend;
import uk.ac.manchester.tornado.runtime.graal.TornadoLIRSuites;
import uk.ac.manchester.tornado.runtime.graal.TornadoSuites;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoMidTierContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

public class SPIRVCompiler {

    private static final AtomicInteger compilationId = new AtomicInteger();

    private static final TimerKey CompilerTimer = DebugContext.timer("SPIRVGraalCompiler");
    private static final TimerKey FrontEnd = DebugContext.timer("SPIRVFrontend");
    private static final TimerKey BackEnd = DebugContext.timer("SPIRVBackend");
    private static final TimerKey EmitLIR = DebugContext.timer("SPIRVEmitLIR");
    private static final TimerKey EmitCode = DebugContext.timer("SPIRVEmitCode");

    private static final SPIRVIRGenerationPhase LIR_GENERATION_PHASE = new SPIRVIRGenerationPhase();

    public static class SPIRVCompilationRequest {
        public final StructuredGraph graph;
        public final ResolvedJavaMethod installedCodeOwner;
        public final Object[] args;
        public final TaskMetaData meta;
        public final Providers providers;
        public final SPIRVBackend backend;
        public final PhaseSuite<HighTierContext> graphBuilderSuite;
        public final OptimisticOptimizations optimisticOpts;
        public final ProfilingInfo profilingInfo;
        public final TornadoSuites suites;
        public final TornadoLIRSuites lirSuites;
        public final SPIRVCompilationResult compilationResult;
        public final CompilationResultBuilderFactory factory;
        public final boolean isKernel;
        public final boolean buildGraph;
        public final long batchThreads;

        public SPIRVCompilationRequest(StructuredGraph graph, ResolvedJavaMethod installedCodeOwner, Object[] args, TaskMetaData meta, Providers providers, SPIRVBackend backend,
                PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, TornadoSuites suites, TornadoLIRSuites lirSuites,
                SPIRVCompilationResult compilationResult, CompilationResultBuilderFactory factory, boolean isKernel, boolean buildGraph, long batchThreads) {
            this.graph = graph;
            this.installedCodeOwner = installedCodeOwner;
            this.args = args;
            this.meta = meta;
            this.providers = providers;
            this.backend = backend;
            this.graphBuilderSuite = graphBuilderSuite;
            this.optimisticOpts = optimisticOpts;
            this.profilingInfo = profilingInfo;
            this.suites = suites;
            this.lirSuites = lirSuites;
            this.compilationResult = compilationResult;
            this.factory = factory;
            this.isKernel = isKernel;
            this.buildGraph = buildGraph;
            this.batchThreads = batchThreads;
        }

        public SPIRVCompilationResult execute() {
            return SPIRVCompiler.compile(this);
        }
    }

    private static SPIRVCompilationResult compile(SPIRVCompilationRequest r) {
        assert !r.graph.isFrozen();
        try (DebugContext.Scope s0 = getDebugContext().scope("GraalCompiler", r.graph, r.providers.getCodeCache()); DebugCloseable a = CompilerTimer.start(getDebugContext())) {
            emitFrontEnd(r.providers, r.backend, r.installedCodeOwner, r.args, r.meta, r.graph, r.graphBuilderSuite, r.optimisticOpts, r.profilingInfo, r.suites, r.isKernel, r.buildGraph,
                    r.batchThreads);
            boolean isParallel = false;
            /*
             * A task is determined as parallel if: (i) it has loops annotated with {@link
             * uk.ac.manchester.tornado.api.annotations.Parallel} which corresponds to use a
             * domain with depth greater than zero, or (ii) it uses the GridScheduler.
             */
            if (r.meta != null && (r.meta.isParallel() || r.meta.isGridSchedulerEnabled())) {
                isParallel = true;
            }
            emitBackEnd(r.graph, null, r.installedCodeOwner, r.backend, r.compilationResult, r.factory, null, r.lirSuites, r.isKernel, isParallel);
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
        return r.compilationResult;
    }

    private static boolean isGraphEmpty(StructuredGraph graph) {
        return graph.start().next() == null;
    }

    private static void emitFrontEnd(Providers providers, SPIRVBackend backend, ResolvedJavaMethod installedCodeOwner, Object[] args, TaskMetaData meta, StructuredGraph graph,
            PhaseSuite<HighTierContext> graphBuilderSuite, OptimisticOptimizations optimisticOpts, ProfilingInfo profilingInfo, TornadoSuites suites, boolean isKernel, boolean buildGraph,
            long batchThreads) {

        try (DebugContext.Scope s = getDebugContext().scope("SPIRVFrontend", new DebugDumpScope("SPIRVFrontend")); DebugCloseable a = FrontEnd.start(getDebugContext())) {

            /*
             * Register metadata with all tornado phases
             */
            ((SPIRVCanonicalizer) suites.getHighTier().getCustomCanonicalizer()).setContext(providers.getMetaAccess(), installedCodeOwner, args, meta);

            final TornadoHighTierContext highTierContext = new TornadoHighTierContext(providers, graphBuilderSuite, optimisticOpts, installedCodeOwner, args, meta, isKernel, batchThreads);
            if (buildGraph) {
                if (isGraphEmpty(graph)) {
                    graphBuilderSuite.apply(graph, highTierContext);
                    new DeadCodeEliminationPhase(Optional).apply(graph);
                } else {
                    getDebugContext().dump(DebugContext.INFO_LEVEL, graph, "initial state");
                }
            }
            suites.getHighTier().apply(graph, highTierContext);
            graph.maybeCompress();

            final TornadoMidTierContext midTierContext = new TornadoMidTierContext(providers, backend, optimisticOpts, profilingInfo, installedCodeOwner, args, meta);
            suites.getMidTier().apply(graph, midTierContext);

            graph.maybeCompress();

            final LowTierContext lowTierContext = new LowTierContext(providers, backend);
            suites.getLowTier().apply(graph, lowTierContext);

            getDebugContext().dump(DebugContext.BASIC_LEVEL, graph.getLastSchedule(), "Final HIR schedule");

        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
    }

    private static void emitBackEnd(StructuredGraph graph, Object stub, ResolvedJavaMethod installedCodeOwner, SPIRVBackend backend, SPIRVCompilationResult compilationResult,
            CompilationResultBuilderFactory factory, RegisterConfig registerConfig, TornadoLIRSuites lirSuites, boolean isKernel, boolean isParallel) {
        try (DebugContext.Scope s = getDebugContext().scope("SPIRVBackend", graph.getLastSchedule()); DebugCloseable a = BackEnd.start(getDebugContext())) {
            LIRGenerationResult lirGen = null;
            lirGen = emitLIR(backend, graph, stub, registerConfig, lirSuites, compilationResult, isKernel);
            try (DebugContext.Scope s2 = getDebugContext().scope("SPIRVCodeGen", lirGen, lirGen.getLIR())) {
                int bytecodeSize = graph.method() == null ? 0 : graph.getBytecodeSize();
                compilationResult.setHasUnsafeAccess(graph.hasUnsafeAccess());
                emitCode(backend, graph.getAssumptions(), graph.method(), graph.getMethods(), bytecodeSize, lirGen, compilationResult, installedCodeOwner, factory, isKernel, isParallel);
            } catch (Throwable e) {
                throw getDebugContext().handle(e);
            }
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
    }

    private static LIRGenerationResult emitLIR(SPIRVBackend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, TornadoLIRSuites lirSuites,
            SPIRVCompilationResult compilationResult, boolean isKernel) {
        try {
            return emitLIR0(backend, graph, stub, registerConfig, lirSuites, compilationResult, isKernel);
        } catch (Throwable e) {
            throw new TornadoInternalError(e);
        }
    }

    private static <T extends CompilationResult> LIRGenerationResult emitLIR0(SPIRVBackend backend, StructuredGraph graph, Object stub, RegisterConfig registerConfig, TornadoLIRSuites lirSuites,
            T compilationResult, boolean isKernel) {
        try (DebugContext.Scope ds = getDebugContext().scope("EmitLIR"); DebugCloseable a = EmitLIR.start(getDebugContext())) {
            OptionValues options = graph.getOptions();
            StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
            Block[] blocks = schedule.getCFG().getBlocks();
            Block startBlock = schedule.getCFG().getStartBlock();
            assert startBlock != null;
            assert startBlock.getPredecessorCount() == 0;

            LIR lir = null;
            AbstractBlockBase<?>[] codeEmittingOrder = null;
            AbstractBlockBase<?>[] linearScanOrder = null;
            try (DebugContext.Scope s = getDebugContext().scope("ComputeLinearScanOrder", lir)) {
                codeEmittingOrder = ComputeBlockOrder.computeCodeEmittingOrder(blocks.length, startBlock);
                linearScanOrder = ComputeBlockOrder.computeLinearScanOrder(blocks.length, startBlock);
                lir = new LIR(schedule.getCFG(), linearScanOrder, codeEmittingOrder, options, getDebugContext());
                getDebugContext().dump(DebugContext.INFO_LEVEL, lir, "After linear scan order");
            } catch (Throwable e) {
                throw getDebugContext().handle(e);
            }
            RegisterAllocationConfig registerAllocationConfig = backend.newRegisterAllocationConfig(registerConfig, new String[] {});
            FrameMapBuilder frameMapBuilder = backend.newFrameMapBuilder(registerConfig);
            LIRGenerationResult lirGenRes = backend.newLIRGenerationResult(graph.compilationId(), lir, frameMapBuilder, registerAllocationConfig, graph, stub);
            LIRGeneratorTool lirGen = backend.newLIRGenerator(lirGenRes);
            NodeLIRBuilderTool nodeLirGen = backend.newNodeLIRBuilder(graph, lirGen);

            // LIR generation
            SPIRVIRGenerationPhase.LIRGenerationContext context = new SPIRVIRGenerationPhase.LIRGenerationContext(lirGen, nodeLirGen, graph, schedule, isKernel);
            LIR_GENERATION_PHASE.apply(backend.getTarget(), lirGenRes, context);

            try (DebugContext.Scope s = getDebugContext().scope("LIRStages", nodeLirGen, lir)) {
                getDebugContext().dump(DebugContext.BASIC_LEVEL, lir, "After LIR generation");
                LIRGenerationResult result = emitLowLevel(backend.getTarget(), lirGenRes, lirGen, lirSuites, registerAllocationConfig);
                getDebugContext().dump(DebugContext.BASIC_LEVEL, lir, "Before code generation");
                return result;
            } catch (Throwable e) {
                throw getDebugContext().handle(e);
            }
        } catch (Throwable e) {
            throw getDebugContext().handle(e);
        }
    }

    private static LIRGenerationResult emitLowLevel(TargetDescription target, LIRGenerationResult lirGenRes, LIRGeneratorTool lirGen, TornadoLIRSuites lirSuites,
            RegisterAllocationConfig registerAllocationConfig) {
        final PreAllocationOptimizationPhase.PreAllocationOptimizationContext preAllocOptContext = new PreAllocationOptimizationPhase.PreAllocationOptimizationContext(lirGen);
        lirSuites.getPreAllocationStage().apply(target, lirGenRes, preAllocOptContext);
        AllocationPhase.AllocationContext allocContext = new AllocationPhase.AllocationContext(lirGen.getSpillMoveFactory(), registerAllocationConfig);
        lirSuites.getAllocationStage().apply(target, lirGenRes, allocContext);
        return lirGenRes;
    }

    private static void emitCode(SPIRVBackend backend, Assumptions assumptions, ResolvedJavaMethod rootMethod, List<ResolvedJavaMethod> methods, int bytecodeSize, LIRGenerationResult lirGen,
            SPIRVCompilationResult compilationResult, ResolvedJavaMethod installedCodeOwner, CompilationResultBuilderFactory factory, boolean isKernel, boolean isParallel) {
        try (DebugCloseable a = EmitCode.start(getDebugContext())) {
            FrameMap frameMap = lirGen.getFrameMap();
            final SPIRVCompilationResultBuilder crb = backend.newCompilationResultBuilder(lirGen, frameMap, compilationResult, factory, isKernel, isParallel);
            backend.emitCode(crb, lirGen.getLIR(), installedCodeOwner);

            if (assumptions != null && !assumptions.isEmpty()) {
                compilationResult.setAssumptions(assumptions.toArray());
            }
            if (methods != null) {
                compilationResult.setMethods(rootMethod, methods);
            }

            compilationResult.setNonInlinedMethods(crb.getNonInlinedMethods());
            crb.finish();

            if (getDebugContext().isCountEnabled()) {
                DebugContext.counter("CompilationResults").increment(getDebugContext());
                DebugContext.counter("CodeBytesEmitted").add(getDebugContext(), compilationResult.getTargetCodeSize());
            }

            getDebugContext().dump(DebugContext.BASIC_LEVEL, compilationResult, "After code generation");
        }
    }

}
