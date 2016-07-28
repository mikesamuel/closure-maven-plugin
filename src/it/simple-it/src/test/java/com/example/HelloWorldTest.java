package com.google.common.html.plugin;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.File;
import java.util.List;

// Soy uses Guice to inject stuff.  Sigh.
import com.google.inject.Guice;
import com.google.inject.Inject;

// Template-support
import com.google.common.html.types.SafeHtmls;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.template.soy.coredirectives.EscapeHtmlDirective;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyCustomValueConverter;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.jbcsrc.api.AdvisingStringBuilder;
import com.google.template.soy.jbcsrc.api.RenderResult;
import com.google.template.soy.jbcsrc.shared.CompiledTemplates;
import com.google.template.soy.jbcsrc.shared.RenderContext;
import com.google.template.soy.shared.restricted.SoyJavaPrintDirective;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.SoyTypeRegistry;
import com.google.template.soy.types.proto.SoyProtoTypeProvider;
import com.google.template.soy.types.proto.SoyProtoValueConverter;

// Compiled templates
import com.google.template.soy.jbcsrc.gen.greetings.world.HelloWorld;



// Testbed imports
import org.junit.Test;
import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class HelloWorldTest extends TestCase {

  final CompiledTemplates compiledTemplates = new CompiledTemplates(
      ImmutableSet.<String>of());
  {
    compiledTemplates.loadAll(ImmutableList.of("greetings.world.HelloWorld"));
  }

  // TODO: These should be populable by CoreDirectivesModule or
  // PrecompiledSoyModule.
  final SoyProtoTypeProvider protoTypeProvider;

  {
    File descriptorFile = new File(
        Joiner.on(File.separator).join(
            "target", "src", "main", "proto", "descriptors.pd"));
    try {
      protoTypeProvider = new SoyProtoTypeProvider.Builder()
          .addFileDescriptorSetFromFile(descriptorFile)
          .build();
    } catch (IOException ex) {
      throw (AssertionError) new AssertionError().initCause(ex);
    } catch (DescriptorValidationException ex) {
      throw (AssertionError) new AssertionError().initCause(ex);
    }
  }

  final SoyTypeRegistry typeRegistry = new SoyTypeRegistry(
      ImmutableSet.<SoyTypeProvider>of(protoTypeProvider));
  
  final ImmutableMap<String, SoyJavaPrintDirective> printDirectiveMap =
      ImmutableMap.<String, SoyJavaPrintDirective>of(
          EscapeHtmlDirective.NAME, new EscapeHtmlDirective());

  final ImmutableList<SoyCustomValueConverter> customConverters =
      ImmutableList.<SoyCustomValueConverter>of(
          new SoyProtoValueConverter(typeRegistry, protoTypeProvider));
  

  String renderHelloWorld(SoyMapData data, SoyMapData ijData)
  throws IOException {
    RenderContext rc = new RenderContext.Builder()
        .withCompiledTemplates(compiledTemplates)
        .withSoyPrintDirectives(printDirectiveMap)
        .withConverter(new SoyValueHelper(customConverters))  // TODO: similarly injectable
        .build();
    HelloWorld hw = new HelloWorld(data, ijData);
    AdvisingStringBuilder out = new AdvisingStringBuilder();
    RenderResult result = hw.render(out, rc);
    assertTrue(result.isDone());
    return out.toString();
  }

  @Test
  public final void testHelloWorldNoInput() throws Exception {
    SoyMapData data = new SoyMapData();
    SoyMapData ijData = new SoyMapData();

    String output = renderHelloWorld(data, ijData);
    assertEquals("Hello, <b>World</b>!", output);
  }


  @Test
  public final void testHelloWorldStringInput() throws Exception {
    SoyMapData data = new SoyMapData(ImmutableMap.of("world", "Cincinatti <:)>"));
    SoyMapData ijData = new SoyMapData();

    String output = renderHelloWorld(data, ijData);
    assertEquals("Hello, <b>Cincinatti &lt;:)&gt;</b>!", output);
  }

  @Test
  public final void testHelloWorldSafeHtmlInput() throws Exception {
    // Pretend we got a protobuf from somewhere.
    SoyMapData data = new SoyMapData(ImmutableMap.of(
        "world", SafeHtmls.toProto(SafeHtmls.htmlEscape("Cincinattu <:)>"))));
    SoyMapData ijData = new SoyMapData();

    String output = renderHelloWorld(data, ijData);
    assertEquals("Hello, <b>Cincinatti &lt;:&gt;</b>!", output);
  }
}
