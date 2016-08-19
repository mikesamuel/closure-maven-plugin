package com.example.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import com.example.demo.Wall.WallItem;
import com.example.demo.Wall.WallItems;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrls;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

/**
 * A Jetty server handler that serves /closure/... resources as static files
 * and handles the legal wall demo.
 * <p>
 * {@code /} redirects to {@code /nonce/wall} where {Wcode nonce} is a randomly
 * generated web-safe string.
 * <p>
 * {@code /nonce/wall} is the main HTML page.
 * <p>
 * {@code /nonce/wall.json} is the data backing the wall in JSON format.
 * <p>
 * {@code /nonce/wall.pb} is the data backing the wall in binary protobuf
 * message format.
 * <p>
 * All other paths are resolved against resources in the /closure/... namespace.
 */
public class Demo extends AbstractHandler {

  /** Compute content types for /closure/... resources. */
  static final ImmutableMap<String, String> EXTENSION_TO_CONTENT_TYPE =
      ImmutableMap.of(
          "js",   "text/javascript; charset=utf-8",
          "html", "text/html; charset=utf-8",
          "css",  "text/css; charset=utf-8",
          "ico",  "image/x-icon");

  private final SecureRandom nonceGenerator = new SecureRandom();

  private final Cache<Nonce, WallItems> walls =
      CacheBuilder.newBuilder()
      .maximumSize(1000)
      .expireAfterAccess(10, TimeUnit.MINUTES)
      .<Nonce, WallItems>build();

  @Override
  public void handle(
      String targetPath,
      Request baseRequest,
      HttpServletRequest request,
      HttpServletResponse response)
  throws IOException, ServletException {
    String method = baseRequest.getMethod();

    Target target = Target.create(targetPath);

    boolean handled = false;
    if ("GET".equals(method)) {
      Optional<WallItems> wallItemsOpt = getWall(target);
      if (wallItemsOpt.isPresent()) {
        WallItems wallItems = wallItemsOpt.get();
        if ("/wall".equals(target.suffix)) {
          response.setContentType("text/html; charset=utf-8");
          response.setStatus(HttpServletResponse.SC_OK);
          // Prevent nonce-leakage via referrer.
          // https://w3c.github.io/webappsec-referrer-policy/#referrer-policy-header
          // http://caniuse.com/#feat=referrer-policy
          response.setHeader("Referrer-Policy", "origin");
          renderWall(target, wallItems, response.getWriter());
          handled = true;
        } else if ("/wall.json".equals(target.suffix)) {
          response.setContentType("application/json; charset=utf-8");
          response.setStatus(HttpServletResponse.SC_OK);
          renderWallJson(wallItems, response.getWriter());
          handled = true;
        } else if ("/wall.pb".equals(target.suffix)) {
          response.setContentType("application/x-protobuffer; charset=utf-8");
          response.setStatus(HttpServletResponse.SC_OK);
          renderWallPb(wallItems, response.getOutputStream());
          handled = true;
        }
      } else {
        Preconditions.checkState(
            target.suffix.startsWith("/") && !target.suffix.contains("/.."));
        String ext = Files.getFileExtension(target.suffix);
        String contentType = EXTENSION_TO_CONTENT_TYPE.get(ext);
        if (contentType != null) {
          URL resourceUrl = getClass().getResource("/closure" + target.suffix);
          if (resourceUrl != null) {
            ByteSource bytes = Resources.asByteSource(resourceUrl);
            response.setContentType("text/html; charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            try (InputStream staticFileIn = bytes.openStream()) {
              ByteStreams.copy(staticFileIn, response.getOutputStream());
            }
            handled = true;
          }
        }
      }
      if (!handled) {
        Nonce newNonce = Nonce.create(nonceGenerator);
        walls.put(newNonce, WallItems.newBuilder().build());
        response.sendRedirect("/" + newNonce.text + "/wall#");
        handled = true;
      }
    } else if ("POST".equals(method)) {
      Optional<WallItems> wallItemsOpt = getWall(target);
      if (wallItemsOpt.isPresent()) {
        if ("/wall.json".equals(target.suffix)) {
          WallItems updatedWallItems = postWallJson(
              target, wallItemsOpt.get(), request.getReader());
          response.setContentType("application/json; charset=utf-8");
          response.setStatus(HttpServletResponse.SC_OK);
          renderWallJson(updatedWallItems, response.getWriter());
          handled = true;
        } else if ("/wall.pb".equals(target.suffix)) {
          WallItems updatedWallItems = postWallPb(
              target, wallItemsOpt.get(), request.getInputStream());
          response.setContentType("application/x-protobuffer; charset=utf-8");
          response.setStatus(HttpServletResponse.SC_OK);
          renderWallPb(updatedWallItems, response.getOutputStream());
          handled = true;
        }
      }
    }
    baseRequest.setHandled(handled);
  }

  private Optional<WallItems> getWall(Target target) {
    if (target.nonce.isPresent()) {
      return Optional.fromNullable(walls.getIfPresent(target.nonce.get()));
    }
    return Optional.absent();
  }


  private WallItems postWallJson(
      Target target, WallItems wallItems, Reader jsonIn)
  throws IOException, InvalidProtocolBufferException {
    WallItems.Builder itemsBuilder = wallItems.toBuilder();

    WallItem.Builder itemBuilder = WallItem.newBuilder();
    JsonFormat.parser().merge(jsonIn, itemBuilder);

    itemsBuilder.addItem(itemBuilder.build());

    return postItems(target, itemsBuilder.build());
  }

  private WallItems postWallPb(
     Target target, WallItems wallItems, InputStream jsonIn)
  throws IOException, InvalidProtocolBufferException {
    WallItems.Builder itemsBuilder = wallItems.toBuilder();

    WallItem.Builder itemBuilder = WallItem.newBuilder();
    itemBuilder.mergeFrom(jsonIn);

    itemsBuilder.addItem(itemBuilder.build());

    return postItems(target, itemsBuilder.build());
  }

  private WallItems postItems(Target target, WallItems newItems) {
    walls.put(target.nonce.get(), newItems);
    return newItems;
  }

  private static void renderWallJson(WallItems wallItems, Writer out)
  throws IOException {
    JsonFormat.printer().appendTo(wallItems, out);
  }

  private static void renderWallPb(WallItems wallItems, OutputStream out)
  throws IOException {
    wallItems.writeTo(out);
  }

  private static void renderWall(
      Target target, WallItems items, Writer out) throws IOException {
    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of(
        "nonce", target.nonce.get().text,
        "wall", items,
        "styles", ImmutableList.<TrustedResourceUrl>of(
            TrustedResourceUrls.fromConstant(WebFiles.CSS_WALL_MAIN_CSS))
        );
  }

  /** Takes optional httpd port number. */
  public static void main(String... args) throws Exception {
    int port = 8080;
    switch (args.length) {
      case 1:
        port = Integer.parseInt(args[0]);
        break;
      case 0:
        break;
      default:
        throw new IllegalArgumentException(
            "Expect at most one argument: httpd port");
    }

    Server server = new Server(port);
    server.setHandler(new Demo());

    server.start();
    System.out.println("(Serving from port " + port + ")");
    server.join();
    System.out.println("(Have a nice day)");
  }
}
