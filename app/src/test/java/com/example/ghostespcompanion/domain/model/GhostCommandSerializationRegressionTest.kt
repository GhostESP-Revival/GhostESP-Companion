package com.example.ghostespcompanion.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compatibility and regression tests asserting the serial command string for
 * every GhostCommand variant against the firmware CLI syntax in
 * GhostCommand.kt on the current baseline.
 *
 * Any drift in the serialized command must be a deliberate change detected
 * here first so firmware compatibility does not silently regress.
 */
class GhostCommandSerializationRegressionTest {

    @Test
    fun `core commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.Help to "help",
            GhostCommand.ChipInfo to "chipinfo",
            GhostCommand.Stop to "stop",
            GhostCommand.Reboot to "reboot",
            GhostCommand.Identify to "identify"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `wifi scan commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.ScanAp() to "scanap",
            GhostCommand.ScanAp(duration = 5) to "scanap 5",
            GhostCommand.ScanAp(live = true) to "scanap -live",
            GhostCommand.ScanAp(stop = true) to "scanap -stop",
            GhostCommand.ScanSta to "scansta",
            GhostCommand.ScanAll() to "scanall",
            GhostCommand.ScanAll(duration = 10) to "scanall 10",
            GhostCommand.StopScan to "stopscan"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `list and select commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.ListResults(GhostCommand.ListMode.ACCESSPoints) to "list -a",
            GhostCommand.ListResults(GhostCommand.ListMode.STATIONS) to "list -s",
            GhostCommand.ListResults(GhostCommand.ListMode.AIR_TAGS) to "list -airtags",
            GhostCommand.Select(GhostCommand.SelectTarget.ACCESS_POINT, "0") to "select -a 0",
            GhostCommand.Select(GhostCommand.SelectTarget.STATION, "0,1") to "select -s 0,1",
            GhostCommand.Select(GhostCommand.SelectTarget.AIR_TAG, "2") to "select -airtag 2",
            GhostCommand.Select(GhostCommand.SelectTarget.FLIPPER, "3") to "selectflipper 3",
            GhostCommand.Select(GhostCommand.SelectTarget.GATT, "4") to "selectgatt 4",
            GhostCommand.SelectAirTag("7") to "selectairtag 7"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `connect and wifi status commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.Connect("HomeNetwork") to "connect \"HomeNetwork\"",
            GhostCommand.Connect("HomeNetwork", "secret") to "connect \"HomeNetwork\" \"secret\"",
            GhostCommand.Disconnect to "disconnect",
            GhostCommand.WifiStatus to "wifistatus",
            GhostCommand.TrackAp to "trackap",
            GhostCommand.TrackSta to "tracksta"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `wifi attack commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.AttackDeauth() to "attack -d",
            GhostCommand.AttackEapol() to "attack -e",
            GhostCommand.AttackSae("winter") to "attack -s winter",
            GhostCommand.SaeFlood("winter") to "saeflood winter",
            GhostCommand.StopSaeFlood to "stopsaeflood",
            GhostCommand.StopDeauth to "stopdeauth",
            GhostCommand.StopSpam to "stopspam"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `beacon spam commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.BeaconSpam(GhostCommand.BeaconSpamMode.RANDOM) to "beaconspam -r",
            GhostCommand.BeaconSpam(GhostCommand.BeaconSpamMode.RICKROLL) to "beaconspam -rr",
            GhostCommand.BeaconSpam(GhostCommand.BeaconSpamMode.AP_LIST) to "beaconspam -l",
            GhostCommand.BeaconSpam(GhostCommand.BeaconSpamMode.CUSTOM("Pineapple")) to "beaconspam Pineapple",
            GhostCommand.BeaconAdd("FreeWiFi") to "beaconadd FreeWiFi",
            GhostCommand.BeaconRemove("FreeWiFi") to "beaconremove FreeWiFi",
            GhostCommand.BeaconClear to "beaconclear",
            GhostCommand.BeaconShow to "beaconshow",
            GhostCommand.KarmaStart() to "karma start",
            GhostCommand.KarmaStart(listOf("A", "B")) to "karma start A B",
            GhostCommand.KarmaStop to "karma stop"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `portal commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.StartPortal(ssid = "Evil") to "startportal default \"Evil\"",
            GhostCommand.StartPortal(ssid = "Evil", password = "leet") to
                "startportal default \"Evil\" \"leet\"",
            GhostCommand.StartPortal(path = "/custom", ssid = "Evil") to "startportal /custom \"Evil\"",
            GhostCommand.StopPortal to "stopportal",
            GhostCommand.ListPortals to "listportals",
            GhostCommand.EvilPortal(GhostCommand.PortalCommand.SET_HTML) to
                "evilportal -c sethtmlstr",
            GhostCommand.EvilPortal(GhostCommand.PortalCommand.CLEAR) to "evilportal -c clear"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `ble scan commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.BleScan(GhostCommand.BleScanMode.FLIPPER) to "blescan -f",
            GhostCommand.BleScan(GhostCommand.BleScanMode.SPAM_DETECTOR) to "blescan -ds",
            GhostCommand.BleScan(GhostCommand.BleScanMode.AIR_TAG) to "blescan -a",
            GhostCommand.BleScan(GhostCommand.BleScanMode.RAW) to "blescan -r",
            GhostCommand.BleScan(GhostCommand.BleScanMode.GATT) to "blescan -g",
            GhostCommand.BleScan(GhostCommand.BleScanMode.FLIPPER, stop = true) to "blescan -s",
            GhostCommand.BleScanStop to "blescan -s",
            GhostCommand.BleAdvertiserScan() to "blescan -adv",
            GhostCommand.BleAdvertiserScan(GhostCommand.BleAdvertiserFilter.Oui("00:1B")) to "blescan -oui 00:1B",
            GhostCommand.BleAdvertiserScan(GhostCommand.BleAdvertiserFilter.Vendor("Apple")) to "blescan -vendor Apple",
            GhostCommand.BleAdvertiserScan(GhostCommand.BleAdvertiserFilter.Vendor("Nordic Semiconductor")) to
                "blescan -vendor \"Nordic Semiconductor\"",
            GhostCommand.ListAdvertisers to "listadv"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `ble spam and tracking commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.BleSpam(mode = null) to "blespam",
            GhostCommand.BleSpam(GhostCommand.BleSpamMode.APPLE) to "blespam -apple",
            GhostCommand.BleSpam(GhostCommand.BleSpamMode.MICROSOFT) to "blespam -ms",
            GhostCommand.BleSpam(GhostCommand.BleSpamMode.SAMSUNG) to "blespam -samsung",
            GhostCommand.BleSpam(GhostCommand.BleSpamMode.GOOGLE) to "blespam -google",
            GhostCommand.BleSpam(GhostCommand.BleSpamMode.RANDOM) to "blespam -random",
            GhostCommand.BleSpam(GhostCommand.BleSpamMode.STOP) to "blespam -s",
            GhostCommand.ListFlippers to "listflippers",
            GhostCommand.ListAirTags to "listairtags",
            GhostCommand.ListGatt to "listgatt",
            GhostCommand.EnumGatt to "enumgatt",
            GhostCommand.TrackGatt to "trackgatt",
            GhostCommand.TrackFlipper(3) to "selectflipper 3",
            GhostCommand.SpoofAirTag(start = true) to "spoofairtag",
            GhostCommand.SpoofAirTag(start = false) to "stopspoof",
            GhostCommand.BleWardrive() to "blewardriving",
            GhostCommand.BleWardrive(stop = true) to "blewardriving -s"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `chameleon subcommands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.Connect()) to "chameleon connect 30",
            GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.Connect(timeout = 60, pin = 1234)) to
                "chameleon connect 60 1234",
            GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.Scan()) to "chameleon scan 60",
            GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.Scan(timeout = 15)) to "chameleon scan 15",
            GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.ScanStop) to "chameleon scan stop",
            GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.Read()) to "chameleon read",
            GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.Read(slot = 1)) to "chameleon read 1",
            GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.Write(slot = 2)) to "chameleon write 2",
            GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.List) to "chameleon list",
            GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.Delete(slot = 3)) to "chameleon delete 3"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `ir subcommands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.Ir(GhostCommand.IrSubcommand.List()) to "ir list",
            GhostCommand.Ir(GhostCommand.IrSubcommand.List("Samsung.ir")) to "ir list Samsung.ir",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Send("Samsung.ir")) to "ir send Samsung.ir",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Send("Samsung.ir", 1)) to "ir send Samsung.ir 1",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Learn()) to "ir learn",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Learn("/ir/foo")) to "ir learn /ir/foo",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Rx()) to "ir rx 60",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Rx(timeout = 10)) to "ir rx 10",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Dazzler()) to "ir dazzler",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Dazzler(stop = true)) to "ir dazzler stop",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Universals(GhostCommand.IrSubcommand.UniversalsSubcommand.List)) to
                "ir universals list",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Universals(GhostCommand.IrSubcommand.UniversalsSubcommand.ListAll)) to
                "ir universals list -all",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Universals(GhostCommand.IrSubcommand.UniversalsSubcommand.Send(2))) to
                "ir universals send 2",
            GhostCommand.Ir(
                GhostCommand.IrSubcommand.Universals(
                    GhostCommand.IrSubcommand.UniversalsSubcommand.SendAll("file.ir", "Power")
                )
            ) to "ir universals sendall file.ir Power",
            GhostCommand.Ir(
                GhostCommand.IrSubcommand.Universals(
                    GhostCommand.IrSubcommand.UniversalsSubcommand.SendAll("file.ir", "Power", 250)
                )
            ) to "ir universals sendall file.ir Power 250",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Show("Samsung.ir")) to "ir show Samsung.ir",
            GhostCommand.Ir(GhostCommand.IrSubcommand.Inline) to "ir inline"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `badusb commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.BadUsbList to "badusb list",
            GhostCommand.BadUsbRun("payload.txt") to "badusb run payload.txt",
            GhostCommand.BadUsbStop to "badusb stop",
            GhostCommand.BadUsbKeyboardStart to "badusb keyboard_start",
            GhostCommand.BadUsbKeyboardStop to "badusb keyboard_stop",
            GhostCommand.BadUsbType("hello") to "badusb type hello",
            GhostCommand.BadUsbJiggleStart to "badusb jiggle_start",
            GhostCommand.BadUsbJiggleStop to "badusb jiggle_stop",
            GhostCommand.BadUsbConfig(GhostCommand.BadUsbSetting.VendorId("0x1209")) to "badusb set_vid 0x1209",
            GhostCommand.BadUsbConfig(GhostCommand.BadUsbSetting.ProductId("0x8000")) to "badusb set_pid 0x8000",
            GhostCommand.BadUsbConfig(GhostCommand.BadUsbSetting.Manufacturer("Acme")) to "badusb set_mfr \"Acme\"",
            GhostCommand.BadUsbConfig(GhostCommand.BadUsbSetting.Product("Keys")) to "badusb set_prod \"Keys\"",
            GhostCommand.BadUsbConfig(GhostCommand.BadUsbSetting.Randomize(true)) to "badusb set_rand 1",
            GhostCommand.BadUsbConfig(GhostCommand.BadUsbSetting.Randomize(false)) to "badusb set_rand 0",
            GhostCommand.BadUsbConfig(GhostCommand.BadUsbSetting.Layout(2)) to "badusb set_layout 2",
            GhostCommand.BadUsbKey(1, 6) to "badusb keysend 1 6",
            GhostCommand.BadUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Start) to "badusb trackpad_start",
            GhostCommand.BadUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Stop) to "badusb trackpad_stop",
            GhostCommand.BadUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Move(-4, 7)) to "badusb trackpad_move -4 7",
            GhostCommand.BadUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Button(1)) to "badusb trackpad_button 1",
            GhostCommand.BadUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Wheel(-3)) to "badusb trackpad_wheel -3",
            GhostCommand.BadUsbExec(128) to "badusb exec 128",
            GhostCommand.BadUsbStatus("running") to "badusb status running"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `gps and wardriving commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.GpsInfo() to "gpsinfo",
            GhostCommand.GpsInfo(stop = true) to "gpsinfo -s",
            GhostCommand.StartWardrive() to "startwd",
            GhostCommand.StartWardrive(stop = true) to "startwd -s",
            GhostCommand.StartWardrive(helper = true) to "startwd --helper",
            GhostCommand.StartWardrive(channels = "1,6,11") to "startwd --channels 1,6,11",
            GhostCommand.StartWardrive(hopMs = 250) to "startwd --hop 250",
            GhostCommand.StartWardrive(weighted = true) to "startwd --weighted",
            GhostCommand.WdStream() to "wdstream start -wifi -i 2000 -ch auto",
            GhostCommand.WdStream(includeBle = true) to "wdstream start -wifi -ble -i 2000 -ch auto",
            GhostCommand.WdStream(stop = true) to "wdstream stop",
            GhostCommand.WdStream(status = true) to "wdstream status",
            GhostCommand.GpsPin(4321) to "gpspin 4321"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `sd card commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.SdStatus to "sd status",
            GhostCommand.SdList() to "sd list",
            GhostCommand.SdList("/ghost") to "sd list /ghost",
            GhostCommand.SdRead("/file.bin") to "sd read /file.bin",
            GhostCommand.SdRead("/file.bin", offset = 10) to "sd read /file.bin 10",
            GhostCommand.SdRead("/file.bin", offset = 10, length = 20) to "sd read /file.bin 10 20",
            GhostCommand.SdRead("/file.bin", base64 = true) to "sd read /file.bin --base64",
            GhostCommand.SdRead("/file.bin", offset = 5, length = 10, base64 = true) to
                "sd read /file.bin 5 10 --base64",
            GhostCommand.SdInfo("/file.bin") to "sd info /file.bin",
            GhostCommand.SdSize("/file.bin") to "sd size /file.bin",
            GhostCommand.SdWrite("/file.bin", "ZGF0YQ==") to "sd write /file.bin ZGF0YQ==",
            GhostCommand.SdAppend("/file.bin", "ZGF0YQ==") to "sd append /file.bin ZGF0YQ==",
            GhostCommand.SdMkdir("/new") to "sd mkdir /new",
            GhostCommand.SdRm("/file.bin") to "sd rm /file.bin",
            GhostCommand.SdTree() to "sd tree",
            GhostCommand.SdTree("/ghost") to "sd tree /ghost",
            GhostCommand.SdTree("/ghost", 3) to "sd tree /ghost 3",
            GhostCommand.SdConfig to "sd_config",
            GhostCommand.SdPinsSpi(5, 18, 19, 23) to "sd_pins_spi 5 18 19 23",
            GhostCommand.SdPinsMmc(14, 15, 2, 4, 12, 13) to "sd_pins_mmc 14 15 2 4 12 13",
            GhostCommand.SdSaveConfig to "sd_save_config"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `settings commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.SettingsList to "settings list",
            GhostCommand.SettingsGet("rgb_mode") to "settings get rgb_mode",
            GhostCommand.SettingsSet("rgb_mode", "rainbow") to "settings set rgb_mode rainbow"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `capture commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.Capture(GhostCommand.CaptureMode.DEAUTH) to "capture -deauth",
            GhostCommand.Capture(GhostCommand.CaptureMode.BEACON) to "capture -beacon",
            GhostCommand.Capture(GhostCommand.CaptureMode.RAW) to "capture -raw",
            GhostCommand.Capture(GhostCommand.CaptureMode.IEEE802154) to "capture -802154",
            GhostCommand.Capture(GhostCommand.CaptureMode.EAPOL) to "capture -eapol",
            GhostCommand.Capture(GhostCommand.CaptureMode.PWN) to "capture -pwn",
            GhostCommand.Capture(GhostCommand.CaptureMode.WPS) to "capture -wps",
            GhostCommand.Capture(GhostCommand.CaptureMode.BLE) to "capture -ble",
            GhostCommand.Capture(GhostCommand.CaptureMode.SKIMMER) to "capture -skimmer",
            GhostCommand.Capture(GhostCommand.CaptureMode.PROBE, channel = 6) to "capture -probe -channel 6",
            GhostCommand.CaptureStop to "capture -stop",
            GhostCommand.CaptureList to "capture -list",
            GhostCommand.CaptureExport("/mnt/pcap/a.pcap") to "capture -export /mnt/pcap/a.pcap",
            GhostCommand.CaptureWireshark() to "capture -wireshark",
            GhostCommand.CaptureWireshark(6) to "capture -wireshark -channel 6",
            GhostCommand.CaptureWiresharkBle to "capture -wiresharkble"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `aerial commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.AerialScan() to "aerialscan 30",
            GhostCommand.AerialScan(duration = 60) to "aerialscan 60",
            GhostCommand.AerialScan(stop = true) to "aerialstop",
            GhostCommand.AerialList to "aeriallist",
            GhostCommand.AerialTrack("3") to "aerialtrack 3",
            GhostCommand.AerialTrack("AA:BB:CC:DD:EE:FF") to "aerialtrack AA:BB:CC:DD:EE:FF",
            GhostCommand.AerialSpoof() to "aerialspoof GHOST-TEST 37.7749 -122.4194 100.0",
            GhostCommand.AerialSpoof(deviceId = "FOO", lat = 1.5, lon = 2.5, alt = 50.0f) to
                "aerialspoof FOO 1.5 2.5 50.0",
            GhostCommand.AerialSpoofStop to "aerialspoofstop"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `ethernet commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.EthUp to "ethup",
            GhostCommand.EthDown to "ethdown",
            GhostCommand.EthInfo to "ethinfo",
            GhostCommand.EthFingerprint("192.168.1.1") to "ethfp 192.168.1.1",
            GhostCommand.EthArp to "etharp",
            GhostCommand.EthPorts("192.168.1.1") to "ethports 192.168.1.1",
            GhostCommand.EthPorts("192.168.1.1", 80, 443) to "ethports 192.168.1.1 80-443",
            GhostCommand.EthStats to "ethstats",
            GhostCommand.EthPoison(GhostCommand.EthPoisonAction.START) to "ethpoison start",
            GhostCommand.EthPoison(GhostCommand.EthPoisonAction.STOP) to "ethpoison stop",
            GhostCommand.EthPoison(GhostCommand.EthPoisonAction.LIST) to "ethpoison list",
            GhostCommand.EthPoison(GhostCommand.EthPoisonAction.COOKIES) to "ethpoison cookies",
            GhostCommand.EthPoison(GhostCommand.EthPoisonAction.CREDS) to "ethpoison creds",
            GhostCommand.EthPoison(GhostCommand.EthPoisonAction.STATUS) to "ethpoison status",
            GhostCommand.EthDns("example.com") to "ethdns example.com",
            GhostCommand.EthDns("10.0.0.1", reverse = true) to "ethdns reverse 10.0.0.1",
            GhostCommand.EthTrace("example.com") to "ethtrace example.com",
            GhostCommand.EthTrace("example.com", 8) to "ethtrace example.com 8",
            GhostCommand.EthPing to "ethping",
            GhostCommand.EthConfig(GhostCommand.EthConfigMode.DHCP) to "ethconfig dhcp",
            GhostCommand.EthConfig(GhostCommand.EthConfigMode.SHOW) to "ethconfig show",
            GhostCommand.EthConfig(
                GhostCommand.EthConfigMode.STATIC,
                "192.168.1.10",
                "255.255.255.0",
                "192.168.1.1"
            ) to "ethconfig static 192.168.1.10 255.255.255.0 192.168.1.1",
            GhostCommand.EthMac() to "ethmac",
            GhostCommand.EthMac("DE:AD:BE:EF:00:01") to "ethmac set DE:AD:BE:EF:00:01",
            GhostCommand.EthServ() to "ethserv",
            GhostCommand.EthServ("192.168.1.5") to "ethserv 192.168.1.5",
            GhostCommand.EthNtp() to "ethntp",
            GhostCommand.EthNtp("pool.ntp.org") to "ethntp pool.ntp.org",
            GhostCommand.EthHttp("http://example.com") to "ethhttp http://example.com",
            GhostCommand.EthHttp("http://example.com", lines = 10) to "ethhttp http://example.com 10",
            GhostCommand.EthHttp("http://example.com", showAll = true) to "ethhttp http://example.com all"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `misc commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.DhcpStarve() to "dhcpstarve start",
            GhostCommand.DhcpStarve(threads = 8) to "dhcpstarve start 8",
            GhostCommand.DhcpStarve(stop = true) to "dhcpstarve stop",
            GhostCommand.DhcpStarve(display = true) to "dhcpstarve display",
            GhostCommand.SaeFloodHelp to "saefloodhelp",
            GhostCommand.BeaconSpamList to "beaconspamlist",
            GhostCommand.ApCred() to "apcred",
            GhostCommand.ApCred(ssid = "MyAP") to "apcred \"MyAP\"",
            GhostCommand.ApCred(ssid = "MyAP", password = "secret") to "apcred \"MyAP\" \"secret\"",
            GhostCommand.ApCred(reset = true) to "apcred -r",
            GhostCommand.PineAp to "pineap",
            GhostCommand.ApEnable(true) to "apenable on",
            GhostCommand.ApEnable(false) to "apenable off"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `rgb commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.RgbMode(GhostCommand.RgbModeType.NORMAL) to "setrgbmode normal",
            GhostCommand.RgbMode(GhostCommand.RgbModeType.STEALTH) to "setrgbmode stealth",
            GhostCommand.RgbMode(GhostCommand.RgbModeType.RAINBOW) to "rgbmode rainbow",
            GhostCommand.RgbMode(GhostCommand.RgbModeType.POLICE) to "rgbmode police",
            GhostCommand.RgbMode(GhostCommand.RgbModeType.STROBE) to "rgbmode strobe",
            GhostCommand.RgbMode(GhostCommand.RgbModeType.KNIGHT) to "rgbmode knight",
            GhostCommand.RgbMode(GhostCommand.RgbModeType.OFF) to "rgbmode off",
            GhostCommand.RgbColor(GhostCommand.RgbColorType.RED) to "rgbmode red",
            GhostCommand.RgbColor(GhostCommand.RgbColorType.TWH_PURPLE) to "rgbmode twh-purple",
            GhostCommand.SetRgbMode("normal") to "setrgbmode normal",
            GhostCommand.SetRgbMode(GhostCommand.PersistentRgbMode.STEALTH) to "setrgbmode stealth",
            GhostCommand.SetRgbPins(1, 2, 3) to "setrgbpins 1 2 3",
            GhostCommand.SetRgbCount(16) to "setrgbcount 16",
            GhostCommand.SetNeopixelBrightness(180) to "setneopixelbrightness 180",
            GhostCommand.GetNeopixelBrightness to "getneopixelbrightness"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `time web and ghostlink commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.Timezone("UTC") to "timezone UTC",
            GhostCommand.SetTime("2024-01-01 00:00:00") to "settime 2024-01-01 00:00:00",
            GhostCommand.Time to "time",
            GhostCommand.WebAuth(true) to "webauth enable",
            GhostCommand.WebAuth(false) to "webauth disable",
            GhostCommand.WebUiAp(GhostCommand.WebUiApAction.ON) to "webuiap on",
            GhostCommand.WebUiAp(GhostCommand.WebUiApAction.OFF) to "webuiap off",
            GhostCommand.WebUiAp(GhostCommand.WebUiApAction.TOGGLE) to "webuiap toggle",
            GhostCommand.WebUiAp() to "webuiap status",
            GhostCommand.CommDiscovery to "commdiscovery",
            GhostCommand.CommConnect("peer1") to "commconnect peer1",
            GhostCommand.CommSend("ping") to "commsend ping",
            GhostCommand.CommSend("set", "data") to "commsend set data",
            GhostCommand.CommStatus to "commstatus",
            GhostCommand.CommDisconnect to "commdisconnect",
            GhostCommand.CommSetPins(17, 18) to "commsetpins 17 18"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `status display and misc tool commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.StatusIdle() to "statusidle",
            GhostCommand.StatusIdle(GhostCommand.StatusIdleAction.List) to "statusidle list",
            GhostCommand.StatusIdle(GhostCommand.StatusIdleAction.Set("matrix")) to "statusidle set matrix",
            GhostCommand.DialConnect() to "dialconnect",
            GhostCommand.DialConnect(all = true) to "dialconnect all",
            GhostCommand.DialConnect(device = "192.168.1.10") to "dialconnect 192.168.1.10",
            GhostCommand.DialConnect(all = true, device = "X") to "dialconnect all X",
            GhostCommand.TpLinkTest("on") to "tplinktest on",
            GhostCommand.PowerPrinter("192.168.1.9", "hello", 50, GhostCommand.PrinterAlignment.CENTER_MIDDLE) to
                "powerprinter 192.168.1.9 \"hello\" 50 CM",
            GhostCommand.Mirror("start") to "mirror start",
            GhostCommand.Input("up") to "input up",
            GhostCommand.UsbKbd("start") to "usbkbd start"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `mem and scan local commands map to firmware spellings`() {
        val cases = listOf(
            GhostCommand.Mem() to "mem",
            GhostCommand.Mem(GhostCommand.MemSubcommand.Dump) to "mem dump",
            GhostCommand.Mem(GhostCommand.MemSubcommand.TraceStart) to "mem trace start",
            GhostCommand.Mem(GhostCommand.MemSubcommand.TraceStop) to "mem trace stop",
            GhostCommand.Mem(GhostCommand.MemSubcommand.TraceDump) to "mem trace dump",
            GhostCommand.ScanLocal to "scanlocal",
            GhostCommand.Sweep() to "sweep",
            GhostCommand.Sweep(stop = true) to "stop",
            GhostCommand.ListenProbes() to "listenprobes",
            GhostCommand.ListenProbes(stop = true) to "listenprobes stop",
            GhostCommand.Congestion to "congestion",
            GhostCommand.ScanPorts("192.168.1.5") to "scanports 192.168.1.5",
            GhostCommand.ScanPorts("192.168.1.5", 80, 443) to "scanports 192.168.1.5 80-443",
            GhostCommand.ScanArp to "scanarp",
            GhostCommand.ScanSsh("192.168.1.5") to "scanssh 192.168.1.5",
            GhostCommand.Raw("custom command") to "custom command"
        )
        cases.forEach { (cmd, expected) ->
            assertEquals(expected, cmd.commandString)
        }
    }

    @Test
    fun `long running operations request a preceding stop`() {
        val stopFirst = listOf(
            GhostCommand.ScanAp(),
            GhostCommand.ScanSta,
            GhostCommand.ScanAll(),
            GhostCommand.TrackAp,
            GhostCommand.TrackSta,
            GhostCommand.AttackDeauth(),
            GhostCommand.AttackEapol(),
            GhostCommand.AttackSae("pw"),
            GhostCommand.SaeFlood("pw"),
            GhostCommand.BeaconSpam(),
            GhostCommand.KarmaStart(),
            GhostCommand.StartPortal(ssid = "evil"),
            GhostCommand.BleScan(GhostCommand.BleScanMode.FLIPPER),
            GhostCommand.BleAdvertiserScan(),
            GhostCommand.BleSpam(),
            GhostCommand.TrackGatt,
            GhostCommand.TrackFlipper(0),
            GhostCommand.Capture(GhostCommand.CaptureMode.DEAUTH),
            GhostCommand.CaptureWireshark(),
            GhostCommand.CaptureWiresharkBle,
            GhostCommand.BadUsbRun("x.txt"),
            GhostCommand.StartWardrive(),
            GhostCommand.WdStream(),
            GhostCommand.GpsInfo(),
            GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.Connect()),
            GhostCommand.Chameleon(GhostCommand.ChameleonSubcommand.Scan()),
            GhostCommand.Ir(GhostCommand.IrSubcommand.Learn()),
            GhostCommand.Ir(GhostCommand.IrSubcommand.Rx()),
            GhostCommand.AerialTrack("0"),
            GhostCommand.AerialSpoof(),
            GhostCommand.Sweep(),
            GhostCommand.ListenProbes()
        )
        stopFirst.forEach { cmd ->
            assertTrue("${cmd::class.simpleName} should request stop first", cmd.requiresStopFirst)
        }
    }

    @Test
    fun `stop and pure query commands do not request a preceding stop`() {
        val noStop = listOf(
            GhostCommand.Help,
            GhostCommand.ChipInfo,
            GhostCommand.Stop,
            GhostCommand.Reboot,
            GhostCommand.Identify,
            GhostCommand.StopScan,
            GhostCommand.ListResults(),
            GhostCommand.WifiStatus,
            GhostCommand.Disconnect,
            GhostCommand.ScanAp(stop = true),
            GhostCommand.BleScan(GhostCommand.BleScanMode.FLIPPER, stop = true),
            GhostCommand.BleWardrive(stop = true),
            GhostCommand.StartWardrive(stop = true),
            GhostCommand.WdStream(stop = true),
            GhostCommand.WdStream(status = true),
            GhostCommand.GpsInfo(stop = true),
            GhostCommand.Sweep(stop = true)
        )
        noStop.forEach { cmd ->
            assertFalse("${cmd::class.simpleName} should not request stop first", cmd.requiresStopFirst)
        }
    }
}
