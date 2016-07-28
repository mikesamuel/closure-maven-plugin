package com.google.common.html.plugin;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.component.repository.ComponentDependency;

import com.comoyo.maven.plugins.protoc.ProtocBundledMojo;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.css.OutputRenamingMapFormat;
import com.google.common.html.plugin.common.CommonPlanner;
import com.google.common.html.plugin.common.GenfilesDirs;
import com.google.common.html.plugin.common.Ingredients;
import com.google.common.html.plugin.common.Ingredients.FileIngredient;
import com.google.common.html.plugin.common.Ingredients
    .SerializedObjectIngredient;
import com.google.common.html.plugin.common.Ingredients
    .SettableFileSetIngredient;
import com.google.common.html.plugin.common.ToolFinder;
import com.google.common.html.plugin.css.CssPlanner;
import com.google.common.html.plugin.extract.Extract;
import com.google.common.html.plugin.extract.ExtractPlanner;
import com.google.common.html.plugin.js.JsOptions;
import com.google.common.html.plugin.plan.HashStore;
import com.google.common.html.plugin.plan.Plan;
import com.google.common.html.plugin.proto.ProtoIO;
import com.google.common.html.plugin.proto.ProtoOptions;
import com.google.common.html.plugin.proto.ProtoPlanner;
import com.google.common.html.plugin.soy.SoyOptions;
import com.google.common.html.plugin.soy.SoyPlanner;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Generates .js & .java sources from .proto and .soy and compiles .js and .css
 * to optimized bundles.
 */
