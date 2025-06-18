package com.cryptalink.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Random;
import java.security.SecureRandom;
import java.util.zip.CRC32;

public class LSBSteganography {
    private static final Logger logger = LoggerFactory.getLogger(LSBSteganography.class);
    private static final String MAGIC_HEADER = "CL"; // 2字节魔数，用于验证
    private static final int HEADER_LENGTH = 6; // 2字节魔数 + 4字节长度
    private static final byte FRAME_START = (byte)0xAA;  // 帧起始标记
    private static final byte FRAME_END = (byte)0x55;    // 帧结束标记
    private static final long SERVER_SEED = 12345L; // 服务器固定种子

    private static byte[] frameMessage(byte[] data) {
        // 计算CRC32校验和
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        long crc = crc32.getValue();
        
        // 创建帧：起始标记(1) + 长度(4) + 数据(n) + CRC32(4) + 结束标记(1)
        ByteBuffer frame = ByteBuffer.allocate(data.length + 10);
        frame.put(FRAME_START);
        frame.putInt(data.length);  // 使用4字节存储长度
        frame.put(data);
        frame.putInt((int)crc);     // 存储4字节CRC32值
        frame.put(FRAME_END);
        return frame.array();
    }

    private static byte[] unframeMessage(byte[] framedData) {
        ByteBuffer buffer = ByteBuffer.wrap(framedData);
        
        // 验证起始标记
        if (buffer.get() != FRAME_START) {
            logger.error("无效的帧起始标记");
            return null;
        }
        
        // 读取数据长度
        int length = buffer.getInt();  // 读取4字节长度
        if (length < 0 || length > framedData.length - 10) {  // 10是帧开销
            logger.error("无效的数据长度: {}", length);
            return null;
        }
        
        // 读取数据
        byte[] data = new byte[length];
        buffer.get(data);
        
        // 计算并验证CRC32
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        long calculatedCrc = crc32.getValue();
        
        int receivedCrc = buffer.getInt();  // 读取4字节CRC32
        if (receivedCrc != calculatedCrc) {
            logger.error("CRC32校验失败: 期望={}, 实际={}", calculatedCrc, receivedCrc);
            return null;
        }
        
        // 验证结束标记
        if (buffer.get() != FRAME_END) {
            logger.error("无效的帧结束标记");
            return null;
        }
        
        return data;
    }

    public static void hideMessage(String bmpFile, String message, long seed) {
        try {
            // 读取原始图像
            File file = new File(bmpFile);
            BufferedImage originalImage = ImageIO.read(file);
            if (originalImage == null) {
                logger.error("无法读取BMP文件");
                return;
            }
            
            logger.info("开始处理图像 - 宽度: {}, 高度: {}, 类型: {}", 
                originalImage.getWidth(), originalImage.getHeight(), originalImage.getType());

            // 创建图像副本用于第一次写入
            BufferedImage image1 = new BufferedImage(
                originalImage.getWidth(),
                originalImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
            );
            for (int x = 0; x < originalImage.getWidth(); x++) {
                for (int y = 0; y < originalImage.getHeight(); y++) {
                    image1.setRGB(x, y, originalImage.getRGB(x, y));
                }
            }

            // 第一次写入：用户消息
            hideMessageWithSeed(image1, message, seed);
            ImageIO.write(image1, "bmp", file);
            logger.info("第一次写入完成：用户消息");

            // 验证第一次写入
            String userMessage = extractMessage(bmpFile, seed);
            if (message.equals(userMessage)) {
                logger.info("用户消息验证成功");
            } else {
                logger.warn("用户消息验证失败 - 期望: {}, 实际: {}", message, userMessage);
            }

            // 创建图像副本用于第二次写入
            BufferedImage image2 = new BufferedImage(
                originalImage.getWidth(),
                originalImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
            );
            for (int x = 0; x < originalImage.getWidth(); x++) {
                for (int y = 0; y < originalImage.getHeight(); y++) {
                    image2.setRGB(x, y, image1.getRGB(x, y));
                }
            }

            // 第二次写入：检测标记
            hideMessageWithSeed(image2, "STEG_DETECTED", SERVER_SEED);
            ImageIO.write(image2, "bmp", file);
            logger.info("第二次写入完成：检测标记");

            // 验证写入
            String testMessage = extractMessage(bmpFile, SERVER_SEED);
            if ("STEG_DETECTED".equals(testMessage)) {
                logger.info("验证成功：检测标记可以正确读取");
            } else {
                logger.warn("验证失败：无法读取检测标记，读取到的内容: {}", testMessage);
            }
        } catch (IOException e) {
            logger.error("隐写过程发生错误: ", e);
        }
    }

