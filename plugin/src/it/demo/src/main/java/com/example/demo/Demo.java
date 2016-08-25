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

import com.example.demo.Wall.Update;
import com.example.demo.Wall.WallItem;
import com.example.demo.Wall.WallItems;

import com.google.closure.module.ClosureModule;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.types.SafeHtml;
import com.google.common.html.types.SafeHtmls;
import com.google.common.html.types.TrustedResourceUrl;
import com.google.common.html.types.TrustedResourceUrls;
import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.jbcsrc.api.AdvisingAppendable;
import com.google.template.soy.jbcsrc.api.Precompiled;
import com.google.template.soy.jbcsrc.api.SoySauce;
import com.google.template.soy.jbcsrc.api.SoySauce.WriteContinuation;
import com.google.template.soy.shared.SoyCssRenamingMap;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.htmltypes.SafeHtmlMint;

/**
 * A Jetty server handler that serves /closure/... resources as static files
 * and handles the legal wall demo.
 * <p>
 * {@code /} redirects to {@code /nonce/wall} where {Wcode nonce} is a randomly
 * generated web-safe string.
 * <p>
 * {@code /nonce/wall} is the main HTML page.
 * <p>
 * {@code /nonce/wall.json} is the data backing the wall in JSON format, or when
 * posted to, adds an item to the wall and echoes the data backing the wall.
 * <p>
 * {@code /nonce/wall.pb} is like wall.json but in binary protobuf format.
 * <p>
 * When the optional {@code ?have=} parameter is specified, then GET queries for
 * program state return a 304 (Not modified) response if the parameter value is
 * the same as the current wall value.
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

  private final Cache<Nonce, WallData> walls =
      CacheBuilder.newBuilder()
      .maximumSize(1000)
      .expireAfterAccess(10, TimeUnit.MINUTES)
      .<Nonce, WallData>build();

  private final Injector injector;

  @Inject
  SoyValueHelper valueHelper;

  @Inject
  SoyCssRenamingMap cssRenamingMap;

  @Inject
  @Precompiled
  SoySauce soySauce;

  {
    injector = Guice.createInjector(new ClosureModule());
    injector.injectMembers(this);
  }

  SafeHtmlMint safeHtmlMint = SafeHtmlMint.fromPolicyFactory(
      new HtmlPolicyBuilder()
      .allowCommonBlockElements()
      .allowCommonInlineFormattingElements()
      .allowStandardUrlProtocols()
      .allowElements("a")
      .allowAttributes("href").onElements("a")
      .requireRelNofollowOnLinks()
      .allowStyling()
      .toFactory());

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
      Optional<WallData> wallDataOpt = getWall(target);
      if (wallDataOpt.isPresent()) {
        Update update = wallDataOpt.get().getWall();
        if ("/wall".equals(target.suffix)) {
          response.setContentType("text/html; charset=utf-8");
          response.setStatus(HttpServletResponse.SC_OK);
          // Prevent nonce-leakage via referrer.

          // https://w3c.github.io/webappsec-referrer-policy/
          // #referrer-policy-header

          // http://caniuse.com/#feat=referrer-policy
          response.setHeader("Referrer-Policy", "origin");
          renderWall(target, update, response.getWriter());
          handled = true;
        } else if ("/wall.json".equals(target.suffix)) {
          handled = maybeNotModified(target, request, response);
          if (!handled) {
            response.setContentType("application/json; charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            renderWallJson(update, response.getWriter());
            handled = true;
          }
        } else if ("/wall.pb".equals(target.suffix)) {
          handled = maybeNotModified(target, request, response);
          if (!handled) {
            response.setContentType("application/x-protobuffer; charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            renderWallPb(update, response.getOutputStream());
            handled = true;
          }
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
            response.setContentType(contentType);
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
        WallData newWall = new WallData();
        walls.put(newNonce, newWall);
        response.sendRedirect("/" + newNonce.text + "/wall#");
        handled = true;
      }
    } else if ("POST".equals(method)) {
      if ("/wall.json".equals(target.suffix)) {
        Optional<Update> update = postWallJson(target, request.getReader());
        if (update.isPresent()) {
          response.setContentType("application/json; charset=utf-8");
          response.setStatus(HttpServletResponse.SC_OK);
          renderWallJson(update.get(), response.getWriter());
          handled = true;
        }
      } else if ("/wall.pb".equals(target.suffix)) {
        Optional<Update> update = postWallPb(target, request.getInputStream());
        if (update.isPresent()) {
          response.setContentType("application/x-protobuffer; charset=utf-8");
          response.setStatus(HttpServletResponse.SC_OK);
          renderWallPb(update.get(), response.getOutputStream());
          handled = true;
        }
      }
      if (!handled) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      }
    }
    baseRequest.setHandled(handled);
  }

  private boolean maybeNotModified(
      Target target, HttpServletRequest request, HttpServletResponse response)
  throws IOException {
    String versionString = request.getParameter("have");
    if (versionString == null) {
      return false;
    }
    int version;
    try {
      version = Integer.parseInt(versionString);
    } catch (@SuppressWarnings("unused") NumberFormatException _) {
      System.err.println("Malformed have parameter " + request.getRequestURL());
      return false;
    }
    Optional<WallData> wallData = getWall(target);
    if (!wallData.isPresent()) {
      return false;
    }
    if (wallData.get().getVersion() != version) {
      return false;
    }
    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
    response.getOutputStream().close();
    return true;
  }

  private Optional<WallData> getWall(Target target) {
    if (target.nonce.isPresent()) {
      return Optional.fromNullable(walls.getIfPresent(target.nonce.get()));
    }
    return Optional.absent();
  }


  private Optional<Update> postWallJson(
      Target target, Reader jsonIn)
  throws IOException, InvalidProtocolBufferException {
    WallItem.Builder itemBuilder = WallItem.newBuilder();
    JsonFormat.parser().merge(jsonIn, itemBuilder);

    return postItems(target, itemBuilder.build());
  }

  private Optional<Update> postWallPb(
     Target target, InputStream jsonIn)
  throws IOException, InvalidProtocolBufferException {
    WallItem.Builder itemBuilder = WallItem.newBuilder();
    itemBuilder.mergeFrom(jsonIn);

    return postItems(target, itemBuilder.build());
  }

  private Optional<Update> postItems(Target target, WallItem item) {
    String untrustedHtml = item.getHtmlUntrusted();
    SafeHtml safeHtml = safeHtmlMint.sanitize(untrustedHtml);

    final WallItem newItem = item.toBuilder()
        .setHtml(SafeHtmls.toProto(safeHtml))
        .build();
    return getWall(target).transform(new Function<WallData, Update>() {
      @Override
      public Update apply(WallData wd) {
        return wd.addItem(newItem);
      }
    });
  }

  private static void renderWallJson(Update u, Writer out)
  throws IOException {
    JsonFormat.printer().appendTo(u, out);
  }

  private static void renderWallPb(Update u, OutputStream out)
  throws IOException {
    u.writeTo(out);
  }

  private void renderWall(Target target, Update u, final Writer out)
  throws IOException {
    Nonce oneTimeCspNonce = Nonce.create(nonceGenerator);

    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of(
        "nonce", target.nonce.get().text,
        "wall", u.getItems(),
        "version", u.getVersion(),
        "styles", ImmutableList.<TrustedResourceUrl>of(
            TrustedResourceUrls.fromConstant(WebFiles.CSS_WALL_MAIN_CSS)),
        "scripts", ImmutableList.<TrustedResourceUrl>of(
            TrustedResourceUrls.fromConstant(WebFiles.JS_MAIN_JS),
            TrustedResourceUrls.fromConstant(WebFiles.JS_COM_EXAMPLE_WALL_JS))
        );
    ImmutableMap<String, Object> ijData = ImmutableMap.<String, Object>of(
        "csp_nonce", oneTimeCspNonce.text);

    SoySauce.Renderer renderer = soySauce
        .renderTemplate("com.example.demo.Wall")
        .setData(data)
        .setIj(ijData)
//      .setMsgBundle(msgBundle)
//      .setXidRenamingMap(idRenamingMap)
        .setCssRenamingMap(cssRenamingMap)
        ;

    WriteContinuation wc = renderer.render(new AdvisingAppendable() {

      @Override
      public AdvisingAppendable append(CharSequence s) throws IOException {
        out.write(s.toString());
        return this;
      }

      @Override
      public AdvisingAppendable append(char ch) throws IOException {
        out.write(ch);
        return this;
      }

      @Override
      public AdvisingAppendable append(CharSequence s, int lt, int rt)
      throws IOException {
        out.write(s.subSequence(lt, rt).toString());
        return this;
      }

      @Override
      public boolean softLimitReached() {
        return false;
      }
    });
    while (!wc.result().isDone()) {
      wc = wc.continueRender();
    }
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

  static final class WallData {
    private int version = 0;
    private WallItems items = WallItems.newBuilder().build();

    public Update getWall() {
      WallItems currentItems;
      int currentVersion;
      synchronized (this) {
        currentItems = this.items;
        currentVersion = this.version;
      }
      return Update.newBuilder()
          .setItems(currentItems)
          .setVersion(currentVersion)
          .build();
    }

    public int getVersion() {
      return version;
    }

    public Update addItem(WallItem newItem) {
      Preconditions.checkNotNull(newItem);

      WallItems currentItems;
      int currentVersion;
      synchronized (this) {
        Preconditions.checkState(this.version < Integer.MAX_VALUE);
        WallItems newItems = this.items.toBuilder().addItem(newItem).build();

        currentItems = this.items = newItems;
        currentVersion = ++this.version;
      }
      return Update.newBuilder()
          .setItems(currentItems)
          .setVersion(currentVersion)
          .build();
    }
  }
}
