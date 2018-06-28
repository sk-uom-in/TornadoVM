/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
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
 * Authors: Juan Fumero
 *
 */

package uk.ac.manchester.tornado.unittests.reductions;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;

import uk.ac.manchester.tornado.api.Parallel;
import uk.ac.manchester.tornado.api.Reduce;
import uk.ac.manchester.tornado.drivers.opencl.builtins.OpenCLIntrinsics;
import uk.ac.manchester.tornado.drivers.opencl.enums.OCLDeviceType;
import uk.ac.manchester.tornado.drivers.opencl.runtime.OCLTornadoDevice;
import uk.ac.manchester.tornado.runtime.TornadoDriver;
import uk.ac.manchester.tornado.runtime.TornadoRuntime;
import uk.ac.manchester.tornado.runtime.api.TaskSchedule;
import uk.ac.manchester.tornado.unittests.common.TornadoTestBase;

public class TestReductionsIntegers extends TornadoTestBase {

    public static final int SMALL_SIZE = 512;
    public static final int BIG_SIZE = 1024;

    public static final int SIZE2 = 524288;
    public static final int SIZE = 4096;

    @Test
    public void testReductionAnnotationCPUSimple() {

        // This test has to be executed on CPU
        TornadoDriver driver = TornadoRuntime.getTornadoRuntime().getDriver(0);
        OCLTornadoDevice device = (OCLTornadoDevice) driver.getDefaultDevice();
        if (device.getDevice().getDeviceType() != OCLDeviceType.CL_DEVICE_TYPE_CPU) {
            return;
        }

        int numProcessors = Runtime.getRuntime().availableProcessors();

        int[] input = new int[SMALL_SIZE];
        int[] result = new int[numProcessors];

        IntStream.range(0, SMALL_SIZE).parallel().forEach(i -> {
            input[i] = 2;
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::reductionAnnotation, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        int[] sequential = new int[1];
        reductionAnnotation(input, sequential);

        // Final result
        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    /**
     * First approach: use annotations in the user code to identify the
     * reduction variables. This is a similar approach to OpenMP and OpenACC.
     * 
     * @param input
     * @param result
     */
    public static void reductionAnnotation(int[] input, @Reduce int[] result) {
        result[0] = 0;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    public static void reductionAnnotation2(int[] input, @Reduce int[] result) {
        result[0] = 0;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += input[i];
        }
    }

    @Test
    public void testReductionAnnotation() {
        int[] input = new int[SIZE];
        int[] result = null;

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        OCLDeviceType deviceType = getDefaultDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                result = new int[Runtime.getRuntime().availableProcessors()];
                break;
            case CL_DEVICE_TYPE_GPU:
            case CL_DEVICE_TYPE_ACCELERATOR:
                result = new int[numGroups];
                break;
            default:
                break;
        }

        IntStream.range(0, SIZE).parallel().forEach(i -> {
            input[i] = 2;
        });

        //@formatter:off
		new TaskSchedule("s0")
			.streamIn(input)
			.task("t0", TestReductionsIntegers::reductionAnnotation2, input, result)
			.streamOut(result)
			.execute();
		//@formatter:on

        int[] sequential = new int[1];
        reductionAnnotation(input, sequential);

        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    public static void multReductionAnnotation(int[] input, @Reduce int[] result, int neutral) {
        for (@Parallel int i = 0; i < result.length; i++) {
            result[i] = neutral;
        }
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] *= input[i];
        }
    }

    @Test
    public void testMultiplicationReduction() {
        int[] input = new int[64];
        int[] result = null;

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        OCLDeviceType deviceType = getDefaultDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                result = new int[Runtime.getRuntime().availableProcessors()];
                break;
            case CL_DEVICE_TYPE_ACCELERATOR:
            case CL_DEVICE_TYPE_GPU:
                result = new int[numGroups];
                break;
            default:
                break;
        }

        Arrays.fill(input, 1);
        input[10] = new Random().nextInt() * 10;

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::multReductionAnnotation, input, result, 1)
            .streamOut(result)
            .execute();
        //@formatter:on

        // Final result
        for (int i = 1; i < result.length; i++) {
            result[0] *= result[i];
        }

