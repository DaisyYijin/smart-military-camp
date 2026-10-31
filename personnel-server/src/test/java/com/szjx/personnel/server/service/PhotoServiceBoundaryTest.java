package com.szjx.personnel.server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PhotoServiceBoundaryTest {
    @TempDir Path temp;

    @Test void normalizesInsideRootButRejectsTraversalAbsoluteAndSiblingPrefixPaths() throws Exception {
        PhotoService photos = new PhotoService(temp.toString());
        Path root = temp.resolve("photos").toRealPath();
        photos.save("safe.jpg", new byte[]{1, 2, 3});
        assertEquals(root.resolve("safe.jpg").toFile(), photos.getFile("sub/../safe.jpg"));
        assertEquals(root.resolve("safe.jpg").toFile(), photos.getFile("sub\\..\\safe.jpg"));
        for (String invalid : new String[]{"../secret", "..\\secret", "../photos-other/secret", ".", "sub/..", "", "/etc/passwd", "C:\\secret", "C:secret", "\\\\host\\share\\secret"}) {
            assertThrows(IllegalArgumentException.class, () -> photos.getFile(invalid), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> photos.getFile(null));
        Path outside = temp.resolve("do-not-delete.jpg");
        Files.write(outside, new byte[]{9});
        assertThrows(IllegalArgumentException.class, () -> photos.delete("../do-not-delete.jpg"));
        assertTrue(Files.exists(outside));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(photos.getFile("safe.jpg").toPath()));
    }

    @Test void rejectsSymlinkEscapeWhenPlatformSupportsSymlinks() throws Exception {
        PhotoService photos = new PhotoService(temp.toString());
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Path link = temp.resolve("photos/link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unsupported) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "当前平台不允许测试符号链接: " + unsupported.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> photos.getFile("link/secret.jpg"));
    }
}
