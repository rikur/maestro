import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import util.DeviceCtlProcess
import util.LocalIOSDevice

class DeviceCtlResponseTest {


    @Test
    fun `test if deserializing device list works`() {
        // given
        val deviceCtlOutput = getDeviceCtlOutput()
        val deviceCtlProcess = mockk<DeviceCtlProcess>()
        every { deviceCtlProcess.devicectlDevicesJson() } returns deviceCtlOutput

        // when
        val connectedDevices = LocalIOSDevice(deviceCtlProcess).listDeviceViaDeviceCtl()

        // then
        assertThat(connectedDevices).isNotEmpty()
    }

    @Test
    fun `legacy connected schema without transport is parsed but not reachable`() {
        val deviceCtlProcess = mockk<DeviceCtlProcess>()
        every { deviceCtlProcess.devicectlDevicesJson() } returns deviceResponse(
            connectionProperties = """
                "pairingState": "paired",
                "tunnelState": "connected"
            """.trimIndent(),
        )

        val device = LocalIOSDevice(deviceCtlProcess).listDeviceViaDeviceCtl().single()

        assertThat(device.connectionProperties.transportType).isNull()
        assertThat(device.connectionProperties.isReachable).isFalse()
        val error = assertThrows<IllegalArgumentException> {
            LocalIOSDevice(deviceCtlProcess).listDeviceViaDeviceCtl("UDID-1")
        }
        assertThat(error).hasMessageThat().contains("connected over USB")
    }

    @Test
    fun `modern connected wired schema is parsed as reachable`() {
        val deviceCtlProcess = mockk<DeviceCtlProcess>()
        every { deviceCtlProcess.devicectlDevicesJson() } returns deviceResponse(
            connectionProperties = """
                "pairingState": "paired",
                "transportType": "wired",
                "tunnelState": "connected"
            """.trimIndent(),
        )

        val device = LocalIOSDevice(deviceCtlProcess).listDeviceViaDeviceCtl("UDID-1")

        assertThat(device.identifier).isEqualTo("COREDEVICE-1")
        assertThat(device.connectionProperties.isReachable).isTrue()
        verify(exactly = 1) { deviceCtlProcess.devicectlDevicesJson() }
    }

    private fun deviceResponse(connectionProperties: String): String =
        """
            {
              "result": {
                "devices": [
                  {
                    "identifier": "COREDEVICE-1",
                    "connectionProperties": {
                      $connectionProperties
                    },
                    "deviceProperties": {
                      "name": "iPhone",
                      "osVersionNumber": "18.4"
                    },
                    "hardwareProperties": {
                      "udid": "UDID-1",
                      "platform": "iOS",
                      "deviceType": "iPhone",
                      "reality": "physical"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

    private fun getDeviceCtlOutput(): String = deviceResponse(
        connectionProperties = """
            "pairingState": "paired",
            "transportType": "wired",
            "tunnelState": "connected"
        """.trimIndent(),
    )
}
