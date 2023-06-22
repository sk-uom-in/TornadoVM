/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2023, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import uk.ac.manchester.tornado.api.GridScheduler;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graph.TornadoExecutionContext;
import uk.ac.manchester.tornado.runtime.graph.TornadoGraph;
import uk.ac.manchester.tornado.runtime.graph.TornadoVMBytecodeResult;
import uk.ac.manchester.tornado.runtime.graph.TornadoVMGraphCompiler;
import uk.ac.manchester.tornado.runtime.interpreter.TornadoVMInterpreter;
import uk.ac.manchester.tornado.runtime.tasks.TornadoTaskGraph;

/**
 * * There is an instance of the {@link TornadoVM} per {@link TornadoTaskGraph}.
 * * Each TornadoVM contains the logic to orchestrate the execution on the *
 * parallel device (e.g., a GPU).
 */
public class TornadoVM extends TornadoLogger {
    private final TornadoExecutionContext executionContext;
    private final boolean setNewDevice;

    private final TornadoProfiler timeProfiler;

    private final TornadoVMBytecodeResult[] tornadoVMBytecodes;

    private final TornadoVMInterpreter[] tornadoVMInterpreters;

    /**
     * It constructs a new TornadoVM instance.
     *
     * @param executionContext
     *            the {@link TornadoExecutionContext} for containing the execution
     *            context
     * @param tornadoGraph
     *            the {@link TornadoGraph} representing the TaskGraph
     * @param timeProfiler
     *            the {@link TornadoProfiler} for profiling execution time
     * @param batchSize
     *            the batch size when running in batch mode
     */
    public TornadoVM(TornadoExecutionContext executionContext, TornadoGraph tornadoGraph, TornadoProfiler timeProfiler, long batchSize, boolean setNewDevice) {
        this.executionContext = executionContext;
        this.timeProfiler = timeProfiler;
        this.setNewDevice = setNewDevice;
        tornadoVMBytecodes = TornadoVMGraphCompiler.compile(tornadoGraph, executionContext, batchSize);
        tornadoVMInterpreters = new TornadoVMInterpreter[executionContext.getValidContextSize()];
        bindBytecodesToInterpreters();
    }

    /**
     * It binds bytecodes to interpreters for each valid context. One valid context
     * per assigned device.
     */
    private void bindBytecodesToInterpreters() {
        IntStream.range(0, executionContext.getValidContextSize())
                .forEach(i -> tornadoVMInterpreters[i] = new TornadoVMInterpreter(executionContext, tornadoVMBytecodes[i], timeProfiler, executionContext.getDevices().get(i)));
    }

    /**
     * It executes the interpreter manager either concurrently in multiple threads
     * or in single-threaded mode.
     *
     * @return An {@link Event} indicating the completion of execution.
     */
    public Event execute() {
        return executeThreadManagerForInterpreters();
    }

    private int getNumberOfThreadsToSpawn() {
        return shouldRunConcurrently() ? executionContext.getValidContextSize() : 1;
    }

    /**
     * It executes the interpreter manager either in concurrently in multiple
     * threads or in single-threaded mode.
     *
     * @return An {@link Event} indicating the completion of execution.
     */
    private Event executeThreadManagerForInterpreters() {
        // Create a thread pool with a fixed number of threads
        int numThreads = getNumberOfThreadsToSpawn();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Create a list to hold the futures of each execution
        List<Future<?>> futures = new ArrayList<>();

        // Submit each task to the thread pool
        for (TornadoVMInterpreter tornadoVMInterpreter : tornadoVMInterpreters) {
            Future<?> future = executor.submit(() -> tornadoVMInterpreter.execute(false));
            futures.add(future);
        }
        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            try {
                future.get(); // Blocking call to wait for the task to complete
            } catch (Exception e) {
                // Handle any exceptions that occurred during execution
                e.printStackTrace();
            }
        }
        // Shutdown the executor after all tasks have completed
        executor.shutdown();

        return new EmptyEvent();
    }

    private boolean shouldRunConcurrently() {
        return TornadoOptions.CONCURRENT_INTERPRETERS && (executionContext.getValidContextSize() > 1);
    }

    public void executeActionOnInterpreters(Consumer<TornadoVMInterpreter> action) {
        for (TornadoVMInterpreter tornadoVMInterpreter : tornadoVMInterpreters) {
            action.accept(tornadoVMInterpreter);
        }
    }

    public void clearInstalledCode() {
        executeActionOnInterpreters(TornadoVMInterpreter::clearInstalledCode);
    }

    public void setCompileUpdate() {
        executeActionOnInterpreters(TornadoVMInterpreter::setCompileUpdate);
    }

    public void dumpProfiles() {
        executeActionOnInterpreters(TornadoVMInterpreter::dumpProfiles);
    }

    public void dumpEvents() {
        executeActionOnInterpreters(TornadoVMInterpreter::dumpEvents);
    }

    public void clearProfiles() {
        executeActionOnInterpreters(TornadoVMInterpreter::clearProfiles);
    }

    public void printTimes() {
        executeActionOnInterpreters(TornadoVMInterpreter::printTimes);
    }

    public void warmup() {
        executeActionOnInterpreters(TornadoVMInterpreter::warmup);
    }

    public void fetchGlobalStates() {
        executeActionOnInterpreters(TornadoVMInterpreter::fetchGlobalStates);
    }

    public void setGridScheduler(GridScheduler gridScheduler) {
        for (TornadoVMInterpreter interpreter : tornadoVMInterpreters) {
            interpreter.setGridScheduler(gridScheduler);
        }
    }

}