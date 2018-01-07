package com.cunninglogic.dumlflasher;

import com.fazecast.jSerialComm.SerialPort;
import org.apache.commons.net.ftp.FTPClient;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/*
    This file is part of DUMLFlasher.

    Foobar is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Foobar is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Foobar.  If not, see <http://www.gnu.org/licenses/>.

 */

public class Main {

    private static SerialPort activePort = null;
    private static FTPClient ftpClient;

    private static boolean isRC = false;
    private static boolean isGL = false;

    public static void main(String[] args) {

        System.out.println("DUMLFlasher 1.0.2 - by jcase@cunninglogic.com");
        System.out.println("Licensed under GPLV3\n");

        System.out.println("Want to help fund more public research?");
        System.out.println("PayPal Donations - > jcase@cunninglogic.com");
        System.out.println("Bitcoin Donations - > 1LrunXwPpknbgVYcBJyDk6eanxTBYnyRKN");
        System.out.println("Bitcoin Cash Donations - > 1LrunXwPpknbgVYcBJyDk6eanxTBYnyRKN");
        System.out.println("Amazon giftcards, plain thank yous or anything else -> jcase@cunninglogic.com\n");

        if ((args.length != 4) || (!(args[1].equals("AC") || (args[1].equals("RC"))))) {
            printHelp();
            return;
        }

        File payload = new File(args[3]);

        if (!payload.exists() || !payload.canRead()) {
            System.out.println("Can not read " + payload.getAbsolutePath());
            printHelp();
            return;
        }

        System.out.println(args[0] + " Mode");

        if (args[0].equals("RC")) {
            isRC = true;

        } else if (args[0].equals("GL")) {
            isGL = true;
        }

        int count = 1;

        System.out.println("Choose target port: (* suggested port)");
        for (SerialPort s : SerialPort.getCommPorts()) {
            if (s.getDescriptivePortName().contains("DJI")) {
                System.out.print("*");
            }

            System.out.println("\t[" + count + "] " + s.getSystemPortName() + " : " + s.getDescriptivePortName());
            count++;
        }

        System.out.println("\t[E] Exit");

        String str = "";
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.print("Choose port: ");
        while (true) {
            try {
                str = br.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {

                if (str.toLowerCase().toLowerCase().equals("e")) {
                    System.out.println("Exiting");
                    System.exit(0);
                }


                int port = Integer.parseInt(str.trim());

                if ((port > count) || (port < 1)) {
                    System.out.println("[!] Invalid port selection");
                    System.out.print("Choose port: ");
                } else {
                    activePort = SerialPort.getCommPorts()[port - 1];
                    System.out.println("Using Port: " + activePort.getSystemPortName());
                    break;
                }
            } catch (NumberFormatException e) {
                System.out.println("[!] Invalid port selection");
                System.out.print("Choose port: ");
            }
        }

        if (activePort == null) {
            System.out.println("Couldn't find port, exiting");
            return;
        }

        if (activePort.isOpen()) {
            System.out.println(activePort.getSystemPortName() + " is already open");
            activePort.closePort();
            return;
        }

        if (!activePort.openPort()) {
            System.out.println("Couldn't open port, exiting");
            activePort.closePort();
            return;
        }

        activePort.setBaudRate(115200);

        System.out.println("Connected on " + activePort.getSystemPortName());

        System.out.println("Sending upgrade command");
        write(getUpgradePacket());

        //ToDo handling reporting
        System.out.println("Enabling reporting");
       // write(getReportPacket());



        System.out.println("Uploading payload");
        try {
            uploadFile(payload);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Sending FileSize packet");
        write(getFileSizePacket(payload));

        System.out.println("Starting flash");
        write(getHashPacket(payload));

        System.out.println("Exiting....");
        activePort.closePort();
        try {
            ftpClient.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void uploadFile(File payload) throws IOException {
        if (ftpClient == null) {
            ftpClient = new FTPClient();
        }

        if (!ftpClient.isConnected()) {
            ftpClient.connect("192.168.42.2", 21);
            ftpClient.login("nouser","nopass");
            ftpClient.enterLocalPassiveMode();
        }
        ftpClient.setFileType(ftpClient.BINARY_FILE_TYPE);
        InputStream is = Files.newInputStream(Paths.get(payload.getAbsolutePath()));
        boolean done = ftpClient.storeFile("/upgrade/dji_system.bin", is);
        is.close();
        if (!done) {
            System.out.println("Failed to upload payload.");
            System.exit(-1);
        }
    }

    private static void write(byte[] packet) {
        activePort.writeBytes(packet,packet.length);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static byte[] getFileSizePacket(File payload) {
        byte[] packet = new byte[] {0x55, 0x1A, 0x04, (byte)0xB1, 0x2A, 0x28, 0x6B, 0x57, 0x40, 0x00, 0x08, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x04};

        if (isRC) {
            packet = new byte[] {0x55, 0x1A, 0x04, (byte)0xB1, 0x2A, 0x2D, (byte)0xEC, 0x27, 0x40, 0x00, 0x08, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x04};
        } else if (isGL) {
            packet = new byte[] {0x55, 0x1A, 0x04, (byte)0xB1, 0x2A, 0x3C, (byte)0xFD, 0x35, 0x40, 0x00, 0x08, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x04};
        }

        byte[] size = ByteBuffer.allocate(4).putInt((int) payload.length()).array();

        packet[12] = size[3];
        packet[13] = size[2];
        packet[14] = size[1];
        packet[15] = size[0];

        return  CRC.pktCRC(packet);
    }

    private static byte[] getHashPacket(File payload) {
        byte[] packet  = new byte[] {0x55, 0x1E, 0x04, (byte)0x8A, 0x2A, 0x28, (byte)0xF6, 0x57, 0x40, 0x00, 0x0A, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        if (isRC) {
            packet = new byte[] {0x55, 0x1E, 0x04, (byte)0x8A, 0x2A, 0x2D, 0x02, 0x28, 0x40, 0x00, 0x0A, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        } else if (isGL) {
            packet = new byte[] {0x55, 0x1E, 0x04, (byte)0x8A, 0x2A, 0x3C, 0x5B, 0x36, 0x40, 0x00, 0x0A, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        }

        byte[] md5 = new byte[0];
        try {
            md5 = Files.readAllBytes(Paths.get(payload.getAbsolutePath()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md5 = md.digest(md5);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        System.arraycopy(md5,0, packet, 12, 16);

        return  CRC.pktCRC(packet);
    }

    private static byte[] getUpgradePacket() {

        byte[] packet = new byte[] {0x55, 0x16, 0x04, (byte)0xFC, 0x2A, 0x28, 0x65, 0x57, 0x40, 0x00, 0x07, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x27, (byte)0xD3};

        if (isRC) {
            packet = new byte[] {0x55, 0x16, 0x04, (byte)0xFC, 0x2A, 0x2D, (byte)0xE7, 0x27, 0x40, 0x00, 0x07, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte)0x9F, 0x44};
        } else if (isGL) {
            packet = new byte[] {0x55, 0x16, 0x04, (byte)0xFC, 0x2A, 0x3C, (byte)0xF7, 0x35, 0x40, 0x00, 0x07, 0x00,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x29};
        }

        return packet;
    }

    private static byte[] getReportPacket() {

        byte[] packet = new byte[] {0x55, 0x0E, 0x04, 0x66, 0x2A, 0x28, 0x68, 0x57, 0x40, 0x00, 0x0C, 0x00, (byte)0x88,
                0x20};

        if (isRC) {
            packet = new byte[] {0x55, 0x0E, 0x04, 0x66, 0x2A, 0x2D, (byte)0xEA, 0x27, 0x40, 0x00, 0x0C, 0x00, 0x2C,
                    (byte)0xC8};
        } else if (isGL) {
            packet = new byte[] {0x55, 0x0E, 0x04, 0x66, 0x2A, 0x3C, (byte)0xFA, 0x35, 0x40, 0x00, 0x0C, 0x00, 0x48, 0x02};
        }

        return packet;
    }

    private static void printHelp() {
        System.out.println("java -jar DUMLFlasher -t <target> -f <filepath>");
        System.out.println("Targets:");
        System.out.println("\tAC - Aircraft");
        System.out.println("\tRC - Remote Control");
        System.out.println("\tGL - Goggles");
    }
}
