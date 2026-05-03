package com.weaversworkshop.playerdisguise.skin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SkinValidator {
    public static final int MAX_BYTES = 64 * 1024;

    private SkinValidator() {}

    public sealed interface Result {
        record Ok(byte[] bytes, String sha256, int width, int height) implements Result {}
        record Err(String message) implements Result {}
    }

    public static Result validate(Path file) {
        try {
            return validate(Files.readAllBytes(file));
        } catch (Exception e) {
            return new Result.Err("Could not read file: " + e.getMessage());
        }
    }

    public static Result validate(byte[] bytes) {
        if (bytes.length > MAX_BYTES) {
            return new Result.Err("Skin too large: " + bytes.length + " bytes (max " + MAX_BYTES + ")");
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return new Result.Err("Not a valid PNG: " + e.getMessage());
        }
        if (img == null) {
            return new Result.Err("Not a valid PNG image");
        }
        int w = img.getWidth();
        int h = img.getHeight();
        boolean dimsOk = (w == 64 && h == 64) || (w == 64 && h == 32);
        if (!dimsOk) {
            return new Result.Err("Skin must be 64x64 or 64x32 (got " + w + "x" + h + ")");
        }
        return new Result.Ok(bytes, sha256(bytes), w, h);
    }

    public static String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