@Mojo(
    name="generate-sources", defaultPhase=LifecyclePhase.PROCESS_SOURCES,
    // Required because ProtocBundledMojo requires dependency resolution
    // so it can figure out which protobufVersion to use.
    requiresDependencyResolution=ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class ClosureGenerateSourcesMojo extends AbstractClosureMojo {

  @Override
  public void execute() throws MojoExecutionException {
    if (!outputDir.exists()) {
      outputDir.mkdirs();
    }

    Log log = this.getLog();

    ClosureMavenPluginSubstitutionMapProvider substitutionMapProvider;
    try {
      substitutionMapProvider = new ClosureMavenPluginSubstitutionMapProvider(
          Files.asCharSource(cssRenameMap, Charsets.UTF_8));
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to read CSS rename map " + cssRenameMap, ex);
    }

    File jsGenfiles = defaultJsGenfiles;
    File jsTestGenfiles = defaultJsTestGenfiles;
    if (js != null && js.length != 0) {
      JsOptions js0 = js[0];
      if (js0.jsGenfiles != null) {
        jsGenfiles = js0.jsGenfiles;
      }
      if (js0.jsTestGenfiles != null) {
        jsTestGenfiles = js0.jsTestGenfiles;
      }
    }

    HashStore hashStore = null;
    if (hashStoreFile.exists()) {
      try {
        try (InputStream hashStoreIn = new FileInputStream(hashStoreFile)) {
          try (Reader hashStoreReader = new InputStreamReader(
                  hashStoreIn, Charsets.UTF_8)) {
            hashStore = HashStore.read(hashStoreReader, log);
          }
        }
      } catch (IOException ex) {
        log.warn("Failed to read hash store", ex);
      }
    }
    if (hashStore == null) {
      log.debug("Creating empty hash store");
      hashStore = new HashStore();
    }

    CommonPlanner planner;
    try {
      planner = new CommonPlanner(
          log, outputDir, substitutionMapProvider, hashStore);
    } catch (IOException ex) {
      throw new MojoExecutionException("failed to initialize planner", ex);
    }

    planner.genfiles.setStoredObject(new GenfilesDirs(
        outputDir,
        javaGenfiles, javaTestGenfiles,
        jsGenfiles, jsTestGenfiles));

    try {
      new ExtractPlanner(planner, project)
          .plan(extracts != null
                ? ImmutableList.copyOf(extracts)
                : ImmutableList.<Extract>of());
    } catch (IOException ex) {
      throw new MojoExecutionException(
          "Failed to plan source file extraction", ex);
    }

    try {
      new CssPlanner(planner)
          .cssRenameMap(cssRenameMap)
          .defaultCssSource(defaultCssSource)
          .defaultCssOutputPathTemplate(defaultCssOutputPathTemplate)
          .defaultCssSourceMapPathTemplate(defaultCssSourceMapPathTemplate)
          .plan(css);
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to plan CSS compile", ex);
    }

    SerializedObjectIngredient<ProtoIO> protoIO;
    try {
      ProtoPlanner pp = new ProtoPlanner(planner, protocExecutable())
          .defaultProtoSource(defaultProtoSource)
          .defaultProtoTestSource(defaultProtoTestSource)
          .defaultMainDescriptorFile(defaultMainDescriptorFile)
          .defaultTestDescriptorFile(defaultTestDescriptorFile);
      pp.plan(proto != null ? proto : new ProtoOptions());

      protoIO = pp.getProtoIO();
    } catch (IOException ex) {
      throw new MojoExecutionException("Failed to plan proto compile", ex);
    }

    SoyOptions soyOptions = soy != null ? soy : new SoyOptions();
    new SoyPlanner(planner, protoIO)
        .defaultSoySource(defaultSoySource)
        .plan(soyOptions);


    Plan plan = planner.toPlan();
    log.debug("Finished plan.  Executing plan");
    while (!plan.isComplete()) {
      plan.executeOneStep();
    }

    log.debug("Writing hash store to " + hashStoreFile);
    try {
      hashStoreFile.getParentFile().mkdirs();
      try (OutputStream hashStoreOut = new FileOutputStream(hashStoreFile)) {
        try (Writer hashStoreWriter = new OutputStreamWriter(
                hashStoreOut, Charsets.UTF_8)){
          hashStore.write(hashStoreWriter);
        }
      }
    } catch (IOException ex) {
      log.warn("Problem writing hash store", ex);
    }

    log.debug("Writing rename map to " + cssRenameMap);
    try {
      cssRenameMap.getParentFile().mkdirs();
      try (Writer cssRenameOut = Files.asCharSink(cssRenameMap, Charsets.UTF_8)
              .openBufferedStream()) {
        substitutionMapProvider.get()
             .write(OutputRenamingMapFormat.JSON, cssRenameOut);
      }
    } catch (IOException ex) {
      log.warn("Problem writing CSS rename map", ex);
    }
  }


  // For protoc support.

  @Parameter(defaultValue="${plugin}", required=true, readonly=true)
  private PluginDescriptor pluginDescriptor;

  @Component
  private RepositorySystem repositorySystem;

//@Parameter(defaultValue="${project.remoteProjectRepositories}", readonly=true,
//           required=true)
  @Parameter(defaultValue="${project.remoteArtifactRepositories}",
             required=true, readonly=true )
  protected List<ArtifactRepository> remoteRepositories;

  ToolFinder<ProtoOptions> protocExecutable() {
    return new ToolFinder<ProtoOptions>() {

      static final String PROTOC_PLUGIN_GROUP_ID = "com.comoyo.maven.plugins";
      static final String PROTOC_PLUGIN_ARTIFACT_ID = "protoc-bundled-plugin";

      @SuppressWarnings("synthetic-access")
      @Override
      public void find(
          ProtoOptions options, Ingredients ingredients,
          SettableFileSetIngredient protocPathOut) {
        ProtocBundledMojo protocBundledMojo = new ProtocBundledMojo();

        String protocPluginVersion = null;
        for (ComponentDependency d : pluginDescriptor.getDependencies()) {
          if (PROTOC_PLUGIN_GROUP_ID.equals(d.getGroupId())
              && PROTOC_PLUGIN_ARTIFACT_ID.equals(d.getArtifactId())) {
            protocPluginVersion = d.getVersion();
          }
        }
        Preconditions.checkNotNull(
            protocPluginVersion, "protoc-plugin version");

        PluginDescriptor protocPluginDescriptor = new PluginDescriptor();
        protocPluginDescriptor.setGroupId(PROTOC_PLUGIN_GROUP_ID);
        protocPluginDescriptor.setArtifactId(PROTOC_PLUGIN_ARTIFACT_ID);
        protocPluginDescriptor.setVersion(protocPluginVersion);

        Cheats.cheatSet(
            ProtocBundledMojo.class, protocBundledMojo,
            "pluginDescriptor", protocPluginDescriptor);
        Cheats.cheatSet(
            ProtocBundledMojo.class, protocBundledMojo,
            "project", project);
        Cheats.cheatSet(
            ProtocBundledMojo.class, protocBundledMojo,
            "repositorySystem", repositorySystem);
        Cheats.cheatSet(
            ProtocBundledMojo.class, protocBundledMojo,
            "remoteRepositories", remoteRepositories);
        if (options.protobufVersion != null) {
          Cheats.cheatSet(
              ProtocBundledMojo.class, protocBundledMojo,
              "protobufVersion", options.protobufVersion);
        }
        if (options.protocExec != null) {
          Cheats.cheatSet(
              ProtocBundledMojo.class, protocBundledMojo,
              "protocExec", options.protocExec);
        }

        try {
          Cheats.cheatCall(
              Void.class, ProtocBundledMojo.class,
              protocBundledMojo, "ensureProtocBinaryPresent"
              );
          File protocFile = Cheats.cheatGet(
              ProtocBundledMojo.class, protocBundledMojo,
              File.class, "protocExec");
          protocPathOut.setFiles(
              ImmutableList.of(ingredients.file(protocFile)),
              ImmutableList.<FileIngredient>of());
        } catch (InvocationTargetException ex) {
          protocPathOut.setProblem(ex.getTargetException());
        } catch (IOException ex) {
          protocPathOut.setProblem(ex);
        }
      }
    };
  }
}
