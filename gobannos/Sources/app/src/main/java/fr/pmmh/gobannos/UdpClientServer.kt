package fr.pmmh.gobannos

import java.io.Closeable
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.nio.ByteBuffer

class UdpServer(port: Int, timeoutMs: Int) : Closeable {
    private var udpSocket: DatagramSocket = DatagramSocket(port)

    init {
        try {
            udpSocket.soTimeout = timeoutMs
        } catch (e: SocketException) {
            e.printStackTrace()
        }
    }

    data class AnswerAndSource (val answer: Int, val address: InetAddress)

    fun receiveAsInt(): AnswerAndSource {
        val data = ByteArray(4)
        val packet = DatagramPacket(data, data.size)
        try {
            udpSocket.receive(packet)
        } catch (e: IOException) {
            e.printStackTrace()
            return AnswerAndSource(0, InetAddress.getLocalHost())
        }
        return AnswerAndSource(bytesToInt(data), packet.address)
    }

    private fun bytesToInt(data: ByteArray): Int {
        try {
            val bb = ByteBuffer.wrap(data)
            return bb.getInt()
        } catch (e: Exception) {
            return -1;
        }
    }

    override fun close() {
        udpSocket.close()
    }
}

class UdpClient(private val port: Int, timeoutMs: Int) : Closeable {
    private val udpSocket: DatagramSocket = DatagramSocket(port)

    init {
        try {
            udpSocket.soTimeout = timeoutMs
        } catch (e: SocketException) {
            e.printStackTrace()
        }
    }

    private fun longToBytes(num: Long): ByteArray {
        val buffer = ByteBuffer.allocate(java.lang.Long.BYTES)
        buffer.putLong(num)
        return buffer.array()
    }

    fun send(num: Long, address: InetAddress): Boolean {
        val data = longToBytes(num)
        return send(data, address)
    }

    private fun send(data: ByteArray, address: InetAddress): Boolean {
        val packet = DatagramPacket(data, data.size, address, this.port)
        try {
            udpSocket.send(packet)
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
        return true
    }

    override fun close() {
        udpSocket.close()
    }
}