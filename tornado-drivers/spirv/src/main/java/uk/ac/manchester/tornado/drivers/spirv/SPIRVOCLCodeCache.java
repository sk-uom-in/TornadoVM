/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVTool;
import uk.ac.manchester.beehivespirvtoolkit.lib.disassembler.Disassembler;
import uk.ac.manchester.beehivespirvtoolkit.lib.disassembler.SPIRVDisassemblerOptions;
import uk.ac.manchester.beehivespirvtoolkit.lib.disassembler.SPVFileReader;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.opencl.OCLErrorCode;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVOCLInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.ocl.SPIRVOCLNativeDispatcher;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVOCLCodeCache extends SPIRVCodeCache {

    public SPIRVOCLCodeCache(SPIRVDeviceContext deviceContext) {
        super(deviceContext);
    }

    @Override
    public SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, byte[] binary) {
        if (binary == null || binary.length == 0) {
            throw new RuntimeException("[ERROR] Binary SPIR-V Module is Empty");
        }
        ByteBuffer buffer = ByteBuffer.allocate(binary.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(binary);
        String tempDirectory = System.getProperty("java.io.tmpdir");
        String spirvTempDirectory = STR."\{tempDirectory}/tornadoVM-spirv";
        Path path = Paths.get(spirvTempDirectory);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new TornadoBailoutRuntimeException("Error - Exception when creating the temp directory for SPIR-V");
        }
        long timeStamp = System.nanoTime();
        String file = STR."\{spirvTempDirectory}/\{timeStamp}-\{id}\{entryPoint}.spv";if(TornadoOptions.DEBUG)
    {
            System.out.println(STR."SPIRV-File : \{file}");
        }

    writeBufferToFile(buffer, file);
        return installSPIRVBinary(meta, id, entryPoint, file);
    }

    private byte[] readFile(String spirvFile) {
        File file = new File(spirvFile);
        FileInputStream fileStream;
        try {
            fileStream = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        byte[] spirvBinary = new byte[(int) file.length()];
        try {
            fileStream.read(spirvBinary);
            fileStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return spirvBinary;
    }

    @Override
    public SPIRVInstalledCode installSPIRVBinary(TaskMetaData meta, String id, String entryPoint, String pathToFile) {

        checkBinaryFileExists(pathToFile);

        if (meta.isPrintKernelEnabled()) {
            SPVFileReader reader;
            try {
                reader = new SPVFileReader(pathToFile);
            } catch (FileNotFoundException e) {
                throw new TornadoBailoutRuntimeException(e.getMessage());
            }
            SPIRVDisassemblerOptions disassemblerOptions = new SPIRVDisassemblerOptions(true, true, false, true, false);
            SPIRVTool spirvTool = new Disassembler(reader, System.out, disassemblerOptions);
            try {
                spirvTool.run();
            } catch (Exception e) {
                throw new TornadoBailoutRuntimeException(e.getMessage());
            }
        }

        byte[] binary = readFile(pathToFile);

        long contextId = deviceContext.getSpirvContext().getOpenCLLayer().getContextId();
        long programPointer;

        SPIRVOCLNativeDispatcher spirvoclNativeCompiler = new SPIRVOCLNativeDispatcher();
        int[] errorCode = new int[1];
        programPointer = spirvoclNativeCompiler.clCreateProgramWithIL(contextId, binary, new long[] { binary.length }, errorCode);
        if (errorCode[0] != OCLErrorCode.CL_SUCCESS) {
            throw new TornadoRuntimeException("[ERROR] - clCreateProgramWithIL failed");
        }

        OCLTargetDevice oclDevice = (OCLTargetDevice) deviceContext.getDevice().getDeviceRuntime();
        int status = spirvoclNativeCompiler.clBuildProgram(programPointer, 1, new long[] { oclDevice.getId() }, "");
        if (status != OCLErrorCode.CL_SUCCESS) {
            String log = spirvoclNativeCompiler.clGetProgramBuildInfo(programPointer, oclDevice.getId());
            System.out.println(log);
            throw new TornadoRuntimeException("[ERROR] - clBuildProgram failed");
        }

        long kernelPointer = spirvoclNativeCompiler.clCreateKernel(programPointer, entryPoint, errorCode);
        if (errorCode[0] != OCLErrorCode.CL_SUCCESS) {
            throw new TornadoRuntimeException("[ERROR] - clCreateKernel failed");
        }

        SPIRVOCLModule module = new SPIRVOCLModule(kernelPointer, entryPoint, pathToFile);
        final SPIRVOCLInstalledCode installedCode = new SPIRVOCLInstalledCode(entryPoint, module, deviceContext);
        
        // Install code in the code cache
        cache.put(STR."\{id}-\{entryPoint}", installedCode);
        return installedCode;
    }
}
