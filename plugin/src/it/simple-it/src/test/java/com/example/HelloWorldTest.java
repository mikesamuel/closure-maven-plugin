package com.example;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

import com.google.closure.module.ClosureModule;

// Protos
import com.example.Proto1.Name;

// Soy uses Guice to inject stuff.  Sigh.
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

// Template-support
import com.google.common.html.types.SafeHtmls;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SanitizedContent.ContentKind;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.api.Precompiled;
import com.google.template.soy.jbcsrc.api.SoySauce;

// Testbed imports
import org.junit.Test;
import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class HelloWorldTest extends TestCase {

  static final String TEMPLATE_NAME = "greetings.world.HelloWorld";
  private final Injector injector;

  @Inject
  SoyValueHelper valueHelper;

  @Inject
  @Precompiled
  SoySauce soySauce;

  {
    injector = Guice.createInjector(new ClosureModule());
    injector.injectMembers(this);
  }


  SanitizedContent renderHelloWorld(
      Map<String, ?> data, Map<String, ?> ijData) {
    SoySauce.Renderer renderer = soySauce
            .renderTemplate(TEMPLATE_NAME)
            .setData(data)
            .setIj(ijData)
//          .setMsgBundle(msgBundle)
//          .setXidRenamingMap(idRenamingMap)
//          .setCssRenamingMap(cssRenamingMap)
            ;

    SoySauce.Continuation<SanitizedContent> c = renderer.renderStrict();
    for (int tries = 100; --tries >= 0;) {
      RenderResult result = c.result();
      if (result.isDone()) {
        return c.get();
      }
      c = c.continueRender();
    }
    throw new IllegalStateException(
        "Rendering stuck on " + c.result().future());
  }

  @Test
  public final void testHelloWorldNoInput() {
    ImmutableMap<String, Object> data = ImmutableMap.of();
    ImmutableMap<String, Object> ijData = ImmutableMap.of();

    SanitizedContent output = renderHelloWorld(data, ijData);
    assertEquals(ContentKind.HTML, output.getContentKind());
    assertEquals("Hello, <b>World</b>!", output.getContent());
  }


  @Test
  public final void testHelloWorldUntrustedTextInput() {
    Name.Builder nameBuilder = Name.newBuilder();
    nameBuilder.setText("Cincinatti <:)>");

    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of(
        "world", nameBuilder.build());
    ImmutableMap<String, Object> ijData = ImmutableMap.of();

    SanitizedContent output = renderHelloWorld(data, ijData);
    assertEquals(ContentKind.HTML, output.getContentKind());
    assertEquals("Hello, <b>Cincinatti &lt;:)&gt;</b>!", output.getContent());
  }

  @Test
  public final void testHelloWorldSafeHtmlInput() {
    Name.Builder nameBuilder = Name.newBuilder();
    nameBuilder.setHtml(SafeHtmls.toProto(SafeHtmls.htmlEscape(
        "Cincinatti <:-}>")));

    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of(
        "world", nameBuilder.build());
    ImmutableMap<String, Object> ijData = ImmutableMap.of();

    SanitizedContent output = renderHelloWorld(data, ijData);
    assertEquals(ContentKind.HTML, output.getContentKind());
    assertEquals("Hello, <b>Cincinatti &lt;:-}&gt;</b>!", output.getContent());
  }
}
