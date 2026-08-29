package com.yuzhiqiang.antigravity.core.file

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AtomicFileWriterTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("atomic-file-writer-test-")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun writesAndAtomicallyReplacesTarget() {
        val target = tempDir.resolve("nested/config.json").toFile()

        AtomicFileWriter.writeText(target, "first").getOrThrow()
        AtomicFileWriter.writeText(target, "second").getOrThrow()

        assertEquals("second", target.readText())
        assertTrue(target.parentFile.list().orEmpty().none { name -> name.endsWith(".tmp") })
    }

    @Test
    fun atomicMoveFailureDoesNotModifyExistingTarget() {
        val target = tempDir.resolve("credentials.json").toFile().apply {
            writeText("original")
        }
        val filesBefore = tempDir.toFile().list().orEmpty().toSet()

        val result = AtomicFileWriter.writeBytes(
            target = target,
            content = "replacement".toByteArray(),
            permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY,
            disallowSymlinks = true,
            moveOperation = AtomicFileWriter.MoveOperation { _, _ ->
                throw AtomicMoveNotSupportedException("temp", "target", "unsupported")
            }
        )

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals("original", target.readText())
        assertEquals(filesBefore, tempDir.toFile().list().orEmpty().toSet())
    }

    @Test
    fun ownerOnlyPolicyIsAppliedBeforeReplacement() {
        if (!supportsPosixPermissions(tempDir)) return
        val target = tempDir.resolve("accounts.json").toFile().apply {
            writeText("original")
        }
        Files.setPosixFilePermissions(
            target.toPath(),
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ
            )
        )

        val ownerOnlyPermissions = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
        )
        var permissionsBeforeMove: Set<PosixFilePermission>? = null
        AtomicFileWriter.writeBytes(
            target = target,
            content = "replacement".toByteArray(),
            permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY,
            disallowSymlinks = true,
            moveOperation = AtomicFileWriter.MoveOperation { source, destination ->
                permissionsBeforeMove = Files.getPosixFilePermissions(source)
                Files.move(
                    source,
                    destination,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        ).getOrThrow()

        assertEquals(ownerOnlyPermissions, permissionsBeforeMove)
        assertEquals(ownerOnlyPermissions, Files.getPosixFilePermissions(target.toPath()))
    }

    @Test
    fun preservePolicyKeepsExistingPosixPermissions() {
        if (!supportsPosixPermissions(tempDir)) return
        val target = tempDir.resolve("host.json").toFile().apply {
            writeText("original")
        }
        val expectedPermissions = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ
        )
        Files.setPosixFilePermissions(target.toPath(), expectedPermissions)

        AtomicFileWriter.writeText(
            target = target,
            content = "replacement",
            permissionPolicy = AtomicFileWriter.PermissionPolicy.PRESERVE_EXISTING
        ).getOrThrow()

        assertEquals(expectedPermissions, Files.getPosixFilePermissions(target.toPath()))
    }

    @Test
    fun successfulMoveDoesNotDeleteFileRecreatedAtTempPath() {
        val target = tempDir.resolve("config.json").toFile().apply {
            writeText("original")
        }
        var recreatedTempPath: Path? = null

        AtomicFileWriter.writeBytes(
            target = target,
            content = "replacement".toByteArray(),
            permissionPolicy = AtomicFileWriter.PermissionPolicy.DEFAULT,
            disallowSymlinks = true,
            moveOperation = AtomicFileWriter.MoveOperation { source, destination ->
                Files.move(
                    source,
                    destination,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
                Files.write(source, "new-file".toByteArray())
                recreatedTempPath = source
            }
        ).getOrThrow()

        assertEquals("replacement", target.readText())
        val recreated = requireNotNull(recreatedTempPath)
        assertTrue(Files.exists(recreated))
        assertEquals("new-file", recreated.toFile().readText())
    }

    @Test
    fun rejectsSymlinkWithoutChangingLinkedFile() {
        val linkedFile = tempDir.resolve("linked.json").toFile().apply {
            writeText("original")
        }
        val symlink = tempDir.resolve("credentials.json")
        val symlinkCreated = runCatching {
            Files.createSymbolicLink(symlink, linkedFile.toPath().fileName)
        }.isSuccess
        if (!symlinkCreated) return

        val result = AtomicFileWriter.writeText(
            target = symlink.toFile(),
            content = "replacement",
            permissionPolicy = AtomicFileWriter.PermissionPolicy.OWNER_ONLY,
            disallowSymlinks = true
        )

        assertTrue(result.isFailure)
        assertTrue(Files.isSymbolicLink(symlink))
        assertEquals("original", linkedFile.readText())
    }

    @Test
    fun ownerOnlyPermissionHelperRejectsSymlinks() {
        val linkedFile = tempDir.resolve("linked-config.json").toFile().apply {
            writeText("config")
        }
        val symlink = tempDir.resolve("config.json")
        val symlinkCreated = runCatching {
            Files.createSymbolicLink(symlink, linkedFile.toPath().fileName)
        }.isSuccess
        if (!symlinkCreated) return

        val result = AtomicFileWriter.setOwnerOnlyPermissions(symlink.toFile())

        assertTrue(result.isFailure)
        assertTrue(Files.isSymbolicLink(symlink))
    }

    private fun supportsPosixPermissions(path: Path): Boolean {
        return Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView::class.java)
    }
}
