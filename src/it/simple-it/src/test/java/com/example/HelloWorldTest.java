package com.google.common.html.plugin;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.File;
import java.util.List;
import java.util.Map;

// Protos
import com.example.Proto1.Name;

// Soy uses Guice to inject stuff.  Sigh.
import com.google.inject.Guice;
import com.google.inject.Inject;

// Template-support
import com.google.common.html.types.SafeHtmls;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.template.soy.coredirectives.EscapeHtmlDirective;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyCustomValueConverter;
import com.google.template.soy.data.SoyEasyDict;
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

  // TODO: similarly injectable
  final SoyValueHelper valueHelper = new SoyValueHelper(customConverters);

  String renderHelloWorld(Map<String, ?> data, Map<String, ?> ijData)
  throws IOException {
    RenderContext rc = new RenderContext.Builder()
        .withCompiledTemplates(compiledTemplates)
        .withSoyPrintDirectives(printDirectiveMap)
        .withConverter(valueHelper)
        .build();

    SoyEasyDict dataDict = valueHelper.newEasyDict();
    dataDict.setFieldsFromJavaStringMap(data);
    dataDict.makeImmutable();

    SoyEasyDict ijDataDict = valueHelper.newEasyDict();
    ijDataDict.setFieldsFromJavaStringMap(ijData);
    ijDataDict.makeImmutable();

    HelloWorld hw = new HelloWorld(dataDict, ijDataDict);
    AdvisingStringBuilder out = new AdvisingStringBuilder();
    RenderResult result = hw.render(out, rc);
    assertTrue(result.isDone());
    return out.toString();
  }

  @Test
  public final void testHelloWorldNoInput() throws Exception {
    ImmutableMap<String, Object> data = ImmutableMap.of();
    ImmutableMap<String, Object> ijData = ImmutableMap.of();

    String output = renderHelloWorld(data, ijData);
    assertEquals("Hello, <b>World</b>!", output);
  }


  @Test
  public final void testHelloWorldUntrustedTextInput() throws Exception {
    Name.Builder nameBuilder = Name.newBuilder();
    nameBuilder.setText("Cincinatti <:)>");

    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of(
        "world", nameBuilder.build());
    ImmutableMap<String, Object> ijData = ImmutableMap.of();

    String output = renderHelloWorld(data, ijData);
    assertEquals("Hello, <b>Cincinatti &lt;:)&gt;</b>!", output);
  }

  @Test
  public final void testHelloWorldSafeHtmlInput() throws Exception {
    Name.Builder nameBuilder = Name.newBuilder();
    nameBuilder.setHtml(SafeHtmls.toProto(SafeHtmls.htmlEscape(
        "Cincinatti <:-}>")));

    ImmutableMap<String, Object> data = ImmutableMap.<String, Object>of(
        "world", nameBuilder.build());
    ImmutableMap<String, Object> ijData = ImmutableMap.of();

    String output = renderHelloWorld(data, ijData);
    assertEquals("Hello, <b>Cincinatti &lt;:-}&gt;</b>!", output);
  }
}
