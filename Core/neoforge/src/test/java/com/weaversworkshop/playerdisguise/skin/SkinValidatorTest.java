package com.weaversworkshop.playerdisguise.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinValidatorTest {

    private static byte[] pngBytes(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", out);
        return out.toByteArray();
    }

    @Test
    void validate_64x64_ok() throws Exception {
        byte[] bytes = pngBytes(64, 64);
        SkinValidator.Result r = SkinValidator.validate(bytes);
        SkinValidator.Result.Ok ok = assertInstanceOf(SkinValidator.Result.Ok.class, r);
        assertEquals(64, ok.width());
        assertEquals(64, ok.height());
        assertNotNull(ok.sha256());
        assertEquals(64, ok.sha256().length()); // 32 bytes hex
        assertEquals(bytes.length, ok.bytes().length);
    }

    @Test
    void validate_64x32_legacyOk() throws Exception {
        SkinValidator.Result r = SkinValidator.validate(pngBytes(64, 32));
        assertInstanceOf(SkinValidator.Result.Ok.class, r);
    }

    @Test
    void validate_wrongDimensions_err() throws Exception {
        SkinValidator.Result r32 = SkinValidator.validate(pngBytes(32, 32));
        SkinValidator.Result r128 = SkinValidator.validate(pngBytes(128, 128));
        SkinValidator.Result rTall = SkinValidator.validate(pngBytes(64, 128));

        assertInstanceOf(SkinValidator.Result.Err.class, r32);
        assertInstanceOf(SkinValidator.Result.Err.class, r128);
        assertInstanceOf(SkinValidator.Result.Err.class, rTall);
        SkinValidator.Result.Err err = (SkinValidator.Result.Err) r32;
        assertTrue(err.message().contains("64x64"));
    }

    @Test
    void validate_notAPng_err() {
        SkinValidator.Result r = SkinValidator.validate("not a png".getBytes());
        assertInstanceOf(SkinValidator.Result.Err.class, r);
    }

    @Test
    void validate_oversize_err() {
        byte[] huge = new byte[SkinValidator.MAX_BYTES + 1];
        SkinValidator.Result r = SkinValidator.validate(huge);
        SkinValidator.Result.Err err = assertInstanceOf(SkinValidator.Result.Err.class, r);
        assertTrue(err.message().toLowerCase().contains("too large"));
    }

    @Test
    void validate_path_overload(@TempDir Path tmp) throws Exception {
        Path p = tmp.resolve("skin.png");
        Files.write(p, pngBytes(64, 64));
        SkinValidator.Result r = SkinValidator.validate(p);
        assertInstanceOf(SkinValidator.Result.Ok.class, r);
    }

    @Test
    void validate_path_missing_err(@TempDir Path tmp) {
        SkinValidator.Result r = SkinValidator.validate(tmp.resolve("nope.png"));
        assertInstanceOf(SkinValidator.Result.Err.class, r);
    }

    @Test
    void sha256_isStable_andSensitiveToBytes() throws Exception {
        byte[] a = pngBytes(64, 64);
        // Two independent encodes of the same blank image yield identical bytes (deterministic encoder),
        // so we re-hash the same array twice and verify equality, then mutate and verify divergence.
        String h1 = SkinValidator.sha256(a);
        String h2 = SkinValidator.sha256(a);
        assertEquals(h1, h2);

        byte[] mutated = a.clone();
        mutated[mutated.length - 1] ^= 0x01;
        assertTrue(!h1.equals(SkinValidator.sha256(mutated)));
    }

    @Test
    void sha256_knownVector() {
        // Empty input — well-known SHA-256 of zero-length string
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                SkinValidator.sha256(new byte[0]));
    }
}
