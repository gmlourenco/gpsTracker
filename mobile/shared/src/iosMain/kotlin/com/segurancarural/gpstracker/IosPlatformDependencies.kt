package com.segurancarural.gpstracker

import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDevice

class IosPlatformDependencies : PlatformDependencies {
    private val defaults = NSUserDefaults.standardUserDefaults
    
    override fun shouldUploadOverCurrentNetwork(): Boolean {
        // Simple implementation for now. Can be expanded to check actual network type.
        return true
    }

    override fun getDeviceMarkerColorHex(): String? {
        return defaults.stringForKey("device_marker_color")
    }

    override fun ensureSerialNumber(): String {
        var serial = defaults.stringForKey("serial_number")
        if (serial == null || serial.isBlank()) {
            serial = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "unknown-ios-device"
            defaults.setObject(serial, forKey = "serial_number")
        }
        return serial
    }

    override fun getOfflineQueueJson(): String? {
        return defaults.stringForKey("offline_queue")
    }

    override fun saveOfflineQueueJson(json: String) {
        defaults.setObject(json, forKey = "offline_queue")
    }

    override fun getFarmId(): String? {
        return defaults.stringForKey("farm_id")
    }

    override fun setFarmId(farmId: String?) {
        if (farmId == null) {
            defaults.removeObjectForKey("farm_id")
        } else {
            defaults.setObject(farmId, forKey = "farm_id")
        }
    }

    override fun getSupabaseJwt(): String? {
        return defaults.stringForKey("supabase_jwt")
    }

    override fun setSupabaseJwt(jwt: String?) {
        if (jwt == null) {
            defaults.removeObjectForKey("supabase_jwt")
        } else {
            defaults.setObject(jwt, forKey = "supabase_jwt")
        }
    }

    override fun getDeviceLabel(): String {
        return defaults.stringForKey("device_label") ?: "iPhone"
    }

    override fun getAppVersion(): String {
        return "1.0.0" // Or read from NSBundle
    }
}
