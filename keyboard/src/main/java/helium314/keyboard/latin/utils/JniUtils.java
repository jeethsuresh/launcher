/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Build;
import android.text.TextUtils;

import helium314.keyboard.latin.App;
import helium314.keyboard.latin.BuildConfig;
import helium314.keyboard.latin.settings.Settings;

import java.io.File;
import java.io.FileInputStream;

public final class JniUtils {
    private static final String TAG = JniUtils.class.getSimpleName();
    public static final String JNI_LIB_NAME = "jni_latinime";
    public static final String JNI_LIB_NAME_GOOGLE = "jni_latinimegoogle";
    public static final String JNI_LIB_IMPORT_FILE_NAME = "libjni_latinime.so";
    private static final String CHECKSUM_ARM64 = "b1049983e6ac5cfc6d1c66e38959751044fad213dff0637a6cf1d2a2703e754f";
    private static final String CHECKSUM_ARM32 = "442a2a8bfcb25489564bc9433a916fa4dc0dba9000fe6f6f03f5939b985091e6";
    private static final String CHECKSUM_X86_64 = "c882e12e6d48dd946e0b644c66868a720bd11ac3fecf152000e21a3d5abd59c9";
    private static final String CHECKSUM_X86 = "bd946d126c957b5a6dea3bafa07fa36a27950b30e2b684dffc60746d0a1c7ad8";

    public static String expectedDefaultChecksum() {
        final String abi = Build.SUPPORTED_ABIS[0];
        return switch (abi) {
            case "arm64-v8a" -> CHECKSUM_ARM64;
            case "armeabi-v7a" -> CHECKSUM_ARM32;
            case "x86_64" -> CHECKSUM_X86_64;
            case "x86" -> CHECKSUM_X86;
            default -> "-"; // invalid checksum that definitely will not match
        };
    }

    public static boolean sHaveGestureLib = false;
    static {
        // Always load the bundled open-source native typing engine. Gestures are
        // decoded by StillGestureDecoder, with no imported proprietary library.
        System.loadLibrary(JNI_LIB_NAME);
        sHaveGestureLib = true;
    }

    private JniUtils() {
        // This utility class is not publicly instantiable.
    }

    public static void loadNativeLibrary() {
        // Ensures the static initializer is called
    }
}
