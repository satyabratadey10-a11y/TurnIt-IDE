package com.turnit.ide

class NativeBridge {
    /**
     * Loads the C++ engine we defined in CMakeLists.txt
     */
    init {
        System.loadLibrary("turnit_engine")
    }

    /**
     * A native method that is implemented by the 'turnit_engine' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String
}
