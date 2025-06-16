package com.cryptalink.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class LSBSteganography {
    private static final Logger logger = LoggerFactory.getLogger(LSBSteganography.class);
    private static final Random random = new Random();

    public static void hideMessage(String bmpFile, String message) {
        try {
            File file = new File(bmpFile);
            BufferedImage image = ImageIO.read(file);
            
            if (image == null) {
                logger.error("无法读取BMP文件");
                return;
            }

            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            int messageLength = messageBytes.length;
            
            // 检查图像容量是否足够
            int maxCapacity = (image.getWidth() * image.getHeight() * 3) / 8;
            if (messageLength > maxCapacity) {
                logger.error("消息太长，无法隐藏在图像中");
                return;
            }

            // 先隐藏消息长度（4字节）
            hideInt(image, 0, 0, messageLength);

            // 随机选择位置隐藏消息
            int[] positions = generateRandomPositions(messageLength, maxCapacity);
            for (int i = 0; i < messageLength; i++) {
                int pos = positions[i];
                int x = pos % image.getWidth();
                int y = pos / image.getWidth();
                hideByte(image, x, y, messageBytes[i]);
            }

            // 保存修改后的图像
            ImageIO.write(image, "bmp", file);
            logger.info("消息已成功隐藏在图像中");
        } catch (IOException e) {
            logger.error("隐写过程发生错误: ", e);
        }
    }

    public static String extractMessage(String bmpFile) {
        try {
            File file = new File(bmpFile);
            BufferedImage image = ImageIO.read(file);
            
            if (image == null) {
                logger.error("无法读取BMP文件");
                return null;
            }

            // 提取消息长度
            int messageLength = extractInt(image, 0, 0);
            
            // 计算最大容量
            int maxCapacity = (image.getWidth() * image.getHeight() * 3) / 8;
            if (messageLength > maxCapacity || messageLength <= 0) {
                logger.error("无效的消息长度");
                return null;
            }

            // 使用相同的随机位置提取消息
            int[] positions = generateRandomPositions(messageLength, maxCapacity);
            byte[] messageBytes = new byte[messageLength];
            for (int i = 0; i < messageLength; i++) {
                int pos = positions[i];
                int x = pos % image.getWidth();
                int y = pos / image.getWidth();
                messageBytes[i] = extractByte(image, x, y);
            }

            String message = new String(messageBytes, StandardCharsets.UTF_8);
            logger.info("成功提取隐藏消息");
            return message;
        } catch (IOException e) {
            logger.error("提取消息时发生错误: ", e);
            return null;
        }
    }

    private static void hideInt(BufferedImage image, int x, int y, int value) {
        for (int i = 0; i < 32; i++) {
            int bit = (value >> i) & 1;
            int pixel = image.getRGB(x + (i / 3), y);
            int component = (i % 3);
            
            // 修改对应颜色分量的最低位
            pixel = modifyLSB(pixel, component, bit);
            image.setRGB(x + (i / 3), y, pixel);
        }
    }

    private static int extractInt(BufferedImage image, int x, int y) {
        int value = 0;
        for (int i = 0; i < 32; i++) {
            int pixel = image.getRGB(x + (i / 3), y);
            int component = (i % 3);
            int bit = extractLSB(pixel, component);
            value |= (bit << i);
        }
        return value;
    }

    private static void hideByte(BufferedImage image, int x, int y, byte value) {
        for (int i = 0; i < 8; i++) {
            int bit = (value >> i) & 1;
            int pixel = image.getRGB(x, y + (i / 3));
            int component = (i % 3);
            pixel = modifyLSB(pixel, component, bit);
            image.setRGB(x, y + (i / 3), pixel);
        }
    }

    private static byte extractByte(BufferedImage image, int x, int y) {
        byte value = 0;
        for (int i = 0; i < 8; i++) {
            int pixel = image.getRGB(x, y + (i / 3));
            int component = (i % 3);
            int bit = extractLSB(pixel, component);
            value |= (bit << i);
        }
        return value;
    }

    private static int modifyLSB(int pixel, int component, int bit) {
        int[] rgb = new int[3];
        rgb[0] = (pixel >> 16) & 0xff; // Red
        rgb[1] = (pixel >> 8) & 0xff;  // Green
        rgb[2] = pixel & 0xff;         // Blue

        // 修改指定颜色分量的最低位
        rgb[component] = (rgb[component] & 0xfe) | bit;

        return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    private static int extractLSB(int pixel, int component) {
        int[] rgb = new int[3];
        rgb[0] = (pixel >> 16) & 0xff; // Red
        rgb[1] = (pixel >> 8) & 0xff;  // Green
        rgb[2] = pixel & 0xff;         // Blue

        return rgb[component] & 1;
    }

    private static int[] generateRandomPositions(int count, int maxPosition) {
        int[] positions = new int[count];
        boolean[] used = new boolean[maxPosition];
        
        for (int i = 0; i < count; i++) {
            int pos;
            do {
                pos = random.nextInt(maxPosition);
            } while (used[pos]);
            
            positions[i] = pos;
            used[pos] = true;
        }
        
        return positions;
    }
} 