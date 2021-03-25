/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger */

#ifndef _Included_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger
#define _Included_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger
 * Method:    memset_native
 * Signature: (Luk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroBufferInteger;II)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger_memset_1native
        (JNIEnv *, jobject, jobject, jint, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger
 * Method:    isEqual
 * Signature: (JJI)Z
 */
JNIEXPORT jboolean JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger_isEqual
        (JNIEnv *, jobject, jlong, jlong, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer
 * Method:    memset_native
 * Signature: (Luk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;BI)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer_memset_1native
        (JNIEnv *, jobject, jobject, jbyte, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger
 * Method:    isEqual
 * Signature: (JJI)Z
 */
JNIEXPORT jboolean JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer_isEqual
        (JNIEnv *, jobject, jlong, jlong, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer
 * Method:    copy_native
 * Signature: (J[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer_copy_1native
        (JNIEnv *, jobject, jlong, jbyteArray);
#ifdef __cplusplus

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer
 * Method:    getByteBuffer_native
 * Signature: (JI)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer_getByteBuffer_1native
        (JNIEnv *, jobject, jlong, jint);

// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Buffer Long
// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong
 * Method:    memset_native
 * Signature: (Luk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroBufferLong;JI)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong_memset_1native
        (JNIEnv *, jobject, jobject, jlong, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong
 * Method:    isEqual
 * Signature: (JJI)Z
 */
JNIEXPORT jboolean JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong_isEqual
        (JNIEnv *, jobject, jlong, jlong, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong
 * Method:    copy_native
 * Signature: (J[J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong_copy_1native
        (JNIEnv *, jobject, jlong, jlongArray);

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong
 * Method:    getLongBuffer_native
 * Signature: (JI)[J
 */
JNIEXPORT jlongArray JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong_getLongBuffer_1native
        (JNIEnv *, jobject, jlong, jint);

}
#endif
#endif