        int[] sequential = new int[1];
        multReductionAnnotation(input, sequential, 1);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    public static void maxReductionAnnotation(int[] input, @Reduce int[] result, int neutral) {
        result[0] = neutral;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] = Math.max(result[0], input[i]);
        }
    }

    @Ignore
    public void testMaxReduction() {
        int[] input = new int[SIZE];
        int[] result = new int[SIZE];

        IntStream.range(0, SIZE).forEach(idx -> {
            input[idx] = idx;
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::maxReductionAnnotation, input, result, Integer.MIN_VALUE)
            .streamOut(result)
            .execute();
        //@formatter:on

        // Final result
        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        for (int i = 1; i < numGroups; i++) {
            result[0] += Math.max(result[0], result[i]);
        }

        int[] sequential = new int[1];
        maxReductionAnnotation(input, sequential, 1);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

    public static void reductionSequentialSmall(int[] input, int[] result) {
        int acc = 0; // neutral element for the addition
        for (int i = 0; i < input.length; i++) {
            acc += input[i];
        }
        result[0] = acc;
    }

    @Test
    public void testSequentialReduction() {
        int[] input = new int[SMALL_SIZE * 2];

        int[] result = null;

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        OCLDeviceType deviceType = getDefaultDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                result = new int[Runtime.getRuntime().availableProcessors()];
                break;
            case CL_DEVICE_TYPE_GPU:
            case CL_DEVICE_TYPE_ACCELERATOR:
                result = new int[numGroups];
                break;
            default:
                break;
        }

        Random r = new Random();

        IntStream.range(0, SMALL_SIZE * 2).parallel().forEach(i -> {
            input[i] = r.nextInt();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::reductionSequentialSmall, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        int[] sequential = new int[1];

        reductionSequentialSmall(input, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

    public static void reduction01(int[] a, @Reduce int[] result) {
        result[0] = 0;
        for (@Parallel int i = 0; i < a.length; i++) {
            result[0] += a[i];
        }
    }

    @Test
    public void testReduction01() {
        int[] input = new int[SMALL_SIZE];

        int[] result = null;

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        OCLDeviceType deviceType = getDefaultDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                result = new int[Runtime.getRuntime().availableProcessors()];
                break;
            case CL_DEVICE_TYPE_GPU:
            case CL_DEVICE_TYPE_ACCELERATOR:
                result = new int[numGroups];
                break;
            default:
                break;
        }

        Random r = new Random();

        IntStream.range(0, SMALL_SIZE).parallel().forEach(i -> {
            input[i] = r.nextInt();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::reduction01, input, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        int[] sequential = new int[1];
        reduction01(input, sequential);

        assertEquals(sequential[0], result[0]);
    }

    public static void mapReduce01(int[] a, int[] b, int[] c, @Reduce int[] result) {

        // map
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }

        OpenCLIntrinsics.globalBarrier();

        // reduction
        result[0] = 0;
        for (@Parallel int i = 0; i < c.length; i++) {
            result[0] += c[i];
        }
    }

    public static void map01(int[] a, int[] b, int[] c) {
        for (@Parallel int i = 0; i < a.length; i++) {
            c[i] = a[i] + b[i];
        }
    }

    public static void reduce01(int[] c, @Reduce int[] result) {
        // reduction
        result[0] = 0;
        for (@Parallel int i = 0; i < c.length; i++) {
            result[0] += c[i];
        }
    }

    @Test
    public void testMapReduce() {
        int[] a = new int[BIG_SIZE];
        int[] b = new int[BIG_SIZE];
        int[] c = new int[BIG_SIZE];

        int[] result = null;

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        OCLDeviceType deviceType = getDefaultDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                result = new int[Runtime.getRuntime().availableProcessors()];
                break;
            case CL_DEVICE_TYPE_ACCELERATOR:
            case CL_DEVICE_TYPE_GPU:
                result = new int[numGroups];
                break;
            default:
                break;
        }

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            a[i] = 10;
            b[i] = 2;
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b, c)
            .task("t0", TestReductionsIntegers::map01, a, b, c)
            .task("t1", TestReductionsIntegers::reduce01, c, result)
            .streamOut(result)
            .execute();        
        //@formatter:on

        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        int[] sequential = new int[BIG_SIZE];
        mapReduce01(a, b, c, sequential);

        assertEquals(sequential[0], result[0]);
    }

    /**
     * Currently we cannot do this due to synchronisation between the first part
     * and the second part, unless an explicit barrier is used.
     */
    @Ignore
    public void testMapReduceSameKernel() {
        int[] a = new int[BIG_SIZE];
        int[] b = new int[BIG_SIZE];
        int[] c = new int[BIG_SIZE];

        int[] result = null;

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        OCLDeviceType deviceType = getDefaultDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                result = new int[Runtime.getRuntime().availableProcessors()];
                break;
            case CL_DEVICE_TYPE_ACCELERATOR:
            case CL_DEVICE_TYPE_GPU:
                result = new int[numGroups];
                break;
            default:
                break;
        }

        Random r = new Random();

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a, b, c)
            .task("t0", TestReductionsIntegers::mapReduce01, a, b, c, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        for (int i = 1; i < numGroups; i++) {
            result[0] += result[i];
        }

        int[] sequential = new int[BIG_SIZE];

        mapReduce01(a, b, c, sequential);

        assertEquals(sequential[0], result[0]);
    }

    /**
     * We reuse one of the input values
     * 
     * @param a
     * @param b
     * @param result
     */
    public static void mapReduce2(int[] a, int[] b, @Reduce int[] result) {
        // map
        for (@Parallel int i = 0; i < a.length; i++) {
            a[i] = a[i] * b[i];
        }

        for (@Parallel int i = 0; i < a.length; i++) {
            result[0] += a[i];
        }
    }

    @Ignore
    public void testMapReduce2() {
        int[] a = new int[BIG_SIZE];
        int[] b = new int[BIG_SIZE];

        int[] result = null;

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        OCLDeviceType deviceType = getDefaultDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                result = new int[Runtime.getRuntime().availableProcessors()];
                break;
            case CL_DEVICE_TYPE_ACCELERATOR:
            case CL_DEVICE_TYPE_GPU:
                result = new int[numGroups];
                break;
            default:
                break;
        }

        IntStream.range(0, BIG_SIZE).parallel().forEach(i -> {
            a[i] = 1;
            b[i] = 2;
        });

        //@formatter:off
	    new TaskSchedule("s0")
	         .streamIn(a)
	         .task("t0", TestReductionsIntegers::mapReduce2, a, b, result)
	         .streamOut(result)
	         .execute();
	    //@formatter:on

        for (int i = 1; i < numGroups; i++) {
            result[0] += result[i];
        }

        int[] sequential = new int[BIG_SIZE];
        mapReduce2(a, b, sequential);

        assertEquals(sequential[0], result[0]);
    }

    public static void testThreadSchuler(int[] a, int[] b, int[] result) {

        // map
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = a[i] * b[i];
        }

        // map
        for (@Parallel int i = 0; i < a.length; i++) {
            result[i] = result[i] * b[i];
        }

    }

    @Test
    public void testThreadSchuler() {
        int[] a = new int[SMALL_SIZE * 2];
        int[] b = new int[SMALL_SIZE * 2];
        int[] result = new int[SMALL_SIZE * 2];

        Random r = new Random();

        IntStream.range(0, SMALL_SIZE * 2).parallel().forEach(i -> {
            a[i] = r.nextInt();
            b[i] = r.nextInt();
        });

        //@formatter:off
        new TaskSchedule("s0")
            .streamIn(a)
            .task("t0", TestReductionsIntegers::testThreadSchuler, a, b, result)
            .streamOut(result)
            .execute();
        //@formatter:on

        int[] sequential = new int[SMALL_SIZE * 2];
        testThreadSchuler(a, b, sequential);

        assertEquals(sequential[0], result[0], 0.001f);
    }

    public static void reductionAddInts2(int[] input, @Reduce int[] result) {
        int error = 2;
        for (@Parallel int i = 0; i < input.length; i++) {
            result[0] += (error + input[i]);
        }
    }

    public static void reductionAddInts3(int[] inputA, int[] inputB, @Reduce int[] result) {
        for (@Parallel int i = 0; i < inputA.length; i++) {
            result[0] += (inputA[i] + inputB[i]);
        }
    }

    @Test
    public void testSumInts2() {
        int[] input = new int[SMALL_SIZE];

        int[] result = null;

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        OCLDeviceType deviceType = getDefaultDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                result = new int[Runtime.getRuntime().availableProcessors()];
                break;
            case CL_DEVICE_TYPE_GPU:
            case CL_DEVICE_TYPE_ACCELERATOR:
                result = new int[numGroups];
                break;
            default:
                break;
        }

        IntStream.range(0, SMALL_SIZE).sequential().forEach(i -> {
            input[i] = 2;
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(input)
            .task("t0", TestReductionsIntegers::reductionAddInts2, input, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        int[] sequential = new int[1];
        reductionAddInts2(input, sequential);

        assertEquals(sequential[0], result[0]);
    }

    @Test
    public void testSumInts3() {
        int[] inputA = new int[SIZE];
        int[] inputB = new int[SIZE];

        int[] result = null;

        int numGroups = 1;
        if (SIZE > 256) {
            numGroups = SIZE / 256;
        }
        OCLDeviceType deviceType = getDefaultDeviceType();
        switch (deviceType) {
            case CL_DEVICE_TYPE_CPU:
                result = new int[Runtime.getRuntime().availableProcessors()];
                break;
            case CL_DEVICE_TYPE_DEFAULT:
                break;
            case CL_DEVICE_TYPE_ACCELERATOR:
            case CL_DEVICE_TYPE_GPU:
                result = new int[numGroups];
                break;
            default:
                break;
        }

        Random r = new Random();
        IntStream.range(0, SIZE).sequential().forEach(i -> {
            inputA[i] = r.nextInt();
            inputB[i] = r.nextInt();
        });

        //@formatter:off
        TaskSchedule task = new TaskSchedule("s0")
            .streamIn(inputA, inputB)
            .task("t0", TestReductionsIntegers::reductionAddInts3, inputA, inputB, result)
            .streamOut(result);
        //@formatter:on

        task.execute();

        for (int i = 1; i < result.length; i++) {
            result[0] += result[i];
        }

        int[] sequential = new int[1];
        reductionAddInts3(inputA, inputB, sequential);

        // Check result
        assertEquals(sequential[0], result[0]);
    }

}
