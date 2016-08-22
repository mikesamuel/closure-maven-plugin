package com.google.closure.module;

import java.io.IOException;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.protobuf.Descriptors;
import com.google.template.soy.data.SoyCustomValueConverter;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.jbcsrc.api.PrecompiledSoyModule;
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
    // TODO: make these descriptors available in the JAR.
    ByteSource descriptorBytes = Resources.asByteSource(
        getClass().getResource(PROTO_DESCRIPTORS_RESOURCE_PATH));
    return new SoyProtoTypeProvider.Builder()
        .addFileDescriptorSetFromByteSource(descriptorBytes)
        .build();
  }
}
