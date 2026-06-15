package com.example.ghostespcompanion.domain.model

/**
 * GhostESP command definitions based on firmware CLI reference
 * 
 * All commands map to the serial CLI interface of the GhostESP firmware.
 * Commands are sent over USB serial and responses are parsed accordingly.
 * 
 * Optimized for performance with object pooling and minimal allocations.
 */
sealed class GhostCommand {
    abstract val commandString: String
    abstract val timeoutMs: Long
    /** True if this command starts a new long-running operation and should be preceded by a Stop. */
    open val requiresStopFirst: Boolean = false

    // ==================== Core Commands ====================
    
    /** Display help information */
    data object Help : GhostCommand() {
        override val commandString: String = "help"
        override val timeoutMs: Long = 5000
    }
    
    /** Display chip information */
    data object ChipInfo : GhostCommand() {
        override val commandString: String = "chipinfo"
        override val timeoutMs: Long = 5000
    }
    
    /** Stop all active operations */
    data object Stop : GhostCommand() {
        override val commandString: String = "stop"
        override val timeoutMs: Long = 5000
    }
    
    /** Reboot the device */
    data object Reboot : GhostCommand() {
        override val commandString: String = "reboot"
        override val timeoutMs: Long = 10000
    }
    
    /** Identify device - returns "GHOSTESP_OK" */
    data object Identify : GhostCommand() {
        override val commandString: String = "identify"
        override val timeoutMs: Long = 3000
    }
    
    // ==================== WiFi Scan Commands ====================
    
