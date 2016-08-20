package com.example.demo;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.html.types.SafeHtmlProto;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.template.soy.data.SoyCustomValueConverter;
import com.google.template.soy.data.SoyValueConverter;
import com.google.template.soy.data.SoyValueHelper;
import com.google.template.soy.jbcsrc.api.PrecompiledSoyModule;
import com.google.template.soy.types.SoyTypeProvider;
import com.google.template.soy.types.proto.SoyProtoTypeProvider;
import com.google.template.soy.types.proto.SoyProtoValueConverter;

@SuppressWarnings({ "static-method", "javadoc" })
public final class DemoModule extends AbstractModule {
  @Override
  protected void configure() {
    // This installs all the core plugins and the apicallscope dependencies.
    install(new PrecompiledSoyModule());

    bind(SoyValueConverter.class).to(SoyValueHelper.class).in(Singleton.class);
    Multibinder.newSetBinder(binder(), SoyTypeProvider.class)
        .addBinding().to(SoyProtoTypeProvider.class);
  }

  @Provides
  @Singleton
  public List<SoyCustomValueConverter> provideSoyValueConverters(
      SoyProtoValueConverter protoConverter) {
    // Note: The order of data converters matters. Converters that only accept
    // specific input types should come before converters that will convert
    // anything.
    return ImmutableList.<SoyCustomValueConverter>of(protoConverter);
  }

  @Provides
  @Singleton
  public SoyProtoTypeProvider provideProtoTypeProvider() {
    // TODO: make these descriptors available in the JAR.
    //File descriptorFile = new File(
    //    Joiner.on(File.separator).join(
    //        "target", "src", "main", "proto", "descriptors.pd"));
    return new SoyProtoTypeProvider.Builder()
        .addDescriptors(ImmutableList.of(
            Wall.getDescriptor(),
            Wall.WallItems.getDescriptor(),
            Wall.WallItem.getDescriptor(),
            SafeHtmlProto.getDescriptor(),
            SafeHtmlProto.getDescriptor().getFile()))
        .buildNoFiles();
  }
}
