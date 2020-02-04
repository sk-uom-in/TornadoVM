/*
 * Copyright (c) 2013-2020, APT Group, School of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.examples.polyglot;

import java.util.Arrays;

import org.graalvm.polyglot.Context;

/**
 * Example of GraalVM Polyglot using R and Tornado.
 * 
 * The R program calls MyCompute.compute() to accelerate vector addition on a
 * GPU/FPGA.
 * 
 * How to run:
 * 
 * <code>
 *     $ tornado --debug -m tornado.examples/uk.ac.manchester.tornado.examples.polyglot.HelloR
 * </code>
 * 
 */
public class HelloR {

    public static void runTornadoFromR() {
        try (Context context = Context.newBuilder().allowAllAccess(true).build()) {
            // @formatter:off
            float[] v = context.eval("R",
                    "myclass = java.type('uk.ac.manchester.tornado.examples.polyglot.MyCompute');\n" +
                            "output = myclass$compute();\n" +
                            "print (output$toString());\n" + "output")
                    .asHostObject();
            // @formatter:on
            System.out.println(Arrays.toString(v));
        }
    }

    public static void main(String[] args) {
        System.out.println("Hello polyglot world Java!");
        Context context = Context.newBuilder()
                .allowAllAccess(true)
                .build();
        context.eval("R", "print('Hello polyglot world from R!');");
        context.close();
        runTornadoFromR();
    }

}
