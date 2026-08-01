package com.example

import org.lsposed.hiddenapibypass.HiddenApiBypass

object TestAudio {
    fun test() {
        HiddenApiBypass.addHiddenApiExemptions("")
        val audioSystemClass = Class.forName("android.media.AudioSystem")
        val method = audioSystemClass.getMethod("setForceUse", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        println("Method: " + method)
    }
}
