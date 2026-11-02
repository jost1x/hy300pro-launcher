/*
 * Adapted from Free My TV's local ADB client.
 * Copyright (c) 2026 Free My TV contributors.
 * Licensed under the MIT License; see THIRD_PARTY_NOTICES-FREE-MY-TV.txt.
 */
package io.jostx.hy300launcher.data

import android.content.Context
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/** ADB client for the projector's own local ADB server. */
class Adb private constructor(private val socket: Socket) : Closeable {
    private val input = DataInputStream(socket.getInputStream())
    private val output: OutputStream = socket.getOutputStream()
    private var nextLocalId = 1

    fun shell(command: String): String {
        val local = nextLocalId++
        send(OPEN, local, 0, ("shell:$command\u0000").toByteArray(StandardCharsets.UTF_8))
        var remote = 0
        val result = ByteArrayOutputStream()
        while (true) {
            val message = read()
            if (message.arg1 != local) continue
            when (message.command) {
                OKAY -> remote = message.arg0
                WRTE -> {
                    result.write(message.data)
                    send(OKAY, local, message.arg0, null)
                }
                CLSE -> {
                    if (remote == 0 && message.arg0 == 0) throw IOException("ADB refused the command")
                    send(CLSE, local, message.arg0, null)
                    return result.toString("UTF-8")
                }
            }
        }
    }

    override fun close() {
        try { socket.close() } catch (_: IOException) {}
    }

    private fun read(): Message {
        val header = ByteArray(24)
        input.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val message = Message(
            command = buffer.int,
            arg0 = buffer.int,
            arg1 = buffer.int,
            data = ByteArray(0),
        )
        val length = buffer.int
        buffer.int // checksum (newer adbd doesn't check it)
        val magic = buffer.int
        if ((message.command xor -1) != magic) throw IOException("invalid ADB message")
        if (length < 0 || length > 1024 * 1024) throw IOException("ADB message too large")
        message.data = ByteArray(length)
        input.readFully(message.data)
        return message
    }

