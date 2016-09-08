package com.example.demo;

import java.io.IOException;
import java.io.StringWriter;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.demo.Wall.Point;
import com.example.demo.Wall.Update;
import com.example.demo.Wall.WallItem;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.html.types.SafeHtmls;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class DemoTest extends TestCase {

  private static final WallItem INNOCUOUS_WALL_ITEM = WallItem.newBuilder()
      .setCentroid(
          Point.newBuilder()
              .setXPercent(50)
              .setYPercent(50)
              .build())
      .setHtmlUntrusted("Hello")
      .setHtml(SafeHtmls.toProto(
          SafeHtmls.htmlEscape("Hello")))
      .build();

  private static final Pattern VARIANT_JS_PATTERN = Pattern.compile(
      "src=\"/js/com.example.wall.item.[\\w.]+.js\"");

  private static String blurVariantJs(String html) {
    Matcher m = VARIANT_JS_PATTERN.matcher(html);
    if (m.find()) {
      return html.substring(0, m.start()) + VARIANT_JS_PATTERN.pattern()
          + html.substring(m.end());
    }
    return html;
  }

  public static void testAllVariantsProduceSameHtmlForSimpleWall()
  throws IOException {
    final DemoVariant goldenVariant = DemoVariant.FIXED;
    String goldenHtml = blurVariantJs(
        render(goldenVariant, INNOCUOUS_WALL_ITEM));

    for (DemoVariant variant : DemoVariant.values()) {
      if (variant == goldenVariant) { continue; }
      String html = blurVariantJs(render(variant, INNOCUOUS_WALL_ITEM));
      assertEquals(variant.name(), goldenHtml, html);
    }
  }

  private static final WallItem PAYLOAD_WALL_ITEM = WallItem.newBuilder()
      .setCentroid(
          Point.newBuilder()
              .setXPercent(25)
              .setYPercent(25)
              .build())
      .setHtmlUntrusted("START<img src=bogus onerror=alert(1)>END")
      .build();


  public static void testPayloadAgainstInsecure() throws IOException {
    String html = render(DemoVariant.INSECURE, PAYLOAD_WALL_ITEM);
    int start = html.indexOf("START");
    int end = html.indexOf("END");
    assertTrue(html, start >= 0 && end >= start);

    assertEquals(
        html,
        "<img src=bogus onerror=alert(1)>",
        html.substring(start + "START".length(), end));
  }

  public static void testPayloadAgainstOverEscaping() throws IOException {
    String html = render(DemoVariant.OVER_ESCAPING, PAYLOAD_WALL_ITEM);
    int start = html.indexOf("START");
    int end = html.indexOf("END");
    assertTrue(html, start >= 0 && end >= start);

    assertEquals(
        html,
        "&lt;img src=bogus onerror=alert(1)&gt;",
        html.substring(start + "START".length(), end));
  }

  public static void testPayloadAgainstFixed() throws IOException {
    String html = render(DemoVariant.FIXED, PAYLOAD_WALL_ITEM);
    int start = html.indexOf("START");
    int end = html.indexOf("END");
    assertTrue(html, start >= 0 && end >= start);

    assertEquals(
        html,
        "",  // <img> was stripped by sanitizer but START/END were not.
        html.substring(start + "START".length(), end));
  }


  private static String render(DemoVariant variant, WallItem item)
  throws IOException {
    Demo d = new Demo(variant) {
      @Override
      protected SecureRandom makeNonceGenerator() {
        try {
          SecureRandom deterministic = SecureRandom.getInstance("SHA1PRNG");
          deterministic.setSeed("Test seed".getBytes(Charsets.US_ASCII));
          return deterministic;
        } catch (NoSuchAlgorithmException ex) {
          throw new AssertionError("constant algo", ex);
        }
      }
    };
    Nonce nonce = d.createNewWall();
    Target target = Target.create("/" + nonce.text + "/wall");
    Optional<Update> withWallItem = d.postItem(target, item);
    assertTrue(withWallItem.isPresent());

    StringWriter sw = new StringWriter();
    d.renderWall(target, withWallItem.get(), sw);
    return sw.toString();
  }
}
