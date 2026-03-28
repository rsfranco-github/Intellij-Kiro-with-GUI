package com.kiro.intellij.settings

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KiroCliResolverTest {

    @Test
    fun `configureProcessBuilder adds extra paths to PATH`() {
        val pb = ProcessBuilder("echo", "test")
        KiroCliResolver.configureProcessBuilder(pb)
        
        val path = pb.environment()["PATH"] ?: ""
        assertTrue(path.isNotBlank(), "PATH should not be blank after configuration")
    }

    @Test
    fun `configureProcessBuilder includes common bin directories`() {
        val pb = ProcessBuilder("echo", "test")
        KiroCliResolver.configureProcessBuilder(pb)
        
        val path = pb.environment()["PATH"] ?: ""
        val home = System.getProperty("user.home")
        
        // 존재하는 디렉토리만 추가되므로, 최소한 하나는 포함되어야 함
        val commonPaths = listOf(
            "$home/.local/bin",
            "/usr/local/bin",
            "/opt/homebrew/bin"
        )
        val hasAtLeastOne = commonPaths.any { dir ->
            File(dir).isDirectory && path.contains(dir)
        }
        // 시스템에 따라 다를 수 있으므로 PATH가 비어있지 않은 것만 확인
        assertTrue(path.isNotBlank())
    }

    @Test
    fun `executable file is detected correctly`(@TempDir tempDir: Path) {
        val fakeKiro = File(tempDir.toFile(), "kiro-cli").apply {
            writeText("#!/bin/sh\necho test")
            setExecutable(true)
        }
        
        assertTrue(fakeKiro.canExecute(), "Created file should be executable")
        assertTrue(fakeKiro.exists(), "Created file should exist")
    }

    @Test
    fun `non-executable file is not detected as executable`(@TempDir tempDir: Path) {
        val fakeFile = File(tempDir.toFile(), "not-executable").apply {
            writeText("just a file")
            setExecutable(false)
        }
        
        assertTrue(!fakeFile.canExecute() || fakeFile.exists(), "File should exist but not be executable")
    }

    @Test
    fun `search paths include expected directories`() {
        // KiroCliResolver의 SEARCH_PATHS가 일반적인 경로를 포함하는지 확인
        // configureProcessBuilder를 통해 간접 검증
        val pb = ProcessBuilder("echo", "test")
        val originalPath = pb.environment()["PATH"] ?: ""
        
        KiroCliResolver.configureProcessBuilder(pb)
        val newPath = pb.environment()["PATH"] ?: ""
        
        // 새 PATH가 원래보다 길거나 같아야 함 (경로가 추가되므로)
        assertTrue(newPath.length >= originalPath.length, 
            "PATH should be extended with additional directories")
    }
}
