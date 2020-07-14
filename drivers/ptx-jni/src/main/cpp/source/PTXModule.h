/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
#include <cuda.h>
/* Header for class uk_ac_manchester_tornado_drivers_ptx_PTXModule */

#ifndef _Included_uk_ac_manchester_tornado_drivers_ptx_PTXModule
#define _Included_uk_ac_manchester_tornado_drivers_ptx_PTXModule

void array_to_module(JNIEnv *env, CUmodule *module_ptr, jbyteArray javaWrapper);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuModuleLoadData
 * Signature: ([B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuModuleLoadData
  (JNIEnv *, jclass, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXModule
 * Method:    cuOccupancyMaxPotentialBlockSize
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXModule_cuOccupancyMaxPotentialBlockSize
  (JNIEnv *, jclass, jbyteArray, jstring);

#endif