    private fun send(command: Int, arg0: Int, arg1: Int, data: ByteArray?) {
        val length = data?.size ?: 0
        var sum = 0
        data?.forEach { byte -> sum += byte.toInt() and 0xff }
        val buffer = ByteBuffer.allocate(24 + length).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command).putInt(arg0).putInt(arg1).putInt(length).putInt(sum).putInt(command xor -1)
        if (data != null) buffer.put(data)
        output.write(buffer.array())
        output.flush()
    }

    private data class Message(val command: Int, val arg0: Int, val arg1: Int, var data: ByteArray)

    companion object {
        private const val CNXN = 0x4e584e43
        private const val AUTH = 0x48545541
        private const val OPEN = 0x4e45504f
        private const val OKAY = 0x59414b4f
        private const val CLSE = 0x45534c43
        private const val WRTE = 0x45545257
        private const val STLS = 0x534c5453
        private const val VERSION = 0x01000001
        private const val MAX_DATA = 256 * 1024
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3
        private val SHA1_PREFIX = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
        )

        fun interface Approval { fun waiting() }

        /** Connects and authenticates, waiting for the TV's first-time approval when needed. */
        @JvmStatic @Throws(IOException::class)
        fun connect(context: Context, approvalMs: Int, approval: Approval?): Adb {
            val keys = keys(context)
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress("127.0.0.1", 5555), 3000)
            } catch (_: IOException) {
                socket.close()
                throw IOException("debugging is off on the TV (port 5555 closed)")
            }
            socket.soTimeout = 10_000
            val adb = Adb(socket)
            try {
                adb.send(CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray(StandardCharsets.US_ASCII))
                var signed = false
                var offeredKey = false
                while (true) {
                    val message = try {
                        adb.read()
                    } catch (_: SocketTimeoutException) {
                        throw IOException(if (offeredKey) "debugging permission wasn't accepted on the TV" else "the TV's ADB didn't respond")
                    }
                    if (message.command == CNXN) {
                        socket.soTimeout = 20_000
                        return adb
                    }
                    if (message.command == STLS) throw IOException("the TV asked for TLS (wireless debugging); use USB debugging")
                    if (message.command != AUTH || message.arg0 != AUTH_TOKEN) throw IOException("unexpected reply from ADB")
                    if (!signed) {
                        adb.send(AUTH, AUTH_SIGNATURE, 0, sign(keys.private, message.data))
                        signed = true
                    } else if (!offeredKey) {
                        adb.send(AUTH, AUTH_RSAPUBLICKEY, 0, publicKeyBlob(keys.public))
                        offeredKey = true
                        approval?.waiting()
                        socket.soTimeout = approvalMs
                    } else {
                        throw IOException("the TV rejected the app's key")
                    }
                }
            } catch (error: IOException) {
                adb.close()
                throw error
            }
        }

        @Throws(IOException::class)
        private fun keys(context: Context): KeyPair {
            val dir = context.filesDir
            val privateFile = File(dir, "adbkey")
            val publicFile = File(dir, "adbkey.pub")
            try {
                val factory = KeyFactory.getInstance("RSA")
                if (privateFile.exists() && publicFile.exists()) {
                    try {
                        return KeyPair(
                            factory.generatePublic(X509EncodedKeySpec(readFile(publicFile))),
                            factory.generatePrivate(PKCS8EncodedKeySpec(readFile(privateFile))),
                        )
                    } catch (_: GeneralSecurityException) {
                        // Replace an unreadable key with a fresh app-private key.
                    } catch (_: IOException) {
                        // Replace an unreadable key with a fresh app-private key.
                    }
                }
                val generator = KeyPairGenerator.getInstance("RSA")
                generator.initialize(2048)
                val pair = generator.generateKeyPair()
                writeFile(privateFile, pair.private.encoded)
                writeFile(publicFile, pair.public.encoded)
                return pair
            } catch (error: GeneralSecurityException) {
                throw IOException("ADB key: ${error.message}")
            }
        }

        private fun sign(key: PrivateKey, token: ByteArray): ByteArray {
            try {
                val block = ByteArray(256)
                block[1] = 1
                val separator = block.size - SHA1_PREFIX.size - token.size - 1
                for (i in 2 until separator) block[i] = 0xff.toByte()
                System.arraycopy(SHA1_PREFIX, 0, block, separator + 1, SHA1_PREFIX.size)
                System.arraycopy(token, 0, block, separator + 1 + SHA1_PREFIX.size, token.size)
                val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                return cipher.doFinal(block)
            } catch (error: GeneralSecurityException) {
                throw IOException("ADB signature: ${error.message}")
            }
        }

        /** Public key in adbd's RSAPublicKey structure, Base64 encoded with its identity. */
        private fun publicKeyBlob(key: PublicKey): ByteArray {
            val rsa = key as RSAPublicKey
            val modulus = rsa.modulus
            val r32 = BigInteger.ONE.shiftLeft(32)
            val n0inv = r32.subtract(modulus.mod(r32).modInverse(r32))
            val rr = BigInteger.ONE.shiftLeft(4096).mod(modulus)
            val buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(64).putInt(n0inv.toInt())
            buffer.put(littleEndian(modulus, 256))
            buffer.put(littleEndian(rr, 256))
            buffer.putInt(rsa.publicExponent.toInt())
            val text = Base64.encodeToString(buffer.array(), Base64.NO_WRAP) + " free-my-tv@tv\u0000"
            return text.toByteArray(StandardCharsets.US_ASCII)
        }

        private fun littleEndian(value: BigInteger, size: Int): ByteArray {
            val big = value.toByteArray()
            val little = ByteArray(size)
            for (i in 0 until minOf(size, big.size)) little[i] = big[big.size - 1 - i]
            return little
        }

        @Throws(IOException::class)
        private fun readFile(file: File): ByteArray = FileInputStream(file).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }

        /** Writes to a sibling temp file, syncs it, and renames only after a complete write. */
        @Throws(IOException::class)
        private fun writeFile(file: File, data: ByteArray) {
            val tmp = File(file.path + ".tmp")
            FileOutputStream(tmp).use { output ->
                output.write(data)
                output.fd.sync()
            }
            if (!tmp.renameTo(file)) throw IOException("could not save ${file.name}")
        }
    }
}
