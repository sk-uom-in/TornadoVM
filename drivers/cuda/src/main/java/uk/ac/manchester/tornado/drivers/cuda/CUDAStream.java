package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.drivers.cuda.CUDAEvent.EventDescription;
import uk.ac.manchester.tornado.runtime.EmptyEvent;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static uk.ac.manchester.tornado.runtime.common.Tornado.DEBUG;

public class CUDAStream extends TornadoLogger {

    protected static final Event EMPTY_EVENT = new EmptyEvent();

    private final byte[] streamWrapper;
    private Map<Integer, CUDAEvent> recordedEvents;
    private int eventCount;

    public CUDAStream() {
        streamWrapper = cuCreateStream();
        recordedEvents = new HashMap<>();
        eventCount = 0;
    }

    private native static byte[][] writeArrayDtoH(long address, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoH(long address, long length, short[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoH(long address, long length, char[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoH(long address, long length, int[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoH(long address, long length, long[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoH(long address, long length, float[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoH(long address, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private native static byte[][] writeArrayDtoHAsync(long address, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoHAsync(long address, long length, short[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoHAsync(long address, long length, char[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoHAsync(long address, long length, int[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoHAsync(long address, long length, long[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoHAsync(long address, long length, float[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayDtoHAsync(long address, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private native static byte[][] writeArrayHtoD(long address, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoD(long address, long length, short[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoD(long address, long length, char[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoD(long address, long length, int[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoD(long address, long length, long[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoD(long address, long length, float[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoD(long address, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private native static byte[][] writeArrayHtoDAsync(long address, long length, byte[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoDAsync(long address, long length, short[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoDAsync(long address, long length, char[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoDAsync(long address, long length, int[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoDAsync(long address, long length, long[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoDAsync(long address, long length, float[] array, long hostOffset, byte[] streamWrapper);
    private native static byte[][] writeArrayHtoDAsync(long address, long length, double[] array, long hostOffset, byte[] streamWrapper);

    private native static byte[][] cuLaunchKernel(byte[] module, String name, int gridDimX, int gridDimY, int gridDimZ, int blockDimX, int blockDimY, int blockDimZ, long sharedMemBytes, byte[] stream, byte[] args);

    private native static byte[] cuCreateStream();
    private native static void cuDestroyStream(byte[] streamWrapper);
    private native static void cuStreamSynchronize(byte[] streamWrapper);

    private native static byte[][] cuEventCreateAndRecord(boolean isProfilingEnabled, byte[] streamWrapper);

    private int recordEvent(EventDescription description) {
        CUDAEvent event = new CUDAEvent(cuEventCreateAndRecord(TornadoOptions.isProfilerEnabled(), streamWrapper), description);
        recordedEvents.put(eventCount, event);
        eventCount++;
        return eventCount - 1;
    }

    private int record(byte[][] eventWrapper, EventDescription description) {
        CUDAEvent event = new CUDAEvent(eventWrapper, description);
        recordedEvents.put(eventCount, event);
        eventCount++;
        return eventCount - 1;
    }


    public void reset() {
        recordedEvents.forEach((key, event) -> event.destroy());
        recordedEvents.clear();
        eventCount = 0;
    }

    public void sync() {
        cuStreamSynchronize(streamWrapper);
    }

    public void cleanup() {
        cuDestroyStream(streamWrapper);
    }

    public Event resolveEvent(int event) {
        if (event == -1) {
            return EMPTY_EVENT;
        }
        return recordedEvents.get(event);
    }

    private void waitForEvents(int[] eventIds) {
        if (eventIds == null) return;
        CUDAEvent[] events = new CUDAEvent[eventIds.length];
        for (int i = 0; i < eventIds.length; i++) {
            events[i] = recordedEvents.get(eventIds[i]);
        }
        CUDAEvent.waitForEventArray(events);
    }

    public int enqueueKernelLaunch(CUDAModule module, byte[] kernelParams, int[] gridDim, int[] blockDim) {
        if (DEBUG) {
            System.out.println("Executing: " + module.kernelFunctionName);
            System.out.println("   Blocks: " + Arrays.toString(blockDim));
            System.out.println("    Grids: " + Arrays.toString(gridDim));
        }

        return record(
                cuLaunchKernel(module.moduleWrapper, module.kernelFunctionName,
                       gridDim[0], gridDim[1], gridDim[2],
                       blockDim[0], blockDim[1], blockDim[2],
                       0, streamWrapper,
                       kernelParams),
                EventDescription.KERNEL
        );
    }

    public int enqueueBarrier() {
        return recordEvent(EventDescription.BARRIER);
    }

    public int enqueueBarrier(int[] events) {
        waitForEvents(events);
        return enqueueBarrier();
    }

    public int enqueueRead(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayDtoH(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_D_TO_H_BYTE
        );
    }

    public int enqueueRead(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayDtoH(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_D_TO_H_SHORT
        );
    }

    public int enqueueRead(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayDtoH(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_D_TO_H_CHAR
        );
    }

    public int enqueueRead(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayDtoH(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_D_TO_H_INT
        );
    }

    public int enqueueRead(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayDtoH(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_D_TO_H_LONG
        );
    }

    public int enqueueRead(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayDtoH(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_D_TO_H_FLOAT
        );
    }

    public int enqueueRead(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayDtoH(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_D_TO_H_DOUBLE
        );
    }


    public int enqueueAsyncRead(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_BYTE);
    }

    public int enqueueAsyncRead(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_SHORT);
    }

    public int enqueueAsyncRead(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_CHAR);
    }

    public int enqueueAsyncRead(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_INT);
    }

    public int enqueueAsyncRead(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_LONG);
    }

    public int enqueueAsyncRead(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_FLOAT);
    }

    public int enqueueAsyncRead(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(writeArrayDtoHAsync(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_DOUBLE);
    }


    public void enqueueWrite(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_BYTE);
    }

    public void enqueueWrite(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_SHORT);
    }

    public void enqueueWrite(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_CHAR);
    }

    public void enqueueWrite(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_INT);
    }

    public void enqueueWrite(long address, long length, long[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_LONG);
    }

    public void enqueueWrite(long address, long length, float[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_FLOAT);
    }

    public void enqueueWrite(long address, long length, double[] array, int hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        record(writeArrayHtoD(address, length, array, hostOffset, streamWrapper), EventDescription.MEMCPY_D_TO_H_DOUBLE);
    }


    public int enqueueAsyncWrite(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_H_TO_D_BYTE
        );
    }

    public int enqueueAsyncWrite(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_H_TO_D_CHAR
        );
    }

    public int enqueueAsyncWrite(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_H_TO_D_SHORT
        );
    }

    public int enqueueAsyncWrite(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_H_TO_D_INT
        );

    }

    public int enqueueAsyncWrite(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_H_TO_D_LONG
        );
    }

    public int enqueueAsyncWrite(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_H_TO_D_FLOAT
        );
    }

    public int enqueueAsyncWrite(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        waitForEvents(waitEvents);
        return record(
                writeArrayHtoDAsync(address, length, array, hostOffset, streamWrapper),
                EventDescription.MEMCPY_H_TO_D_DOUBLE
        );
    }
}