    private static void hideMessageWithSeed(BufferedImage image, String message, long seed) throws IOException {
        // 将消息转换为字节数组并添加帧结构
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] framedMessage = frameMessage(messageBytes);
        int messageLength = framedMessage.length;
        
        logger.debug("原始消息长度: {}, 帧化后长度: {}", messageBytes.length, messageLength);

        // 检查图像容量是否足够
        int maxCapacity = (image.getWidth() * image.getHeight() * 3) / 8;
        if (messageLength > maxCapacity - HEADER_LENGTH) {
            logger.error("消息太长，无法隐藏在图像中");
            return;
        }

        Random random = new Random(seed);
        
        // 写入魔数和消息长度
        writeHeader(image, messageLength);

        // 使用伪随机数生成器分散存储消息位
        int maxBits = (image.getWidth() * image.getHeight() * 3) - (HEADER_LENGTH * 8);
        boolean[] usedBits = new boolean[maxBits];
        
        for (int byteIndex = 0; byteIndex < messageLength; byteIndex++) {
            int currentByte = framedMessage[byteIndex] & 0xFF;  // 确保是无符号字节
            for (int bitIndex = 7; bitIndex >= 0; bitIndex--) {  // 从最高位开始
                int bit = (currentByte >> bitIndex) & 1;
                
                // 找到一个未使用的随机位置
                int position;
                do {
                    position = random.nextInt(maxBits);
                } while (usedBits[position]);
                
                usedBits[position] = true;
                
                // 计算像素位置和颜色分量
                int pixelIndex = position / 3;
                int x = pixelIndex % image.getWidth();
                int y = pixelIndex / image.getWidth();
                int component = position % 3;
                
                // 修改像素
                int pixel = image.getRGB(x, y);
                pixel = modifyLSB(pixel, component, bit);
                image.setRGB(x, y, pixel);
            }
        }
    }

    public static String extractMessage(String bmpFile, long seed) {
        try {
            File file = new File(bmpFile);
            BufferedImage image = ImageIO.read(file);

            if (image == null) {
                logger.error("无法读取BMP文件");
                return null;
            }

            // 验证魔数和获取消息长度
            int[] headerInfo = readHeader(image);
            if (headerInfo == null) {
                logger.error("无效的文件格式或未找到隐写信息");
                return null;
            }
            int messageLength = headerInfo[0];

            // 验证消息长度
            int maxCapacity = (image.getWidth() * image.getHeight() * 3) / 8;
            if (messageLength <= 0 || messageLength > maxCapacity - HEADER_LENGTH) {
                logger.error("无效的消息长度: {}", messageLength);
                return null;
            }

            Random random = new Random(seed);
            byte[] framedMessage = new byte[messageLength];
            int maxBits = (image.getWidth() * image.getHeight() * 3) - (HEADER_LENGTH * 8);
            boolean[] usedBits = new boolean[maxBits];

            // 按照相同的随机顺序提取消息位
            for (int byteIndex = 0; byteIndex < messageLength; byteIndex++) {
                int currentByte = 0;
                for (int bitIndex = 7; bitIndex >= 0; bitIndex--) {  // 从最高位开始
                    int position;
                    do {
                        position = random.nextInt(maxBits);
                    } while (usedBits[position]);
                    
                    usedBits[position] = true;
                    
                    int pixelIndex = position / 3;
                    int x = pixelIndex % image.getWidth();
                    int y = pixelIndex / image.getWidth();
                    int component = position % 3;
                    
                    int pixel = image.getRGB(x, y);
                    int bit = extractLSB(pixel, component);
                    if (bit == 1) {
                        currentByte |= (1 << bitIndex);  // 设置对应位
                    }
                }
                framedMessage[byteIndex] = (byte)currentByte;
            }

            // 解析帧并提取原始消息
            byte[] messageBytes = unframeMessage(framedMessage);
            if (messageBytes == null) {
                logger.error("消息帧解析失败");
                return null;
            }

            String message = new String(messageBytes, StandardCharsets.UTF_8);
            logger.debug("成功提取隐藏消息，帧长度: {}, 实际内容长度: {}, 内容: {}", 
                messageLength, message.length(), message);
            return message;
        } catch (IOException e) {
            logger.error("提取消息时发生错误: ", e);
            return null;
        } catch (Exception e) {
            logger.error("隐写检测过程中发生未知错误: ", e);
            return null;
        }
    }

    public static boolean hasSteg(String bmpFile) {
        String message = extractMessage(bmpFile, SERVER_SEED);
        return message != null && message.equals("STEG_DETECTED");
    }

    private static void writeHeader(BufferedImage image, int messageLength) {
        // 写入魔数 "CL"
        int x = 0, y = 0;
        int pixel = image.getRGB(x, y);
        
        // 写入C的最低两位到红色通道的最低两位
        int c = 'C' & 0xFF;
        pixel = modifyLSB(pixel, 0, (c >> 0) & 1);  // 最低位
        pixel = modifyLSB(pixel, 0, (c >> 1) & 1);  // 次低位
        
        // 写入L的最低两位到绿色通道的最低两位
        int l = 'L' & 0xFF;
        pixel = modifyLSB(pixel, 1, (l >> 0) & 1);  // 最低位
        pixel = modifyLSB(pixel, 1, (l >> 1) & 1);  // 次低位
        
        image.setRGB(x, y, pixel);
        
        // 验证魔数写入
        int testPixel = image.getRGB(0, 0);
        int testC = (extractLSB(testPixel, 0) | (extractLSB(testPixel, 0) << 1));
        int testL = (extractLSB(testPixel, 1) | (extractLSB(testPixel, 1) << 1));
        logger.debug("魔数写入验证 - C低2位: {}, L低2位: {}", 
            testC, testL);

        // 写入消息长度（4字节）
        for (int i = 31; i >= 0; i--) {  // 从最高位开始写入
            int bit = (messageLength >> i) & 1;
            x = (i + 16) / 24;  // 16是因为前面用了2字节存魔数
            y = ((i + 16) % 24) / 3;
            int component = (i + 16) % 3;
            
            pixel = image.getRGB(x, y);
            pixel = modifyLSB(pixel, component, bit);
            image.setRGB(x, y, pixel);
        }
        logger.debug("写入消息长度: {}", messageLength);
    }

    private static int[] readHeader(BufferedImage image) {
        // 读取并验证魔数
        int x = 0, y = 0;
        int pixel = image.getRGB(x, y);
        
        // 从红色和绿色通道读取魔数位
        int c = (extractLSB(pixel, 0) | (extractLSB(pixel, 0) << 1));
        int l = (extractLSB(pixel, 1) | (extractLSB(pixel, 1) << 1));
        
        logger.debug("读取到的魔数位 - C低2位: {}, L低2位: {}", c, l);
        logger.debug("期望的魔数位 - C低2位: {}, L低2位: {}", 
            ('C' & 0x03), ('L' & 0x03));
        
        // 验证魔数的最低两位
        if (c != ('C' & 0x03) || l != ('L' & 0x03)) {
            logger.error("魔数验证失败 - 读取值: C低2位={}, L低2位={}, 期望值: C低2位={}, L低2位={}", 
                c, l, 'C' & 0x03, 'L' & 0x03);
            return null;
        }

        // 读取消息长度
        int messageLength = 0;
        for (int i = 31; i >= 0; i--) {  // 从最高位开始读取
            x = (i + 16) / 24;
            y = ((i + 16) % 24) / 3;
            int component = (i + 16) % 3;
            
            pixel = image.getRGB(x, y);
            int bit = extractLSB(pixel, component);
            if (bit == 1) {
                messageLength |= (1 << i);
            }
        }
        logger.debug("读取到的消息长度: {}", messageLength);

        return new int[]{messageLength};
    }

    private static int modifyLSB(int pixel, int component, int bit) {
        int[] rgb = new int[3];
        rgb[0] = (pixel >> 16) & 0xff; // Red
        rgb[1] = (pixel >> 8) & 0xff;  // Green
        rgb[2] = pixel & 0xff;         // Blue

        // 修改指定颜色分量的最低位
        rgb[component] = (rgb[component] & 0xfe) | (bit & 1);

        return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    private static int extractLSB(int pixel, int component) {
        int[] rgb = new int[3];
        rgb[0] = (pixel >> 16) & 0xff; // Red
        rgb[1] = (pixel >> 8) & 0xff;  // Green
        rgb[2] = pixel & 0xff;         // Blue

        return rgb[component] & 1;
    }

    public static long generateRandomSeed() {
        return new SecureRandom().nextLong();
    }
}