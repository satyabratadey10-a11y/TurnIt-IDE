#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_turnit_ide_NativeBridge_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "TurnIt PRoot Engine Initialized";
    return env->NewStringUTF(hello.c_str());
}
