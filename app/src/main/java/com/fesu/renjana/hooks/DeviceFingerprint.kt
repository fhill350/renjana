package com.fesu.renjana.hooks

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.fesu.renjana.utils.RenjanaLog
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Device Fingerprint Randomization Module
 *
 * Generates unique device identifiers per instance to:
 * 1. Prevent cross-instance tracking
 * 2. Evade device fingerprinting
 * 3. Make each instance appear as a different device
 * 4. Maintain consistency within an instance
 */
object DeviceFingerprint {
    private const val TAG = "DeviceFingerprint"

    // Per-instance device identifiers
    private val instanceIds = ConcurrentHashMap<String, DeviceIdentifiers>()

    // Real device identifiers (cached for comparison)
    private var realAndroidId: String? = null
    private var realSerial: String? = null

    /**
     * Data class holding all device identifiers for an instance
     */
    data class DeviceIdentifiers(
        val androidId: String,
        val serial: String,
        val imei: String,
        val macAddress: String,
        val wifiSsid: String,
        val bluetoothAddress: String,
        val advertisingId: String,
        val gsfId: String,
        val simSerial: String,
        val subscriberId: String
    )

    /**
     * Initialize device fingerprint randomization
     *
     * @param context Application context
     */
    fun initialize(context: Context) {
        // Cache real device identifiers
        realAndroidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }

        realSerial = try {
            @Suppress("DEPRECATION")
            Build.SERIAL
        } catch (e: Exception) {
            null
        }

