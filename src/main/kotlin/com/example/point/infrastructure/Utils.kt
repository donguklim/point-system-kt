package com.example.point.infrastructure

import java.net.InetAddress

fun getIpAddressByHostname(hostname: String): String {
    return try {
        val address = InetAddress.getByName(hostname)
        address.hostAddress
    } catch (e: Exception) {
        "Unable to resolve IP address for hostname: $hostname"
    }
}