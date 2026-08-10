package com.example.ghostespcompanion.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GhostCommandTest {
    @Test
    fun `current firmware command strings`() {
        val cases = listOf(
            GhostCommand.DhcpStarve() to "dhcpstarve start",
            GhostCommand.DhcpStarve(threads = 4) to "dhcpstarve start 4",
            GhostCommand.DhcpStarve(stop = true) to "dhcpstarve stop",
            GhostCommand.DhcpStarve(display = true) to "dhcpstarve display",
            GhostCommand.ListenProbes(stop = true) to "listenprobes stop",
            GhostCommand.Sweep(stop = true) to "stop",
            GhostCommand.WebAuth(true) to "webauth on",
            GhostCommand.WebAuth(false) to "webauth off",
            GhostCommand.ScanPorts("192.168.1.5", 80, 443) to "scanports 192.168.1.5 80-443",
            GhostCommand.EthPorts("192.168.1.5", 80, 443) to "ethports 192.168.1.5 80-443",
            GhostCommand.PowerPrinter("192.168.1.9", "hello world", 50, GhostCommand.PrinterAlignment.CENTER_MIDDLE) to
                "powerprinter 192.168.1.9 \"hello world\" 50 CM",
            GhostCommand.RgbMode(GhostCommand.RgbModeType.POLICE) to "rgbmode police",
            GhostCommand.RgbMode(GhostCommand.RgbModeType.NORMAL) to "setrgbmode normal",
            GhostCommand.RgbMode(GhostCommand.RgbModeType.STEALTH) to "setrgbmode stealth",
            GhostCommand.RgbColor(GhostCommand.RgbColorType.TWH_PURPLE) to "rgbmode twh-purple",
            GhostCommand.SetRgbMode(GhostCommand.PersistentRgbMode.STEALTH) to "setrgbmode stealth"
        )

        cases.forEach { (command, expected) -> assertEquals(expected, command.commandString) }
    }

    @Test
    fun `new command families use firmware syntax`() {
        val cases = listOf(
            GhostCommand.CaptureList to "capture -list",
            GhostCommand.CaptureExport("/mnt/ghostesp/pcaps/a.pcap") to "capture -export /mnt/ghostesp/pcaps/a.pcap",
            GhostCommand.CaptureWireshark(6) to "capture -wireshark -channel 6",
            GhostCommand.CaptureWiresharkBle to "capture -wiresharkble",
            GhostCommand.StartWardrive(helper = true, channels = "1,6,11", hopMs = 250, weighted = true) to
                "startwd --helper --channels 1,6,11 --hop 250 --weighted",
            GhostCommand.BleAdvertiserScan() to "blescan -adv",
            GhostCommand.BleAdvertiserScan(GhostCommand.BleAdvertiserFilter.Oui("00:1A:2B")) to "blescan -oui 00:1A:2B",
            GhostCommand.BleAdvertiserScan(GhostCommand.BleAdvertiserFilter.Vendor("Apple")) to "blescan -vendor Apple",
            GhostCommand.BleAdvertiserScan(GhostCommand.BleAdvertiserFilter.Vendor("Nordic Semiconductor")) to
                "blescan -vendor \"Nordic Semiconductor\"",
            GhostCommand.ListAdvertisers to "listadv",
            GhostCommand.BadUsbConfig(GhostCommand.BadUsbSetting.VendorId("0x1209")) to "badusb set_vid 0x1209",
            GhostCommand.BadUsbKey(1, 6) to "badusb keysend 1 6",
            GhostCommand.BadUsbTrackpad(GhostCommand.BadUsbTrackpadAction.Move(-4, 7)) to "badusb trackpad_move -4 7",
            GhostCommand.BadUsbExec(128) to "badusb exec 128",
            GhostCommand.BadUsbStatus("running") to "badusb status running",
            GhostCommand.EthStats to "ethstats",
            GhostCommand.EthPoison(GhostCommand.EthPoisonAction.CREDS) to "ethpoison creds"
        )

        cases.forEach { (command, expected) -> assertEquals(expected, command.commandString) }
    }
}
