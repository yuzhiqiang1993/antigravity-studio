package com.yuzhiqiang.antigravity.services.auth

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.*

class CrossProcessAccountSwitchLockTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testLockAcquireAndRelease() = runBlocking {
        val lockDir = tempFolder.newFolder("lock_test")
        val lock = CrossProcessAccountSwitchLock(lockDirectory = lockDir)

        assertFalse(lock.lockFile.exists())

        var executed = false
        lock.withCrossProcessLock("test-studio") {
            assertTrue(lock.lockFile.exists())
            executed = true
        }

        assertTrue(executed)
        assertFalse(lock.lockFile.exists())
    }

    @Test
    fun testLockMutualExclusion() = runBlocking {
        val lockDir = tempFolder.newFolder("lock_test_mutex")
        val lock1 = CrossProcessAccountSwitchLock(lockDirectory = lockDir, lockTimeoutMs = 10_000L)
        val lock2 = CrossProcessAccountSwitchLock(lockDirectory = lockDir, lockTimeoutMs = 10_000L)

        val job1 = async {
            lock1.withCrossProcessLock("studio-1") {
                delay(300)
            }
        }

        delay(50) // 确保 job1 先拿到锁

        assertFailsWith<CrossProcessAccountSwitchLock.LockAcquisitionException> {
            lock2.withCrossProcessLock("studio-2") {
                // 不应该执行到这里
            }
        }

        job1.await()
    }
}
