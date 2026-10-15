package ios.devicectl

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import util.CommandLineUtils

class DeviceControlIOSDeviceTest {

    @Test
    fun `both public JVM constructors remain available`() {
        DeviceControlIOSDevice::class.java.getConstructor(String::class.java)
        DeviceControlIOSDevice::class.java.getConstructor(String::class.java, String::class.java)
    }

    @Test
    fun `platform termination failure is propagated`() {
        mockkObject(CommandLineUtils)
        try {
            every { CommandLineUtils.runCommand(any(), any(), any(), any()) } answers {
                val command = firstArg<List<String>>()
                File(command[command.indexOf("--json-output") + 1])
                    .writeText("""{"result":{"process":{"processIdentifier":4242}}}""")
                mockk<Process>()
            }
            val device = DeviceControlIOSDevice("UDID-1")
            device.launch("com.example.app", emptyMap())
            val failure = IllegalStateException("device disconnected")
            every { CommandLineUtils.runCommand(any(), any(), any(), any()) } throws failure

            assertSame(failure, assertThrows(IllegalStateException::class.java) { device.stop("com.example.app") })
        } finally {
            unmockkObject(CommandLineUtils)
        }
    }

    @Test
    fun `launch command without arguments has no terminator`() {
        assertEquals(
            listOf(
                "xcrun", "devicectl", "device", "process", "launch",
                "--json-output", "/tmp/out.json",
                "--device", "UDID-1",
                "com.example.app",
            ),
            DeviceControlIOSDevice.buildLaunchCommand("UDID-1", "com.example.app", emptyMap(), "/tmp/out.json"),
        )
    }

    @Test
    fun `launch command passes app arguments after -- terminator in iOS form`() {
        assertEquals(
            listOf(
                "xcrun", "devicectl", "device", "process", "launch",
                "--json-output", "/tmp/out.json",
                "--device", "UDID-1",
                "--",
                "com.example.app",
                "-foo", "bar",
                "isE2E", "true",
            ),
            DeviceControlIOSDevice.buildLaunchCommand(
                "UDID-1",
                "com.example.app",
                linkedMapOf("foo" to "bar", "isE2E" to true),
                "/tmp/out.json",
            ),
        )
    }

    @Test
    fun `terminate command targets pid`() {
        assertEquals(
            listOf(
                "xcrun", "devicectl", "device", "process", "terminate",
                "--device", "UDID-1",
                "--pid", "4242",
            ),
            DeviceControlIOSDevice.buildTerminateCommand("UDID-1", 4242L),
        )
    }

    @Test
    fun `pid is parsed from devicectl launch json output`() {
        val json = """
            {"info":{"outcome":"success"},"result":{"process":{"processIdentifier":1234,"executable":"file:///private/var/containers/x/App.app/App"}}}
        """.trimIndent()
        assertEquals(1234L, DeviceControlIOSDevice.parseLaunchedProcessId(json))
    }

    @Test
    fun `pid parse returns null on malformed or missing output`() {
        assertNull(DeviceControlIOSDevice.parseLaunchedProcessId("not json"))
        assertNull(DeviceControlIOSDevice.parseLaunchedProcessId("""{"result":{}}"""))
    }

    @Test
    fun `clearAppState and addMedia fail loudly instead of silently no-oping`() {
        val device = DeviceControlIOSDevice("UDID-1")
        assertThrows(UnsupportedOperationException::class.java) { device.clearAppState("com.example.app") }
        assertThrows(UnsupportedOperationException::class.java) { device.addMedia("/tmp/photo.png") }
    }
}
