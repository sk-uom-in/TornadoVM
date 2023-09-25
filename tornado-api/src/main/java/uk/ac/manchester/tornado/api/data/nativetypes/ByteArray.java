package uk.ac.manchester.tornado.api.data.nativetypes;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

public class ByteArray {
    private MemorySegment segment;
    private final int BYTE_BYTES = 1;

    private int numberOfElements;

    public ByteArray(int numberOfElements) {
        this.numberOfElements = numberOfElements;
        segment = Arena.ofAuto().allocate(numberOfElements * BYTE_BYTES, 1);
    }


    public void set(int index, byte value) {
        segment.setAtIndex(JAVA_BYTE, index, value);
    }

    public byte get(int index) {
        return segment.getAtIndex(JAVA_BYTE, index);
    }


    public void init(byte value) {
        for (int i = 0; i < segment.byteSize() / BYTE_BYTES; i++) {
            segment.setAtIndex(JAVA_BYTE, i, value);
        }
    }

    public int getSize() {
        return numberOfElements;
    }

    public MemorySegment getSegment() {
        return segment;
    }

    @Override
    public String toString() {
        String arrayContents = String.valueOf(this.get(0));
        for (int i = 1; i < numberOfElements; i++) {
            arrayContents += ", " + this.get(i);
        }
        return arrayContents;
    }
}
