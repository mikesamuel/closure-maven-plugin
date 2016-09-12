package com.google.closure.module;

import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.List;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.protobuf.Descriptors;
import com.google.template.soy.data.SoyCustomValueConverter;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.jbcsrc.api.PrecompiledSoyModule;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.proto.SoyProtoTypeProvider;
import com.google.template.soy.types.proto.SoyProtoValueConverter;

/**
 * A guice module that allows using the Closure Templates JBC-src backend.
 */
@SuppressWarnings("static-method")
public final class ClosureModule extends AbstractModule {

  /**
   * The resource path to the bundled proto-descriptors.
   */
  public static final String PROTO_DESCRIPTORS_RESOURCE_PATH =
      "/closure/descriptors.pd";

  /**
   * The resource path to the bundled CSS rename map in JSON format.
   */
  public static final String CSS_RENAMING_MAP_RESOURCE_PATH =
      "/closure/css/css-rename-map.json";

  @Override
  protected void configure() {
    // This installs all the core plugins and the apicallscope dependencies.
    install(new PrecompiledSoyModule());

    bind(SoyValueConverter.class).to(SoyValueHelper.class).in(Singleton.class);
    Multibinder.newSetBinder(binder(), SoyTypeProvider.class)
        .addBinding().to(SoyProtoTypeProvider.class);
  }

  /**
   * Necessary to thread the proto value converters through to SoyTypeRegistry.
   * -- because writing a method is totally easier than passing a parameter to
   * a constructor.  Thanks guice.
   */
  @Provides
  @Singleton
  public List<SoyCustomValueConverter> provideSoyValueConverters(
      SoyProtoValueConverter protoConverter) {
    // Note: The order of data converters matters. Converters that only accept
    // specific input types should come before converters that will convert
    // anything.
    return ImmutableList.<SoyCustomValueConverter>of(protoConverter);
  }

  /**
   * Register the proto descriptors bundled with the application JAR so that
   * the SoyTypeRegistry can map proto names in Soy code to protobuf
   * definitions.
   */
  @Provides
  @Singleton
  public SoyProtoTypeProvider provideProtoTypeProvider()
  throws IOException, Descriptors.DescriptorValidationException {
    SoyProtoTypeProvider.Builder b = new SoyProtoTypeProvider.Builder();
    URL pdUrl = getClass().getResource(PROTO_DESCRIPTORS_RESOURCE_PATH);
    if (pdUrl != null) {
      ByteSource descriptorBytes = Resources.asByteSource(pdUrl);
      b.addFileDescriptorSetFromByteSource(descriptorBytes);
    }
    return b.build();
  }

  /** Reads the CSS rename map  */
  @Provides
  @Singleton
  public SoyCssRenamingMap provideCssRenamingMap()
  throws IOException {
    ImmutableMap.Builder<String, String> cssMapBuilder = ImmutableMap.builder();

    URL crUrl = getClass().getResource(CSS_RENAMING_MAP_RESOURCE_PATH);
    if (crUrl != null) {
      CharSource cssRenamingMapJson = Resources.asCharSource(
          crUrl, Charsets.UTF_8);
      JsonElement json;
      try (Reader jsonIn = cssRenamingMapJson.openStream()) {
        json = new JsonParser().parse(jsonIn);
      }
      for (Map.Entry<String, JsonElement> e
           : json.getAsJsonObject().entrySet()) {
        cssMapBuilder.put(e.getKey(), e.getValue().getAsString());
      }
    }
    return new SoyCssRenamingMapImpl(cssMapBuilder.build());
  }

  static final class SoyCssRenamingMapImpl implements SoyCssRenamingMap {
    final ImmutableMap<String, String> originalNameToRewrittenName;

    SoyCssRenamingMapImpl(
        Map<? extends String, ? extends String> originalNameToRewrittenName) {
      this.originalNameToRewrittenName = ImmutableMap.copyOf(
          originalNameToRewrittenName);
    }

    @Override
    public String get(String originalName) {
      return this.originalNameToRewrittenName.get(originalName);
    }
  }
}
