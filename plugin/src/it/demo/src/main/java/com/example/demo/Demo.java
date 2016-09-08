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
import com.google.closure.module.ClosureModule;
import com.google.closure.module.ResponseWriter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
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

  final DemoVariant variant;

  /** Compute content types for /closure/... resources. */
  static final ImmutableMap<String, String> EXTENSION_TO_CONTENT_TYPE =
      ImmutableMap.of(
          "js",   "text/javascript; charset=utf-8",
          "html", "text/html; charset=utf-8",
          "css",  "text/css; charset=utf-8",
          "ico",  "image/x-icon");

  /** We load different JS per variant. */
  static final ImmutableMap<DemoVariant, TrustedResourceUrl> WALL_ITEM_JS =
      Maps.immutableEnumMap(ImmutableMap.<DemoVariant, TrustedResourceUrl>of(
          DemoVariant.FIXED,
          TrustedResourceUrls.fromConstant(
              WebFiles.JS_COM_EXAMPLE_WALL_ITEM_FIXED_JS),
          DemoVariant.OVER_ESCAPING,
          TrustedResourceUrls.fromConstant(
              WebFiles.JS_COM_EXAMPLE_WALL_ITEM_OVERESCAPING_JS),
          DemoVariant.INSECURE,
          TrustedResourceUrls.fromConstant(
              WebFiles.JS_COM_EXAMPLE_WALL_ITEM_INSECURE_JS)));

  private final SecureRandom nonceGenerator = makeNonceGenerator();

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

  /** We use this to convert untrusted HTML into trustworthy SafeHtml */
  final SafeHtmlMint safeHtmlMint = SafeHtmlMint.fromPolicyFactory(
      new HtmlPolicyBuilder()
      .allowCommonBlockElements()
      .allowCommonInlineFormattingElements()
      .allowStandardUrlProtocols()
      .allowElements("a")
      .allowAttributes("href").onElements("a")
      .requireRelNofollowOnLinks()
      .allowStyling()
      .toFactory());

  /**
   * @param variant specifies which variant to run.
   */
  public Demo(DemoVariant variant) {
    this.variant = Preconditions.checkNotNull(variant);
  }

  /** Main entry point for Jetty HTTP request dispatch. */
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
        Nonce newNonce = createNewWall();
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

  /**
   * Respond with 304 as appropriate when a webservice client tells us which
   * version they have.
   */
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
    } catch (@SuppressWarnings("unused") NumberFormatException ex) {
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

  @VisibleForTesting
  Nonce createNewWall() {
    Nonce newNonce = Nonce.create(nonceGenerator);
    WallData newWall = new WallData();
    walls.put(newNonce, newWall);
    return newNonce;
  }

  @SuppressWarnings("static-method")
  @VisibleForTesting
  protected SecureRandom makeNonceGenerator() {
    return new SecureRandom();
  }


  /**
   * Mutate the internal state and return the details so we can return the
   * updated state to the client.
   *
   * @param jsonIn contains a wall-item to add in protobuf JSON format.
   */
  private Optional<Update> postWallJson(Target target, Reader jsonIn)
  throws IOException, InvalidProtocolBufferException {
    WallItem.Builder itemBuilder = WallItem.newBuilder();
    JsonFormat.parser().merge(jsonIn, itemBuilder);

    return postItem(target, itemBuilder.build());
  }


  /**
   * @param pbIn contains a wall-item to add in protobuf binary format.
   */
  private Optional<Update> postWallPb(Target target, InputStream pbIn)
  throws IOException, InvalidProtocolBufferException {
    WallItem.Builder itemBuilder = WallItem.newBuilder();
    itemBuilder.mergeFrom(pbIn);

    return postItem(target, itemBuilder.build());
  }

  /**
   * @param item to add to the wall specified by target.
   *     Knowledge of the target nonce implies authority to modify that target.
   */
  @VisibleForTesting
  Optional<Update> postItem(Target target, WallItem item) {
    WallItem.Builder newItemBuilder = item.toBuilder()
        .clearHtml();

    if (variant == DemoVariant.FIXED) {
      String untrustedHtml = item.getHtmlUntrusted();
      SafeHtml safeHtml = safeHtmlMint.sanitize(untrustedHtml);
      newItemBuilder.setHtml(SafeHtmls.toProto(safeHtml));
    }

    final WallItem newItem = newItemBuilder.build();
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

  @VisibleForTesting
  void renderWall(Target target, Update u, final Writer out)
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
            TrustedResourceUrls.fromConstant(WebFiles.JS_COM_EXAMPLE_WALL_JS),
            WALL_ITEM_JS.get(variant))
        );
    ImmutableMap<String, Object> ijData = ImmutableMap.<String, Object>of(
        "csp_nonce", oneTimeCspNonce.text);

    if (variant == DemoVariant.INSECURE) {
      AdHocHtml.write(data, oneTimeCspNonce, this.cssRenamingMap, out);
      return;
    }
    SoySauce.Renderer renderer = soySauce
        .renderTemplate("com.example.demo.Wall")
        .setData(data)
        .setIj(ijData)
//      .setMsgBundle(msgBundle)
//      .setXidRenamingMap(idRenamingMap)
        .setCssRenamingMap(cssRenamingMap)
        ;

    try (ResponseWriter rw = new ResponseWriter(out)) {
      WriteContinuation wc = renderer.render(rw);
      while (!wc.result().isDone()) {
        wc = wc.continueRender();
      }
    }
  }

  /** Takes optional httpd port number. */
  public static void main(String... args) throws Exception {
    int port = 8080;
    DemoVariant variant = DemoVariant.DEFAULT;
    for (int i = 0; i < args.length; ++i) {
      String flag = args[i];
      try {
        switch (flag) {
          case "--port":
            port = Integer.parseInt(args[++i]);
            break;
          case "--variant":
            variant = DemoVariant.valueOf(args[++i]);
            break;
          default:
            throw new IllegalArgumentException(
                "Expect at most one argument: httpd port");
        }
      } catch (RuntimeException ex) {
        System.err.println("Failed to parse value of flag " + flag);
        throw ex;
      }
    }

    Server server = new Server(port);
    server.setHandler(new Demo(variant));

    server.start();
    System.out.println("(Serving from port " + port + ")");
    server.join();
    System.out.println("(Have a nice day)");
  }
}