        RenjanaLog.i(TAG, "Device fingerprint randomization initialized")
    }

    /**
     * Generate or retrieve device identifiers for an instance
     *
     * @param instanceId Instance ID
     * @return DeviceIdentifiers for this instance
     */
    fun getIdentifiers(instanceId: String): DeviceIdentifiers {
        return instanceIds.getOrPut(instanceId) {
            generateIdentifiers(instanceId)
        }
    }

    /**
     * Generate unique device identifiers for an instance
     *
     * @param instanceId Instance ID (used as seed for deterministic generation)
     * @return Generated DeviceIdentifiers
     */
    private fun generateIdentifiers(instanceId: String): DeviceIdentifiers {
        val random = SecureRandom()
        val seed = MessageDigest.getInstance("SHA-256")
            .digest(instanceId.toByteArray())

        random.setSeed(seed.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) })

        val identifiers = DeviceIdentifiers(
            androidId = generateAndroidId(random),
            serial = generateSerial(random),
            imei = generateImei(random),
            macAddress = generateMacAddress(random),
            wifiSsid = generateWifiSsid(random),
            bluetoothAddress = generateBluetoothAddress(random),
            advertisingId = generateAdvertisingId(random),
            gsfId = generateGsfId(random),
            simSerial = generateSimSerial(random),
            subscriberId = generateSubscriberId(random)
        )

        RenjanaLog.d(TAG, "Generated identifiers for instance $instanceId")
        return identifiers
    }

    /**
     * Generate Android ID (16 hex chars)
     */
    private fun generateAndroidId(random: SecureRandom): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generate device serial number
     */
    private fun generateSerial(random: SecureRandom): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..12).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    /**
     * Generate IMEI (15 digits with Luhn checksum)
     */
    private fun generateImei(random: SecureRandom): String {
        val tac = "35" + (1..6).map { random.nextInt(10) }.joinToString("")
        val serial = (1..6).map { random.nextInt(10) }.joinToString("")
        val checkDigit = calculateLuhnCheckDigit(tac + serial)
        return tac + serial + checkDigit
    }

    /**
     * Calculate Luhn check digit
     */
    private fun calculateLuhnCheckDigit(number: String): Int {
        var sum = 0
        var isEven = false

        for (i in number.length - 1 downTo 0) {
            var digit = number[i] - '0'
            if (isEven) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            isEven = !isEven
        }

        return (10 - (sum % 10)) % 10
    }

    /**
     * Generate MAC address (6 bytes, hex format)
     */
    private fun generateMacAddress(random: SecureRandom): String {
        val bytes = ByteArray(6)
        random.nextBytes(bytes)
        // Set locally administered bit and clear multicast bit
        bytes[0] = ((bytes[0].toInt() and 0xFE) or 0x02).toByte()
        return bytes.joinToString(":") { "%02x".format(it) }
    }

    /**
     * Generate WiFi SSID
     */
    private fun generateWifiSsid(random: SecureRandom): String {
        val prefixes = arrayOf("HOME-", "OFFICE-", "WIFI-", "NET-")
        val prefix = prefixes[random.nextInt(prefixes.size)]
        val suffix = (1..4).map { random.nextInt(10) }.joinToString("")
        return prefix + suffix
    }

    /**
     * Generate Bluetooth address (6 bytes, hex format)
     */
    private fun generateBluetoothAddress(random: SecureRandom): String {
        return generateMacAddress(random)
    }

    /**
     * Generate Google Advertising ID (UUID format)
     */
    private fun generateAdvertisingId(random: SecureRandom): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return formatUuid(bytes)
    }

    /**
     * Generate Google Services Framework ID (16 hex chars)
     */
    private fun generateGsfId(random: SecureRandom): String {
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generate SIM serial number (20 digits)
     */
    private fun generateSimSerial(random: SecureRandom): String {
        return (1..20).map { random.nextInt(10) }.joinToString("")
    }

    /**
     * Generate subscriber ID (IMSI, 15 digits)
     */
    private fun generateSubscriberId(random: SecureRandom): String {
        val mcc = "310" // US
        val mnc = "260" // T-Mobile
        val msin = (1..10).map { random.nextInt(10) }.joinToString("")
        return mcc + mnc + msin
    }

    /**
     * Format bytes as UUID
     */
    private fun formatUuid(bytes: ByteArray): String {
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    /**
     * Get spoofed Android ID for an instance
     */
    fun getAndroidId(instanceId: String): String {
        return getIdentifiers(instanceId).androidId
    }

    /**
     * Get spoofed serial for an instance
     */
    fun getSerial(instanceId: String): String {
        return getIdentifiers(instanceId).serial
    }

    /**
     * Get spoofed IMEI for an instance
     */
    fun getImei(instanceId: String): String {
        return getIdentifiers(instanceId).imei
    }

    /**
     * Get spoofed MAC address for an instance
     */
    fun getMacAddress(instanceId: String): String {
        return getIdentifiers(instanceId).macAddress
    }

    /**
     * Check if a value matches the real device identifier
     * Used to detect if an app is trying to read the real ID
     *
     * @param value Value to check
     * @return true if this is a real device identifier
     */
    fun isRealDeviceIdentifier(value: String): Boolean {
        return value == realAndroidId || value == realSerial
    }

    /**
     * Mutates static Build fields in the current subprocess to match the instance's identity.
     * This provides 100% reliable hardware spoofing for all bytecode direct field accesses.
     */
    fun applyDeviceSpoofToProcess(config: com.fesu.renjana.models.InstanceConfig?, instanceId: String) {
        try {
            val identifiers = getIdentifiers(instanceId)
            val model = config?.spoofModel?.ifBlank { null }
            val brand = config?.spoofBrand?.ifBlank { null }
            val manufacturer = config?.spoofManufacturer?.ifBlank { null }
            val androidVersion = config?.spoofAndroidVersion?.ifBlank { null }
            val serial = config?.spoofSerial?.ifBlank { null } ?: identifiers.serial

            // Try to match a known profile from DeviceDatabase for realistic fields
            val matchedProfile = com.fesu.renjana.core.DeviceDatabase.profiles.firstOrNull {
                (model != null && it.model.equals(model, ignoreCase = true)) ||
                (brand != null && it.brand.equals(brand, ignoreCase = true))
            }

            val effectiveModel = model ?: matchedProfile?.model ?: "SM-S911B"
            val effectiveBrand = brand ?: matchedProfile?.brand ?: "samsung"
            val effectiveManufacturer = manufacturer ?: matchedProfile?.manufacturer ?: "Samsung"
            val effectiveVersion = androidVersion ?: matchedProfile?.androidVersion ?: "14"
            val deviceCode = effectiveModel.lowercase().replace(" ", "_").replace("-", "_")

            val effectiveFingerprint = matchedProfile?.buildFingerprint ?: (
                "$effectiveBrand/$deviceCode/$deviceCode:$effectiveVersion/UP1A.231005.007/$serial:user/release-keys"
            )

            setStaticField(Build::class.java, "MODEL", effectiveModel)
            setStaticField(Build::class.java, "DEVICE", deviceCode)
            setStaticField(Build::class.java, "PRODUCT", deviceCode)
            setStaticField(Build::class.java, "BOARD", deviceCode)
            setStaticField(Build::class.java, "BRAND", effectiveBrand)
            setStaticField(Build::class.java, "MANUFACTURER", effectiveManufacturer)
            setStaticField(Build::class.java, "HARDWARE", effectiveManufacturer.lowercase())
            setStaticField(Build::class.java, "BOOTLOADER", "${effectiveModel}_1.0")
            setStaticField(Build::class.java, "SERIAL", serial)
            setStaticField(Build::class.java, "FINGERPRINT", effectiveFingerprint)
            setStaticField(Build.VERSION::class.java, "RELEASE", effectiveVersion)

            // Also register in AntiDetection intercepted system properties for SystemProperties.get calls
            AntiDetection.setSystemPropertyIntercept("ro.product.model", effectiveModel)
            AntiDetection.setSystemPropertyIntercept("ro.product.brand", effectiveBrand)
            AntiDetection.setSystemPropertyIntercept("ro.product.name", deviceCode)
            AntiDetection.setSystemPropertyIntercept("ro.product.device", deviceCode)
            AntiDetection.setSystemPropertyIntercept("ro.product.manufacturer", effectiveManufacturer)
            AntiDetection.setSystemPropertyIntercept("ro.hardware", effectiveManufacturer.lowercase())
            AntiDetection.setSystemPropertyIntercept("ro.build.version.release", effectiveVersion)
            AntiDetection.setSystemPropertyIntercept("ro.build.fingerprint", effectiveFingerprint)
            AntiDetection.setSystemPropertyIntercept("ro.serialno", serial)
            AntiDetection.setSystemPropertyIntercept("ro.boot.serialno", serial)

            RenjanaLog.i(TAG, "Applied device spoofing to process (pid=${android.os.Process.myPid()}) for instance $instanceId: $effectiveBrand $effectiveModel (Android $effectiveVersion)")
        } catch (e: Throwable) {
            RenjanaLog.w(TAG, "Failed to apply static device spoof: ${e.message}")
        }
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Throwable) {}
    }

    /**
     * Cleanup instance-specific identifiers
     */
    fun cleanup(instanceId: String) {
        instanceIds.remove(instanceId)
        RenjanaLog.d(TAG, "Cleaned up identifiers for instance $instanceId")
    }
}
