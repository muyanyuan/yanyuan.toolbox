package com.yanyuan.toolbox

import com.yanyuan.toolbox.runtime.ToolboxRuntimeInstaller
import com.yanyuan.toolbox.runtime.RuntimeManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeFalse
import org.junit.Test

class RuntimeManifestTest {
    @Test
    fun parsesVersionAbiAndFileHashes() {
        val manifest = RuntimeManifest.parse(
            """
            {
              "version":"bootstrap-test",
              "abi":"arm64-v8a",
              "files":[{"path":"bin/bash","sha256":"abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd","size":12,"mode":448}]
            }
            """.trimIndent()
        )
        assertEquals("bootstrap-test", manifest.version)
        assertEquals("arm64-v8a", manifest.abi)
        assertEquals("bin/bash", manifest.files.single().path)
        assertEquals("abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd", manifest.files.single().sha256)
        assertEquals(12L, manifest.files.single().size)
        assertEquals(448, manifest.files.single().mode)
    }

    @Test
    fun normalizesBootstrapPathsAndRejectsTraversal() {
        assertEquals("bin/bash", ToolboxRuntimeInstaller.normalizeArchivePath("./bin/bash"))
        assertThrows(IllegalArgumentException::class.java) {
            ToolboxRuntimeInstaller.normalizeArchivePath("./../../outside")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToolboxRuntimeInstaller.normalizeArchivePath("/absolute/path")
        }
    }

    @Test
    fun restoresOnlyContainedRelativeSymlinks() {
        assumeFalse(System.getProperty("os.name", "").startsWith("Windows"))
        val root = java.nio.file.Files.createTempDirectory("runtime-prefix").toFile()
        try {
            java.io.File(root, "bin").mkdirs()
            java.io.File(root, "bin/bash").writeText("shell")
            ToolboxRuntimeInstaller.restoreSymlink(root, "bash→bin/sh")
            assertEquals("shell", java.nio.file.Files.readString(java.io.File(root, "bin/sh").toPath()))
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                ToolboxRuntimeInstaller.restoreSymlink(root, "../../outside→bin/escape")
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsManifestWithoutFileList() {
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeManifest.parse("{\"version\":\"v1\",\"abi\":\"x86_64\"}")
        }
    }

    @Test
    fun rejectsInvalidHashAndUnsafeMode() {
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeManifest.parse("""{"version":"v1","abi":"x86_64","files":[{"path":"bin/bash","sha256":"bad","size":1,"mode":448}]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RuntimeManifest.parse("""{"version":"v1","abi":"x86_64","files":[{"path":"bin/bash","sha256":"abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd","size":1,"mode":1024}]}""")
        }
    }
}
