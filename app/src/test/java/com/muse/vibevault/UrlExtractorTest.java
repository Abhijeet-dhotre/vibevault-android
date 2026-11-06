package com.muse.vibevault;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/** Plain JVM unit tests for the share-text URL extractor. */
public class UrlExtractorTest {

    @Test
    public void bareUrl() {
        assertEquals("https://youtu.be/abc123",
                UrlExtractor.extract("https://youtu.be/abc123"));
    }

    @Test
    public void youtubeShareText() {
        assertEquals("https://youtu.be/abc123",
                UrlExtractor.extract("Watch this video! https://youtu.be/abc123 via @YouTube"));
    }

    @Test
    public void trailingPunctuationIsTrimmed() {
        assertEquals("https://example.com/video.mp4",
                UrlExtractor.extract("look at this (https://example.com/video.mp4)."));
    }

    @Test
    public void wwwGetsScheme() {
        assertEquals("https://www.tiktok.com/@user/video/123",
                UrlExtractor.extract("www.tiktok.com/@user/video/123"));
    }

    @Test
    public void noUrlReturnsNull() {
        assertNull(UrlExtractor.extract("just some words, no link"));
        assertNull(UrlExtractor.extract(null));
        assertNull(UrlExtractor.extract(""));
    }

    @Test
    public void isUrlChecksWholeString() {
        assertTrue(UrlExtractor.isUrl("https://vimeo.com/22439234"));
        assertFalse(UrlExtractor.isUrl("see https://vimeo.com/22439234 now"));
    }
}