    /** Run WiFi AP scan */
    data class ScanAp(
        val duration: Int? = null,
        val live: Boolean = false,
        val stop: Boolean = false
    ) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = !stop
        override val commandString: String = when {
            stop -> "scanap -stop"
            live -> "scanap -live"
            duration != null -> "scanap $duration"
            else -> "scanap"
        }
        override val timeoutMs: Long = if (stop) 5000 else (duration?.times(1000L) ?: 30000L) + 5000
    }
    
    /** Run WiFi station scan */
    data object ScanSta : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = "scansta"
        override val timeoutMs: Long = 30000
    }

    /** Run combined AP and STA scan */
    data class ScanAll(val duration: Int? = null) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = if (duration != null) "scanall $duration" else "scanall"
        override val timeoutMs: Long = (duration?.times(1000L) ?: 30000L) + 5000
    }
    
    /** Stop WiFi scan */
    data object StopScan : GhostCommand() {
        override val commandString: String = "stopscan"
        override val timeoutMs: Long = 5000
    }
    
    /** List scan results */
    data class ListResults(
        val mode: ListMode = ListMode.ACCESSPoints
    ) : GhostCommand() {
        override val commandString: String = when (mode) {
            ListMode.ACCESSPoints -> "list -a"
            ListMode.STATIONS -> "list -s"
            ListMode.AIR_TAGS -> "list -airtags"
        }
        override val timeoutMs: Long = 5000
    }
    
    enum class ListMode {
        ACCESSPoints, STATIONS, AIR_TAGS
    }
    
    /** Select target for actions - supports comma-separated indices for multi-select */
    data class Select(
        val target: SelectTarget,
        val indices: String // Can be single "0" or comma-separated "0,1,2"
    ) : GhostCommand() {
        override val commandString: String = when (target) {
            SelectTarget.ACCESS_POINT -> "select -a $indices"
            SelectTarget.STATION -> "select -s $indices"
            SelectTarget.AIR_TAG -> "select -airtag $indices"
            SelectTarget.FLIPPER -> "selectflipper $indices"
            SelectTarget.GATT -> "selectgatt $indices"
        }
        override val timeoutMs: Long = 5000
    }
    
    enum class SelectTarget {
        ACCESS_POINT, STATION, AIR_TAG, FLIPPER, GATT
    }
    
    /** Select AirTag by index (standalone command) */
    data class SelectAirTag(val index: String) : GhostCommand() {
        override val commandString: String = "selectairtag $index"
        override val timeoutMs: Long = 5000
    }
    
    /** Connect to WiFi network */
    data class Connect(val ssid: String, val password: String? = null) : GhostCommand() {
        override val commandString: String = if (password != null) {
            "connect \"$ssid\" \"$password\""
        } else {
            "connect \"$ssid\""
        }
        override val timeoutMs: Long = 30000
    }
    
    /** Disconnect from WiFi */
    data object Disconnect : GhostCommand() {
        override val commandString: String = "disconnect"
        override val timeoutMs: Long = 5000
    }
    
    /** Get WiFi connection status */
    data object WifiStatus : GhostCommand() {
        override val commandString: String = "wifistatus"
        override val timeoutMs: Long = 5000
    }
    
    /** Track AP RSSI */
    data object TrackAp : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = "trackap"
        override val timeoutMs: Long = Long.MAX_VALUE
    }

    /** Track station RSSI */
    data object TrackSta : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = "tracksta"
        override val timeoutMs: Long = Long.MAX_VALUE
    }
    
    // ==================== WiFi Attack Commands ====================
    
    /** Run deauth attack */
    data class AttackDeauth(val targetIndex: String? = null) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = "attack -d"
        override val timeoutMs: Long = Long.MAX_VALUE
    }

    /** Run EAPOL logoff attack */
    data class AttackEapol(val targetIndex: String? = null) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = "attack -e"
        override val timeoutMs: Long = Long.MAX_VALUE
    }

    /** Run SAE flood attack */
    data class AttackSae(val password: String) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = "attack -s $password"
        override val timeoutMs: Long = Long.MAX_VALUE
    }

    /** SAE flood with password */
    data class SaeFlood(val password: String) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = "saeflood $password"
        override val timeoutMs: Long = Long.MAX_VALUE
    }
    
    /** Stop SAE flood */
    data object StopSaeFlood : GhostCommand() {
        override val commandString: String = "stopsaeflood"
        override val timeoutMs: Long = 5000
    }
    
    /** Stop deauth attack */
    data object StopDeauth : GhostCommand() {
        override val commandString: String = "stopdeauth"
        override val timeoutMs: Long = 5000
    }
    
    /** Stop beacon spam */
    data object StopSpam : GhostCommand() {
        override val commandString: String = "stopspam"
        override val timeoutMs: Long = 5000
    }
    
    /** Start beacon spam - firmware supports: -r (random), -rr (rickroll), -l (AP list), or custom name */
    data class BeaconSpam(val mode: BeaconSpamMode = BeaconSpamMode.RANDOM) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = when (mode) {
            BeaconSpamMode.RANDOM -> "beaconspam -r"
            BeaconSpamMode.RICKROLL -> "beaconspam -rr"
            BeaconSpamMode.AP_LIST -> "beaconspam -l"
            is BeaconSpamMode.CUSTOM -> "beaconspam ${mode.name}"
        }
        override val timeoutMs: Long = Long.MAX_VALUE
    }
    
    sealed class BeaconSpamMode {
        data object RANDOM : BeaconSpamMode()
        data object RICKROLL : BeaconSpamMode()
        data object AP_LIST : BeaconSpamMode()
        data class CUSTOM(val name: String) : BeaconSpamMode()
    }
    
    /** Add beacon to list */
    data class BeaconAdd(val ssid: String) : GhostCommand() {
        override val commandString: String = "beaconadd $ssid"
        override val timeoutMs: Long = 5000
    }
    
    /** Remove beacon from list */
    data class BeaconRemove(val ssid: String) : GhostCommand() {
        override val commandString: String = "beaconremove $ssid"
        override val timeoutMs: Long = 5000
    }
    
    /** Clear beacon list */
    data object BeaconClear : GhostCommand() {
        override val commandString: String = "beaconclear"
        override val timeoutMs: Long = 5000
    }
    
    /** Show beacon list */
    data object BeaconShow : GhostCommand() {
        override val commandString: String = "beaconshow"
        override val timeoutMs: Long = 5000
    }
    
    /** Start karma attack */
    data class KarmaStart(val ssids: List<String>? = null) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = if (ssids != null && ssids.isNotEmpty()) {
            "karma start ${ssids.joinToString(" ")}"
        } else {
            "karma start"
        }
        override val timeoutMs: Long = Long.MAX_VALUE
    }
    
    /** Stop karma attack */
    data object KarmaStop : GhostCommand() {
        override val commandString: String = "karma stop"
        override val timeoutMs: Long = 5000
    }
    
    // ==================== Portal Commands ====================
    
    /** Start evil portal */
    data class StartPortal(
        val path: String = "default",
        val ssid: String,
        val password: String? = null
    ) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = if (password != null) {
            "startportal $path \"$ssid\" \"$password\""
        } else {
            "startportal $path \"$ssid\""
        }
        override val timeoutMs: Long = 10000
    }
    
    /** Stop evil portal */
    data object StopPortal : GhostCommand() {
        override val commandString: String = "stopportal"
        override val timeoutMs: Long = 5000
    }
    
    /** List available portals */
    data object ListPortals : GhostCommand() {
        override val commandString: String = "listportals"
        override val timeoutMs: Long = 5000
    }
    
    /** Evil portal commands */
    data class EvilPortal(val command: PortalCommand) : GhostCommand() {
        override val commandString: String = "evilportal -c ${command.value}"
        override val timeoutMs: Long = 5000
    }
    
    enum class PortalCommand(val value: String) {
        SET_HTML("sethtmlstr"),
        CLEAR("clear")
    }
    
    // ==================== BLE Commands ====================
    
    /** Scan for BLE devices - firmware: -f (flipper), -ds (spam detector), -a (airtag), -r (raw), -g (gatt), -s (stop) */
    data class BleScan(
        val mode: BleScanMode,
        val stop: Boolean = false
    ) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = !stop
        override val commandString: String = when {
            stop -> "blescan -s"
            mode == BleScanMode.FLIPPER -> "blescan -f"
            mode == BleScanMode.SPAM_DETECTOR -> "blescan -ds"
            mode == BleScanMode.AIR_TAG -> "blescan -a"
            mode == BleScanMode.RAW -> "blescan -r"
            mode == BleScanMode.GATT -> "blescan -g"
            else -> "blescan"
        }
        override val timeoutMs: Long = if (stop) 5000 else 30000
    }
    
    enum class BleScanMode {
        FLIPPER, SPAM_DETECTOR, AIR_TAG, RAW, GATT
    }
    
    /** Stop BLE scan */
    data object BleScanStop : GhostCommand() {
        override val commandString: String = "blescan -s"
        override val timeoutMs: Long = 5000
    }
    
    /** Start BLE spam - firmware: -apple, -ms, -samsung, -google, -random, -s (stop) */
    data class BleSpam(val mode: BleSpamMode? = null) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = mode != BleSpamMode.STOP
        override val commandString: String = when (mode) {
            null -> "blespam"
            BleSpamMode.APPLE -> "blespam -apple"
            BleSpamMode.MICROSOFT -> "blespam -ms"
            BleSpamMode.SAMSUNG -> "blespam -samsung"
            BleSpamMode.GOOGLE -> "blespam -google"
            BleSpamMode.RANDOM -> "blespam -random"
            BleSpamMode.STOP -> "blespam -s"
        }
        override val timeoutMs: Long = if (mode == BleSpamMode.STOP) 5000 else Long.MAX_VALUE
    }
    
    enum class BleSpamMode {
        APPLE, MICROSOFT, SAMSUNG, GOOGLE, RANDOM, STOP
    }
    
    /** List discovered Flipper devices */
    data object ListFlippers : GhostCommand() {
        override val commandString: String = "listflippers"
        override val timeoutMs: Long = 5000
    }
    
    /** List discovered AirTags */
    data object ListAirTags : GhostCommand() {
        override val commandString: String = "listairtags"
        override val timeoutMs: Long = 5000
    }
    
    /** List GATT devices */
    data object ListGatt : GhostCommand() {
        override val commandString: String = "listgatt"
        override val timeoutMs: Long = 5000
    }
    
    /** Enumerate GATT services */
    data object EnumGatt : GhostCommand() {
        override val commandString: String = "enumgatt"
        override val timeoutMs: Long = 30000
    }
    
    /** Track GATT device - uses the device selected by selectgatt */
    data object TrackGatt : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = "trackgatt"
        override val timeoutMs: Long = Long.MAX_VALUE
    }

    /** Select Flipper by index and start tracking (selectflipper N triggers tracking immediately) */
    data class TrackFlipper(val index: Int) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = "selectflipper $index"
        override val timeoutMs: Long = Long.MAX_VALUE
    }

    /** Spoof AirTag */
    data class SpoofAirTag(val start: Boolean) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = start
        override val commandString: String = if (start) "spoofairtag" else "stopspoof"
        override val timeoutMs: Long = if (start) Long.MAX_VALUE else 5000
    }

    /** BLE wardriving */
    data class BleWardrive(val stop: Boolean = false) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = !stop
        override val commandString: String = if (stop) "blewardriving -s" else "blewardriving"
        override val timeoutMs: Long = if (stop) 5000 else Long.MAX_VALUE
    }
    
    // ==================== Chameleon/NFC Commands ====================
    
    /** Chameleon Ultra commands - NFC card reading/writing and cloning */
    data class Chameleon(val subcommand: ChameleonSubcommand) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = subcommand.requiresStopFirst
        override val commandString: String = subcommand.commandString
        override val timeoutMs: Long = subcommand.timeoutMs
    }

    sealed class ChameleonSubcommand {
        abstract val commandString: String
        abstract val timeoutMs: Long
        open val requiresStopFirst: Boolean = false

        /** Connect to Chameleon Ultra device */
        data class Connect(val timeout: Int = 30, val pin: Int? = null) : ChameleonSubcommand() {
            override val requiresStopFirst: Boolean = true
            override val commandString: String = if (pin != null) {
                "chameleon connect $timeout $pin"
            } else {
                "chameleon connect $timeout"
            }
            override val timeoutMs: Long = (timeout * 1000L) + 5000
        }
        
        /** Scan for NFC tag */
        data class Scan(val timeout: Int = 60) : ChameleonSubcommand() {
            override val requiresStopFirst: Boolean = true
            override val commandString: String = "chameleon scan $timeout"
            override val timeoutMs: Long = (timeout * 1000L) + 5000
        }
        
        /** Stop NFC scan */
        data object ScanStop : ChameleonSubcommand() {
            override val commandString: String = "chameleon scan stop"
            override val timeoutMs: Long = 5000
        }
        
        /** Read NFC tag */
        data class Read(val slot: Int? = null) : ChameleonSubcommand() {
            override val commandString: String = if (slot != null) {
                "chameleon read $slot"
            } else {
                "chameleon read"
            }
            override val timeoutMs: Long = 30000
        }
        
        /** Write to NFC tag */
        data class Write(val slot: Int) : ChameleonSubcommand() {
            override val commandString: String = "chameleon write $slot"
            override val timeoutMs: Long = 30000
        }
        
        /** List saved slots */
        data object List : ChameleonSubcommand() {
            override val commandString: String = "chameleon list"
            override val timeoutMs: Long = 5000
        }
        
        /** Delete a slot */
        data class Delete(val slot: Int) : ChameleonSubcommand() {
            override val commandString: String = "chameleon delete $slot"
            override val timeoutMs: Long = 5000
        }
    }
    
    // ==================== IR Commands ====================
    
    /** IR commands - firmware: list, send, learn, rx, dazzler, universals */
    data class Ir(val subcommand: IrSubcommand) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = subcommand.requiresStopFirst
        override val commandString: String = subcommand.commandString
        override val timeoutMs: Long = subcommand.timeoutMs
    }

    sealed class IrSubcommand {
        abstract val commandString: String
        abstract val timeoutMs: Long
        open val requiresStopFirst: Boolean = false

        data class List(val path: String? = null) : IrSubcommand() {
            override val commandString: String = if (path != null) "ir list $path" else "ir list"
            override val timeoutMs: Long = 5000
        }
        
        data class Send(val remote: String, val buttonIndex: Int? = null) : IrSubcommand() {
            override val commandString: String = if (buttonIndex != null) "ir send $remote $buttonIndex" else "ir send $remote"
            override val timeoutMs: Long = 5000
        }
        
        data class Learn(val path: String? = null) : IrSubcommand() {
            override val requiresStopFirst: Boolean = true
            override val commandString: String = if (path != null) "ir learn $path" else "ir learn"
            override val timeoutMs: Long = 15000
        }

        data class Rx(val timeout: Int = 60) : IrSubcommand() {
            override val requiresStopFirst: Boolean = true
            override val commandString: String = "ir rx $timeout"
            override val timeoutMs: Long = (timeout * 1000L) + 5000
        }

        data class Dazzler(val stop: Boolean = false) : IrSubcommand() {
            override val requiresStopFirst: Boolean get() = !stop
            override val commandString: String = if (stop) "ir dazzler stop" else "ir dazzler"
            override val timeoutMs: Long = if (stop) 5000 else Long.MAX_VALUE
        }

        data class Universals(val subcommand: UniversalsSubcommand) : IrSubcommand() {
            override val requiresStopFirst: Boolean get() = subcommand.requiresStopFirst
            override val commandString: String = subcommand.commandString
            override val timeoutMs: Long = subcommand.timeoutMs
        }

        sealed class UniversalsSubcommand {
            abstract val commandString: String
            abstract val timeoutMs: Long
            open val requiresStopFirst: Boolean = false
            
            data object List : UniversalsSubcommand() {
                override val commandString: String = "ir universals list"
                override val timeoutMs: Long = 5000
            }
            
            data object ListAll : UniversalsSubcommand() {
                override val commandString: String = "ir universals list -all"
                override val timeoutMs: Long = 5000
            }
            
            data class Send(val index: Int) : UniversalsSubcommand() {
                override val commandString: String = "ir universals send $index"
                override val timeoutMs: Long = 5000
            }
            
            data class SendAll(val file: String, val buttonName: String, val delayMs: Int? = null) : UniversalsSubcommand() {
                override val requiresStopFirst: Boolean = true
                override val commandString: String = if (delayMs != null) {
                    "ir universals sendall $file $buttonName $delayMs"
                } else {
                    "ir universals sendall $file $buttonName"
                }
                override val timeoutMs: Long = Long.MAX_VALUE
            }
        }
        
        /** Show IR signal from file */
        data class Show(val remote: String) : IrSubcommand() {
            override val commandString: String = "ir show $remote"
            override val timeoutMs: Long = 5000
        }
        
        /** Inline IR transmit */
        data object Inline : IrSubcommand() {
            override val commandString: String = "ir inline"
            override val timeoutMs: Long = 5000
        }
    }
    
    // ==================== BadUSB Commands ====================
    
    /** List BadUSB scripts */
    data object BadUsbList : GhostCommand() {
        override val commandString: String = "badusb list"
        override val timeoutMs: Long = 5000
    }
    
    /** Run BadUSB script */
    data class BadUsbRun(val filename: String) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = "badusb run $filename"
        override val timeoutMs: Long = Long.MAX_VALUE
    }
    
    /** Stop BadUSB script */
    data object BadUsbStop : GhostCommand() {
        override val commandString: String = "badusb stop"
        override val timeoutMs: Long = 5000
    }

    /** Start USB keyboard mode */
    data object BadUsbKeyboardStart : GhostCommand() {
        override val commandString: String = "badusb keyboard_start"
        override val timeoutMs: Long = 5000
    }

    /** Stop USB keyboard mode */
    data object BadUsbKeyboardStop : GhostCommand() {
        override val commandString: String = "badusb keyboard_stop"
        override val timeoutMs: Long = 5000
    }

    /** Type text through BadUSB keyboard mode */
    data class BadUsbType(val text: String) : GhostCommand() {
        override val commandString: String = "badusb type $text"
        override val timeoutMs: Long = 5000
    }

    /** Start mouse jiggler */
    data object BadUsbJiggleStart : GhostCommand() {
        override val commandString: String = "badusb jiggle_start"
        override val timeoutMs: Long = 5000
    }

    /** Stop mouse jiggler */
    data object BadUsbJiggleStop : GhostCommand() {
        override val commandString: String = "badusb jiggle_stop"
        override val timeoutMs: Long = 5000
    }

    // ==================== GPS Commands ====================
    
    /** Get GPS info */
    data class GpsInfo(val stop: Boolean = false) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = !stop
        override val commandString: String = if (stop) "gpsinfo -s" else "gpsinfo"
        override val timeoutMs: Long = if (stop) 5000 else Long.MAX_VALUE
    }

    /** Start wardriving */
    data class StartWardrive(val stop: Boolean = false) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = !stop
        override val commandString: String = if (stop) "startwd -s" else "startwd"
        override val timeoutMs: Long = if (stop) 5000 else Long.MAX_VALUE
    }
    
    /** Set GPS pin */
    data class GpsPin(val pin: Int) : GhostCommand() {
        override val commandString: String = "gpspin $pin"
        override val timeoutMs: Long = 5000
    }
    
    // ==================== SD Card Commands ====================
    
    /** Get SD card status */
    data object SdStatus : GhostCommand() {
        override val commandString: String = "sd status"
        override val timeoutMs: Long = 5000
    }
    
    /** List SD card files */
    data class SdList(val path: String? = null) : GhostCommand() {
        override val commandString: String = if (path != null) "sd list $path" else "sd list"
        override val timeoutMs: Long = 5000
    }
    
    /** Read file from SD */
    data class SdRead(
        val path: String,
        val offset: Int? = null,
        val length: Int? = null,
        val base64: Boolean = false
    ) : GhostCommand() {
        override val commandString: String = buildString {
            append("sd read $path")
            offset?.let { append(" $it") }
            length?.let { append(" $it") }
            if (base64) append(" --base64")
        }
        override val timeoutMs: Long = 10000
    }
    
    /** Get file or directory info */
    data class SdInfo(val path: String) : GhostCommand() {
        override val commandString: String = "sd info $path"
        override val timeoutMs: Long = 5000
    }
    
    /** Get file size in bytes */
    data class SdSize(val path: String) : GhostCommand() {
        override val commandString: String = "sd size $path"
        override val timeoutMs: Long = 5000
    }
    
    /** Write file to SD with base64 data */
    data class SdWrite(val path: String, val base64Data: String) : GhostCommand() {
        override val commandString: String = "sd write $path $base64Data"
        override val timeoutMs: Long = 30000
    }
    
    /** Append base64 data to file */
    data class SdAppend(val path: String, val base64Data: String) : GhostCommand() {
        override val commandString: String = "sd append $path $base64Data"
        override val timeoutMs: Long = 30000
    }
    
    /** Create directory */
    data class SdMkdir(val path: String) : GhostCommand() {
        override val commandString: String = "sd mkdir $path"
        override val timeoutMs: Long = 5000
    }
    
    /** Delete file or directory */
    data class SdRm(val path: String) : GhostCommand() {
        override val commandString: String = "sd rm $path"
        override val timeoutMs: Long = 5000
    }
    
    /** Recursive directory listing */
    data class SdTree(val path: String? = null, val depth: Int? = null) : GhostCommand() {
        override val commandString: String = buildString {
            append("sd tree")
            path?.let { append(" $it") }
            depth?.let { append(" $it") }
        }
        override val timeoutMs: Long = 10000
    }
    
    /** Show SD configuration */
    data object SdConfig : GhostCommand() {
        override val commandString: String = "sd_config"
        override val timeoutMs: Long = 5000
    }
    
    /** Configure SD pins for SPI mode */
    data class SdPinsSpi(val cs: Int, val clk: Int, val miso: Int, val mosi: Int) : GhostCommand() {
        override val commandString: String = "sd_pins_spi $cs $clk $miso $mosi"
        override val timeoutMs: Long = 5000
    }
    
    /** Configure SD pins for MMC mode */
    data class SdPinsMmc(val clk: Int, val cmd: Int, val d0: Int, val d1: Int, val d2: Int, val d3: Int) : GhostCommand() {
        override val commandString: String = "sd_pins_mmc $clk $cmd $d0 $d1 $d2 $d3"
        override val timeoutMs: Long = 5000
    }
    
    /** Save SD configuration */
    data object SdSaveConfig : GhostCommand() {
        override val commandString: String = "sd_save_config"
        override val timeoutMs: Long = 5000
    }
    
    // ==================== Settings Commands ====================
    
    /** List all settings */
    data object SettingsList : GhostCommand() {
        override val commandString: String = "settings list"
        override val timeoutMs: Long = 5000
    }
    
    /** Get a setting value */
    data class SettingsGet(val key: String) : GhostCommand() {
        override val commandString: String = "settings get $key"
        override val timeoutMs: Long = 5000
    }
    
    /** Set a setting value */
    data class SettingsSet(val key: String, val value: String) : GhostCommand() {
        override val commandString: String = "settings set $key $value"
        override val timeoutMs: Long = 5000
    }
    
    // ==================== Capture Commands ====================
    
    /** Capture packets using firmware-backed capture modes */
    data class Capture(val mode: CaptureMode, val channel: Int? = null) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = buildString {
            append("capture ${mode.value}")
            channel?.let { append(" -channel $it") }
        }
        override val timeoutMs: Long = Long.MAX_VALUE
    }

    data object CaptureStop : GhostCommand() {
        override val commandString: String = "capture -stop"
        override val timeoutMs: Long = 5000
    }
    
    enum class CaptureMode(val value: String) {
        PROBE("-probe"),
        DEAUTH("-deauth"),
        BEACON("-beacon"),
        RAW("-raw"),
        IEEE802154("-802154"),
        EAPOL("-eapol"),
        PWN("-pwn"),
        WPS("-wps"),
        BLE("-ble"),
        SKIMMER("-skimmer")
    }
    
    // ==================== Aerial (Drone) Commands ====================
    
    /** Start aerial scan */
    data class AerialScan(val duration: Int = 30, val stop: Boolean = false) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = !stop
        override val commandString: String = if (stop) "aerialstop" else "aerialscan $duration"
        override val timeoutMs: Long = if (stop) 5000 else (duration * 1000L) + 5000
    }

    /** List aerial devices */
    data object AerialList : GhostCommand() {
        override val commandString: String = "aeriallist"
        override val timeoutMs: Long = 5000
    }

    /** Track aerial device */
    data class AerialTrack(val indexOrMac: String) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = "aerialtrack $indexOrMac"
        override val timeoutMs: Long = Long.MAX_VALUE
    }

    /** Spoof aerial device */
    data class AerialSpoof(
        val deviceId: String = "GHOST-TEST",
        val lat: Double = 37.7749,
        val lon: Double = -122.4194,
        val alt: Float = 100.0f
    ) : GhostCommand() {
        override val requiresStopFirst: Boolean = true
        override val commandString: String = "aerialspoof $deviceId $lat $lon $alt"
        override val timeoutMs: Long = Long.MAX_VALUE
    }
    
    /** Stop aerial spoofing */
    data object AerialSpoofStop : GhostCommand() {
        override val commandString: String = "aerialspoofstop"
        override val timeoutMs: Long = 5000
    }
    
    // ==================== Ethernet Commands ====================
    
    /** Ethernet up */
    data object EthUp : GhostCommand() {
        override val commandString: String = "ethup"
        override val timeoutMs: Long = 10000
    }
    
    /** Ethernet down */
    data object EthDown : GhostCommand() {
        override val commandString: String = "ethdown"
        override val timeoutMs: Long = 5000
    }
    
    /** Ethernet info */
    data object EthInfo : GhostCommand() {
        override val commandString: String = "ethinfo"
        override val timeoutMs: Long = 5000
    }
    
    /** Ethernet fingerprint scan */
    data class EthFingerprint(val ip: String) : GhostCommand() {
        override val commandString: String = "ethfp $ip"
        override val timeoutMs: Long = 30000
    }
    
    /** Ethernet ARP scan */
    data object EthArp : GhostCommand() {
        override val commandString: String = "etharp"
        override val timeoutMs: Long = 30000
    }
    
    /** Ethernet port scan */
    data class EthPorts(val ip: String, val startPort: Int? = null, val endPort: Int? = null) : GhostCommand() {
        override val commandString: String = buildString {
            append("ethports $ip")
            startPort?.let { append(" $it") }
            endPort?.let { append(" $it") }
        }
        override val timeoutMs: Long = 60000
    }
    
    /** Ethernet DNS lookup */
    data class EthDns(val hostname: String, val reverse: Boolean = false) : GhostCommand() {
        override val commandString: String = if (reverse) "ethdns reverse $hostname" else "ethdns $hostname"
        override val timeoutMs: Long = 10000
    }
    
    /** Ethernet traceroute */
    data class EthTrace(val hostname: String, val maxHops: Int? = null) : GhostCommand() {
        override val commandString: String = if (maxHops != null) "ethtrace $hostname $maxHops" else "ethtrace $hostname"
        override val timeoutMs: Long = 60000
    }
    
    /** Ethernet ping */
    data object EthPing : GhostCommand() {
        override val commandString: String = "ethping"
        override val timeoutMs: Long = 30000
    }
    
    /** Ethernet configuration */
    data class EthConfig(val mode: EthConfigMode, val ip: String? = null, val netmask: String? = null, val gateway: String? = null) : GhostCommand() {
        override val commandString: String = when (mode) {
            EthConfigMode.DHCP -> "ethconfig dhcp"
            EthConfigMode.STATIC -> "ethconfig static $ip $netmask $gateway"
            EthConfigMode.SHOW -> "ethconfig show"
        }
        override val timeoutMs: Long = 10000
    }
    
    enum class EthConfigMode {
        DHCP, STATIC, SHOW
    }
    
    /** Get/Set Ethernet MAC address */
    data class EthMac(val mac: String? = null) : GhostCommand() {
        override val commandString: String = if (mac != null) "ethmac set $mac" else "ethmac"
        override val timeoutMs: Long = 5000
    }
    
    /** Ethernet service discovery */
    data class EthServ(val ip: String? = null) : GhostCommand() {
        override val commandString: String = if (ip != null) "ethserv $ip" else "ethserv"
        override val timeoutMs: Long = 30000
    }
    
    /** NTP time sync over Ethernet */
    data class EthNtp(val ntpServer: String? = null) : GhostCommand() {
        override val commandString: String = if (ntpServer != null) "ethntp $ntpServer" else "ethntp"
        override val timeoutMs: Long = 10000
    }
    
    /** HTTP request over Ethernet */
    data class EthHttp(val url: String, val lines: Int? = null, val showAll: Boolean = false) : GhostCommand() {
        override val commandString: String = buildString {
            append("ethhttp $url")
            when {
                showAll -> append(" all")
                lines != null -> append(" $lines")
            }
        }
        override val timeoutMs: Long = 30000
    }
    
    // ==================== Misc Commands ====================
    
    /** DHCP starvation */
    data class DhcpStarve(val stop: Boolean = false) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = !stop
        override val commandString: String = if (stop) "dhcpstarve -s" else "dhcpstarve"
        override val timeoutMs: Long = if (stop) 5000 else Long.MAX_VALUE
    }
    
    /** SAE flood help */
    data object SaeFloodHelp : GhostCommand() {
        override val commandString: String = "saefloodhelp"
        override val timeoutMs: Long = 5000
    }
    
    /** Beacon spam list */
    data object BeaconSpamList : GhostCommand() {
        override val commandString: String = "beaconspamlist"
        override val timeoutMs: Long = 5000
    }
    
    /** AP credentials with reset option */
    data class ApCred(val ssid: String? = null, val password: String? = null, val reset: Boolean = false) : GhostCommand() {
        override val commandString: String = when {
            reset -> "apcred -r"
            ssid != null && password != null -> "apcred \"$ssid\" \"$password\""
            ssid != null -> "apcred \"$ssid\""
            else -> "apcred"
        }
        override val timeoutMs: Long = 5000
    }
    
    /** PineAP detection */
    data object PineAp : GhostCommand() {
        override val commandString: String = "pineap"
        override val timeoutMs: Long = 30000
    }
    
    /** Enable AP */
    data class ApEnable(val enable: Boolean) : GhostCommand() {
        override val commandString: String = "apenable ${if (enable) "on" else "off"}"
        override val timeoutMs: Long = 5000
    }
    
    /** RGB mode */
    data class RgbMode(val mode: RgbModeType) : GhostCommand() {
        override val commandString: String = "rgbmode ${mode.value}"
        override val timeoutMs: Long = 5000
    }
    
    enum class RgbModeType(val value: String) {
        NORMAL("normal"),
        RAINBOW("rainbow"),
        STEALTH("stealth")
    }
    
    /** Set RGB mode */
    data class SetRgbMode(val mode: String) : GhostCommand() {
        override val commandString: String = "setrgbmode $mode"
        override val timeoutMs: Long = 5000
    }
    
    /** Set RGB pins */
    data class SetRgbPins(val red: Int, val green: Int, val blue: Int) : GhostCommand() {
        override val commandString: String = "setrgbpins $red $green $blue"
        override val timeoutMs: Long = 5000
    }
    
    /** Set RGB LED count */
    data class SetRgbCount(val count: Int) : GhostCommand() {
        override val commandString: String = "setrgbcount $count"
        override val timeoutMs: Long = 5000
    }
    
    /** Set neopixel brightness */
    data class SetNeopixelBrightness(val brightness: Int) : GhostCommand() {
        override val commandString: String = "setneopixelbrightness $brightness"
        override val timeoutMs: Long = 5000
    }
    
    /** Get neopixel brightness */
    data object GetNeopixelBrightness : GhostCommand() {
        override val commandString: String = "getneopixelbrightness"
        override val timeoutMs: Long = 5000
    }
    
    /** Timezone */
    data class Timezone(val tz: String) : GhostCommand() {
        override val commandString: String = "timezone $tz"
        override val timeoutMs: Long = 5000
    }
    
    /** Set time */
    data class SetTime(val time: String) : GhostCommand() {
        override val commandString: String = "settime $time"
        override val timeoutMs: Long = 5000
    }
    
    /** Get time */
    data object Time : GhostCommand() {
        override val commandString: String = "time"
        override val timeoutMs: Long = 5000
    }
    
    /** Web auth */
    data class WebAuth(val enable: Boolean) : GhostCommand() {
        override val commandString: String = "webauth ${if (enable) "on" else "off"}"
        override val timeoutMs: Long = 5000
    }
    
    /** Web UI AP */
    data class WebUiAp(val action: WebUiApAction = WebUiApAction.STATUS) : GhostCommand() {
        override val commandString: String = when (action) {
            WebUiApAction.ON -> "webuiap on"
            WebUiApAction.OFF -> "webuiap off"
            WebUiApAction.TOGGLE -> "webuiap toggle"
            WebUiApAction.STATUS -> "webuiap status"
        }
        override val timeoutMs: Long = 10000
    }
    
    enum class WebUiApAction {
        ON, OFF, TOGGLE, STATUS
    }
    
    // ==================== GhostLink (Dual Communication) Commands ====================
    
    /** Discover other GhostESP devices */
    data object CommDiscovery : GhostCommand() {
        override val commandString: String = "commdiscovery"
        override val timeoutMs: Long = 30000
    }
    
    /** Connect to a discovered GhostESP peer */
    data class CommConnect(val peerName: String) : GhostCommand() {
        override val commandString: String = "commconnect $peerName"
        override val timeoutMs: Long = 10000
    }
    
    /** Send command to connected peer */
    data class CommSend(val command: String, val data: String? = null) : GhostCommand() {
        override val commandString: String = if (data != null) {
            "commsend $command $data"
        } else {
            "commsend $command"
        }
        override val timeoutMs: Long = 30000
    }
    
    /** Get GhostLink connection status */
    data object CommStatus : GhostCommand() {
        override val commandString: String = "commstatus"
        override val timeoutMs: Long = 5000
    }
    
    /** Disconnect from peer */
    data object CommDisconnect : GhostCommand() {
        override val commandString: String = "commdisconnect"
        override val timeoutMs: Long = 5000
    }
    
    /** Set preferred GhostLink TX/RX pins */
    data class CommSetPins(val tx: Int, val rx: Int) : GhostCommand() {
        override val commandString: String = "commsetpins $tx $rx"
        override val timeoutMs: Long = 5000
    }
    
    // ==================== Status Display Commands ====================
    
    /** Status display idle animation */
    data class StatusIdle(val action: StatusIdleAction = StatusIdleAction.SHOW) : GhostCommand() {
        override val commandString: String = when (action) {
            is StatusIdleAction.List -> "statusidle list"
            is StatusIdleAction.Set -> "statusidle set ${action.mode}"
            StatusIdleAction.SHOW -> "statusidle"
        }
        override val timeoutMs: Long = 5000
    }
    
    sealed class StatusIdleAction {
        data object SHOW : StatusIdleAction()
        data object List : StatusIdleAction()
        data class Set(val mode: String) : StatusIdleAction()
    }
    
    /** DIAL connect */
    data class DialConnect(val device: String? = null, val all: Boolean = false) : GhostCommand() {
        override val commandString: String = buildString {
            append("dialconnect")
            if (all) append(" all")
            device?.let { append(" $it") }
        }
        override val timeoutMs: Long = 30000
    }
    
    /** TP-Link test */
    data class TpLinkTest(val mode: String) : GhostCommand() {
        override val commandString: String = "tplinktest $mode"
        override val timeoutMs: Long = 10000
    }
    
    /** Power printer */
    data class PowerPrinter(val ip: String, val text: String) : GhostCommand() {
        override val commandString: String = "powerprinter $ip \"$text\""
        override val timeoutMs: Long = 10000
    }
    
    /** Screen mirror */
    data class Mirror(val command: String) : GhostCommand() {
        override val commandString: String = "mirror $command"
        override val timeoutMs: Long = 5000
    }
    
    /** Input command */
    data class Input(val direction: String) : GhostCommand() {
        override val commandString: String = "input $direction"
        override val timeoutMs: Long = 1000
    }
    
    /** USB keyboard */
    data class UsbKbd(val command: String) : GhostCommand() {
        override val commandString: String = "usbkbd $command"
        override val timeoutMs: Long = 5000
    }
    
    /** Memory info */
    data class Mem(val subcommand: MemSubcommand = MemSubcommand.Status) : GhostCommand() {
        override val commandString: String = subcommand.commandString
        override val timeoutMs: Long = subcommand.timeoutMs
    }
    
    sealed class MemSubcommand {
        abstract val commandString: String
        abstract val timeoutMs: Long
        
        data object Status : MemSubcommand() {
            override val commandString: String = "mem"
            override val timeoutMs: Long = 5000
        }
        
        data object Dump : MemSubcommand() {
            override val commandString: String = "mem dump"
            override val timeoutMs: Long = 10000
        }
        
        data object TraceStart : MemSubcommand() {
            override val commandString: String = "mem trace start"
            override val timeoutMs: Long = 5000
        }
        
        data object TraceStop : MemSubcommand() {
            override val commandString: String = "mem trace stop"
            override val timeoutMs: Long = 5000
        }
        
        data object TraceDump : MemSubcommand() {
            override val commandString: String = "mem trace dump"
            override val timeoutMs: Long = 10000
        }
    }
    
    /** Scan local (IP lookup) */
    data object ScanLocal : GhostCommand() {
        override val commandString: String = "scanlocal"
        override val timeoutMs: Long = 10000
    }
    
    /** Sweep channels */
    data class Sweep(val stop: Boolean = false) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = !stop
        override val commandString: String = if (stop) "sweep -s" else "sweep"
        override val timeoutMs: Long = if (stop) 5000 else Long.MAX_VALUE
    }

    /** Listen probes */
    data class ListenProbes(val stop: Boolean = false) : GhostCommand() {
        override val requiresStopFirst: Boolean get() = !stop
        override val commandString: String = if (stop) "listenprobes -s" else "listenprobes"
        override val timeoutMs: Long = if (stop) 5000 else Long.MAX_VALUE
    }
    
    /** Congestion detection */
    data object Congestion : GhostCommand() {
        override val commandString: String = "congestion"
        override val timeoutMs: Long = 30000
    }
    
    /** Scan ports */
    data class ScanPorts(val target: String, val startPort: Int? = null, val endPort: Int? = null) : GhostCommand() {
        override val commandString: String = buildString {
            append("scanports $target")
            startPort?.let { append(" $it") }
            endPort?.let { append(" $it") }
        }
        override val timeoutMs: Long = 60000
    }
    
    /** Scan ARP */
    data object ScanArp : GhostCommand() {
        override val commandString: String = "scanarp"
        override val timeoutMs: Long = 30000
    }
    
    /** Scan SSH */
    data class ScanSsh(val target: String) : GhostCommand() {
        override val commandString: String = "scanssh $target"
        override val timeoutMs: Long = 30000
    }
    
    // ==================== Raw Command ====================
    
    /** Send raw command string directly */
    data class Raw(val command: String) : GhostCommand() {
        override val commandString: String = command
        override val timeoutMs: Long = 10000
    }
}
