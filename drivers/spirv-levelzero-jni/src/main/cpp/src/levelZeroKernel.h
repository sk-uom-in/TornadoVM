/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel */

#ifndef _Included_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
#define _Included_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeKernelSuggestGroupSize_native
 * Signature: (JIII[I[I[I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeKernelSuggestGroupSize_1native
        (JNIEnv *, jobject, jlong, jint, jint, jint, jintArray, jintArray, jintArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeKernelSetGroupSize_native
 * Signature: (JIII)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeKernelSetGroupSize_1native
        (JNIEnv *, jobject, jlong, jint, jint, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeKernelSetArgumentValue_native
 * Signature: (JIILuk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroBufferInteger;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeKernelSetArgumentValue_1native
        (JNIEnv *, jobject, jlong, jint, jint, jobject);

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeKernelSetArgumentValue_nativeByteArg
 * Signature: (JII[B)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeKernelSetArgumentValue_1nativeByteArg
        (JNIEnv *, jobject, jlong, jint, jint, jbyteArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel
 * Method:    zeKernelSetArgumentValue_nativePtrArg
 * Signature: (JIIJ)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroKernel_zeKernelSetArgumentValue_1nativePtrArg
        (JNIEnv *, jobject, jlong, jint, jint, jlong);

#ifdef __cplusplus
}
#endif
#endif
